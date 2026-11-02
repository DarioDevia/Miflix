package ar.com.miflix.client

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Only bounded public Home artwork lives here. Never catalog, preferences or Telegram data. */
internal class TvHomeArtwork(private val context:Context) {
    private val folder=File(context.filesDir,"tv_home_probe_art").apply { check(isDirectory || mkdirs()) }
    private val client=OkHttpClient.Builder().callTimeout(3,TimeUnit.SECONDS).followSslRedirects(false).build()
    private fun uri(name:String):Uri=Uri.Builder().scheme("content").authority(context.packageName+".homeprobe.images").appendPath(name).build()
    fun clean(retained:Set<String>) {
        val keep=retained.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .filter { it.authority==context.packageName+".homeprobe.images" }
            .mapNotNull { homeImageLeaf(it.pathSegments,it.query,it.fragment) }.toSet()
        folder.listFiles().orEmpty().filter {
            (Regex("[a-f0-9]{64}\\.png").matches(it.name) && it.name !in keep) || Regex("[a-f0-9]{64}\\.png\\.tmp").matches(it.name)
        }.forEach { it.delete() }
    }
    fun image(card:HomeProbeCard):Uri {
        val remote=tvArtworkUrl(card.image,TvArtworkKind.BACKDROP)
        val name=homeProbeHash("home-art-v2:"+card.key+":"+remote+":"+card.title)+".png"
        val ready=File(folder,name)
        if(ready.isFile) return uri(name)
        val decoded=remote?.let { url -> runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful)
                val body=checkNotNull(response.body)
                check(body.contentLength()<=2*1024*1024)
                val bytes=body.byteStream().use { input ->
                    val out=java.io.ByteArrayOutputStream()
                    val buffer=ByteArray(8192)
                    while(true) {
                        val n=input.read(buffer)
                        if(n<0) break
                        check(out.size()+n<=2*1024*1024)
                        out.write(buffer,0,n)
                    }
                    out.toByteArray()
                }
                check(bytes.size<=2*1024*1024)
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                check(bounds.outWidth in 1..16384 && bounds.outHeight in 1..16384)
                var sample=1
                while(bounds.outWidth.toLong()*bounds.outHeight/(sample.toLong()*sample)>2_000_000L) sample*=2
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample })
            }
        }.getOrNull() }
        val target=if(decoded!=null) name else homeProbeHash("home-fallback-v2:"+card.key+":"+card.title)+".png"
        if(File(folder,target).isFile) { decoded?.recycle();return uri(target) }
        // Existing referenced images are never evicted to meet this limit: abort the sync safely instead.
        val files=folder.listFiles().orEmpty()
        check(files.size<32 && files.sumOf { it.length() }<62L*1024*1024)
        val bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(bitmap).apply { drawColor(Color.rgb(17,23,34)) }
            if(decoded!=null) {
                val scale=minOf(960f/decoded.width,540f/decoded.height)
                val w=decoded.width*scale;val h=decoded.height*scale
                canvas.drawBitmap(decoded,null,RectF((960-w)/2,(540-h)/2,(960+w)/2,(540+h)/2),Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=42f }
                canvas.drawText("MiFlix",40f,150f,paint)
                canvas.drawText(card.title.take(32),40f,300f,paint)
            }
            val pending=File(folder,target+".tmp")
            try {
                pending.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
                check(pending.length()<=2L*1024*1024)
                check(pending.renameTo(File(folder,target)))
            } finally { pending.delete() }
        } finally { bitmap.recycle();decoded?.recycle() }
        return uri(target)
    }
}
