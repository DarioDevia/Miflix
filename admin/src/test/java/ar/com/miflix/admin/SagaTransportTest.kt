package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class SagaTransportTest {
    private fun provider(s:MockWebServer,direct:Boolean=true,timeout:Long=1000)=TmdbSagaProvider(HttpMetadata(AdminConfig(s.url("/").toString().trimEnd('/'),"synthetic-admin",if(direct) "synthetic-tmdb" else ""),OkHttpClient.Builder().callTimeout(timeout,TimeUnit.MILLISECONDS).build(),s.url("/3").toString()))
    private fun server(test:(MockWebServer)->Unit) {val s=MockWebServer();s.start();try{test(s)}finally{s.shutdown()}}
    @Test fun membershipUsesOfficialMovieEndpointAndSpanishExistingToken()=server {s ->s.enqueue(MockResponse().setBody("""{"id":1,"belongs_to_collection":{"id":10,"name":"Saga"}}"""));assertEquals(10L,provider(s).membership(1));val req=s.takeRequest();assertEquals("/3/movie/1?language=es-AR",req.path);assertEquals("Bearer synthetic-tmdb",req.getHeader("Authorization"))}
    @Test fun explicitNullIsValidNoCollection()=server {s ->s.enqueue(MockResponse().setBody("""{"id":1,"belongs_to_collection":null}"""));assertNull(provider(s).membership(1))}
    @Test fun missingFieldIsFailureRatherThanNoCollection()=server {s ->s.enqueue(MockResponse().setBody("""{"id":1}"""));assertThrows(IllegalStateException::class.java){provider(s).membership(1)}}
    @Test fun wrongMovieIdentityAndInvalidCollectionRejected()=server {s ->for(raw in listOf("""{"id":2,"belongs_to_collection":null}""","""{"id":1,"belongs_to_collection":{"id":-1}}""","""{"id":1,"belongs_to_collection":"bad"}""")) {s.enqueue(MockResponse().setBody(raw));assertThrows(IllegalStateException::class.java){provider(s).membership(1)}}}
    @Test fun retryAfterAndStatusPreservedWithoutResponseBody()=server {s ->s.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After","9").setBody("private server error synthetic-tmdb"));val e=assertThrows(SagaRequestFailure::class.java){provider(s).membership(1)};assertEquals(429,e.status);assertEquals(9000L,e.retryAfterMs);assertFalse(e.message!!.contains("synthetic"))}
    @Test fun authAndServerErrorsHaveSafeMessages()=server {s ->for(code in listOf(401,403,500,503)) {s.enqueue(MockResponse().setResponseCode(code).setBody("synthetic-admin"));val e=assertThrows(SagaRequestFailure::class.java){provider(s).membership(1)};assertEquals(code,e.status);assertFalse(e.message!!.contains("synthetic"))}}
    @Test fun malformedResponseRejected()=server {s ->s.enqueue(MockResponse().setBody("bad json"));assertThrows(IllegalStateException::class.java){provider(s).membership(1)}}
    @Test fun timeoutBoundedAndReportedTransient()=server {s ->s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));assertEquals(0,assertThrows(SagaRequestFailure::class.java){provider(s,timeout=100).membership(1)}.status)}
    @Test fun relayUsesExistingRouteButUnsupportedCollectionHasActionableError()=server {s ->s.enqueue(MockResponse().setBody("""{"id":1,"belongs_to_collection":null}"""));assertNull(provider(s,false).membership(1));val req=s.takeRequest();assertEquals("/admin/metadata/tmdb/movie/1?language=es-AR",req.path);assertEquals("Bearer synthetic-admin",req.getHeader("Authorization"));s.enqueue(MockResponse().setResponseCode(400));val e=assertThrows(IllegalStateException::class.java){provider(s,false).detail(10)};assertTrue(e.message!!.contains("Bearer Token local"))}
    @Test fun collectionDetailsValidatePartsAndIdentity()=server {s ->s.enqueue(MockResponse().setBody("""{"id":10,"name":"Saga","parts":[{"id":1,"title":"Uno"}]}"""));assertEquals(10L,provider(s).detail(10).number("id"));assertEquals("/3/collection/10?language=es-AR",s.takeRequest().path)}
}
