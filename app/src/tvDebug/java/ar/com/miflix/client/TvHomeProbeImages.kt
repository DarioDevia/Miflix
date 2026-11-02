package ar.com.miflix.client

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Public generated PNGs only. No arbitrary paths, writes or access to other MiFlix files. */
class TvHomeProbeImages : ContentProvider() {
    override fun onCreate() = true
    private fun image(uri: Uri): File {
        val name = uri.pathSegments.singleOrNull() ?: throw FileNotFoundException()
        if (uri.authority != context!!.packageName + ".homeprobe.images" ||
            !Regex("[a-f0-9]{64}\\.png").matches(name)) throw FileNotFoundException()
        val file = File(File(context!!.filesDir, "tv_home_probe_art"), name)
        if (!file.isFile) throw FileNotFoundException()
        return file
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only test artwork")
        return ParcelFileDescriptor.open(image(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun getType(uri: Uri): String { image(uri); return "image/png" }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = image(uri)
        val columns = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }.toTypedArray()
        return MatrixCursor(columns).apply { addRow(columns.map {
            if (it == OpenableColumns.SIZE) file.length() else file.name
        }) }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
