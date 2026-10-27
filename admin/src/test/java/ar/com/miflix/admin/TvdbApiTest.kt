package ar.com.miflix.admin

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.Base64

class TvdbApiTest {
    private val key="synthetic-key-only-for-tests"
    private fun login(token: String="synthetic-session")=MockResponse().setBody("""{"status":"success","data":{"token":"$token"}}""")
    private fun success()=MockResponse().setBody("""{"status":"success","data":[{"id":"spa"}]}""")
    private fun api(s: MockWebServer,clock: ()->Long={System.currentTimeMillis()},c: ()->TvdbCredentials?={TvdbCredentials(key)},timeout: Long=1000)=
        TvdbApi(c,OkHttpClient.Builder().callTimeout(timeout,TimeUnit.MILLISECONDS).followRedirects(false).build(),s.url("/v4").toString(),clock,0)
    private fun withServer(test: (MockWebServer)->Unit) {val s=MockWebServer();s.start();try {test(s)} finally {s.shutdown()}}
    @Test fun missingKeyDoesNotMakeRequest()=withServer {s ->
        val error=assertThrows(IllegalStateException::class.java) {api(s,c={null}).testConnection()}
        assertTrue(error.message!!.contains("no está configurado"));assertEquals(0,s.requestCount)
    }
    @Test fun loginOmitsPinAndUsesTokenAutomatically()=withServer {s ->
        s.enqueue(login());s.enqueue(success());api(s).testConnection()
        val req=s.takeRequest();assertEquals("/v4/login",req.path);assertEquals("POST",req.method)
        val body=JsonParser.parseString(req.body.readUtf8()).asJsonObject;assertEquals(key,body.text("apikey"));assertFalse(body.has("pin"))
        assertNull(req.getHeader("Authorization"));assertEquals("Bearer synthetic-session",s.takeRequest().getHeader("Authorization"))
    }
    @Test fun subscriberPinOnlyWhenConfigured()=withServer {s ->
        s.enqueue(login());s.enqueue(success());api(s,c={TvdbCredentials(key,"test-pin")}).testConnection()
        assertEquals("test-pin",JsonParser.parseString(s.takeRequest().body.readUtf8()).asJsonObject.text("pin"))
    }
    @Test fun tokenReusedWithoutAnotherLogin()=withServer {s ->
        s.enqueue(login());s.enqueue(success());s.enqueue(success());val a=api(s);a.testConnection();a.testConnection()
        assertEquals(3,s.requestCount);assertEquals("/v4/login",s.takeRequest().path);assertEquals("/v4/languages",s.takeRequest().path);assertEquals("/v4/languages",s.takeRequest().path)
    }
    @Test fun rejectedTokenReauthenticatesOnce()=withServer {s ->
        s.enqueue(login("old"));s.enqueue(MockResponse().setResponseCode(401));s.enqueue(login("new"));s.enqueue(success())
        api(s).testConnection();val reqs=(1..4).map {s.takeRequest()};assertEquals(listOf("/v4/login","/v4/languages","/v4/login","/v4/languages"),reqs.map {it.path});assertEquals("Bearer new",reqs.last().getHeader("Authorization"))
    }
    @Test fun repeated401StopsWithoutInfiniteRetry()=withServer {s ->
        s.enqueue(login());s.enqueue(MockResponse().setResponseCode(401));s.enqueue(login());s.enqueue(MockResponse().setResponseCode(401))
        assertTrue(assertThrows(IllegalStateException::class.java) {api(s).testConnection()}.message!!.contains("401"));assertEquals(4,s.requestCount)
    }
    @Test fun jwtExpirationTriggersNewAuthentication()=withServer {s ->
        var time=100_000L
        val jwt="e30."+Base64.getUrlEncoder().withoutPadding().encodeToString("{\"exp\":200}".toByteArray())+".signature"
        s.enqueue(login(jwt));s.enqueue(success());s.enqueue(login());s.enqueue(success());val a=api(s,clock={time})
        a.testConnection();time=250_000;a.testConnection();assertEquals(4,s.requestCount)
    }
    @Test fun resetForChangedOrDeletedKeyClearsToken()=withServer {s ->
        var c:TvdbCredentials?=TvdbCredentials(key);val a=api(s,c={c})
        s.enqueue(login());s.enqueue(success());a.testConnection();c=TvdbCredentials("replacement-synthetic-key");a.reset()
        s.enqueue(login());s.enqueue(success());a.testConnection();repeat(2){s.takeRequest()}
        assertTrue(s.takeRequest().body.readUtf8().contains("replacement-synthetic-key"));c=null;a.reset();assertThrows(IllegalStateException::class.java){a.testConnection()}
    }
    @Test fun invalidLoginIsRedacted()=withServer {s ->
        s.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"$key synthetic-session\"}"))
        val message=assertThrows(IllegalStateException::class.java){api(s).testConnection()}.message!!
        assertTrue(message.contains("401"));assertFalse(message.contains(key));assertFalse(message.contains("synthetic-session"))
    }
    @Test fun accessDeniedDoesNotReauthenticate()=withServer {s ->
        s.enqueue(login());s.enqueue(MockResponse().setResponseCode(403));assertTrue(assertThrows(IllegalStateException::class.java){api(s).testConnection()}.message!!.contains("403"));assertEquals(2,s.requestCount)
    }
    @Test fun deniedLoginIsClear()=withServer {s ->s.enqueue(MockResponse().setResponseCode(403));assertTrue(assertThrows(IllegalStateException::class.java){api(s).testConnection()}.message!!.contains("403"))}
    @Test fun rateLimitHonorsCooldownWithoutRetry()=withServer {s ->
        s.enqueue(login());s.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After","120"));val a=api(s)
        assertThrows(IllegalStateException::class.java){a.testConnection()};assertThrows(IllegalStateException::class.java){a.testConnection()};assertEquals(2,s.requestCount)
    }
    @Test fun malformedJsonCannotLeakBody()=withServer {s ->
        s.enqueue(MockResponse().setBody(key+" not JSON"));val message=assertThrows(IllegalStateException::class.java){api(s).testConnection()}.message!!;assertTrue(message.contains("JSON"));assertFalse(message.contains(key))
    }
    @Test fun missingTokenRejected()=withServer {s ->s.enqueue(MockResponse().setBody("""{"status":"success","data":{}}"""));assertThrows(IllegalStateException::class.java){api(s).testConnection()}}
    @Test fun missingStatusOrDataRejected()=withServer {s ->s.enqueue(MockResponse().setBody("{}"));assertThrows(IllegalStateException::class.java){api(s).testConnection()}}
    @Test fun emptyLanguagesNotConnectionSuccess()=withServer {s ->s.enqueue(login());s.enqueue(MockResponse().setBody("""{"status":"success","data":[]}"""));assertThrows(IllegalStateException::class.java){api(s).testConnection()}}
    @Test fun timeoutIsClearAndRedacted()=withServer {s ->
        s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));val msg=assertThrows(IllegalStateException::class.java){api(s,timeout=150).testConnection()}.message!!
        assertTrue(msg.contains("espera"));assertFalse(msg.contains(key))
    }
    @Test fun connectionFailureDoesNotExposeSecret()=withServer {s ->s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));assertFalse(assertThrows(IllegalStateException::class.java){api(s).testConnection()}.message!!.contains(key))}
    @Test fun redirectsDoNotForwardCredentials()=withServer {s ->s.enqueue(MockResponse().setResponseCode(302).addHeader("Location","https://other.invalid/"));assertThrows(IllegalStateException::class.java){api(s).testConnection()};assertEquals(1,s.requestCount)}
    @Test fun missingTranslationCanBeExplicitlyAbsent()=withServer {s ->s.enqueue(login());s.enqueue(MockResponse().setResponseCode(404));assertNull(api(s).get("/episodes/5/translations/spa",true))}
    @Test fun unsafePathRejectedBeforeAuthentication()=withServer {s ->assertThrows(IllegalArgumentException::class.java){api(s).get("//evil/../path")};assertEquals(0,s.requestCount)}
}
