package ar.com.miflix.admin

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class TvdbConfigTest {
    private fun codec()=KeyGenerator.getInstance("AES").apply {init(256)}.generateKey().let {key ->TvdbSecretCodec {key}}
    @Test fun acceptsDocumentedStringWithoutInventedUuidRestriction() {assertEquals("synthetic-key",TvdbCredentials(" synthetic-key ").normalized().apiKey)}
    @Test fun missingKeyRejected() {assertThrows(IllegalArgumentException::class.java){TvdbCredentials(" \n ").normalized()}}
    @Test fun embeddedWhitespaceOrOversizeRejected() {for(key in listOf("one two","bad\nkey","x".repeat(513))) assertThrows(IllegalArgumentException::class.java){TvdbCredentials(key).normalized()}}
    @Test fun optionalPinOmittedByDefaultAndValidated() {assertEquals("",TvdbCredentials("key").normalized().pin);assertThrows(IllegalArgumentException::class.java){TvdbCredentials("key","bad pin").normalized()}}
    @Test fun secretsNeverInObjectToString() {val s=TvdbCredentials("test-private-key","test-pin").toString();assertFalse(s.contains("test-private"));assertFalse(s.contains("test-pin"))}
    @Test fun ciphertextRoundTripsBothCredentials() {val c=codec();val raw=c.encrypt(TvdbCredentials("synthetic-key","synthetic-pin"));assertFalse(raw.contains("synthetic"));val decoded=c.decrypt(raw);assertEquals("synthetic-key",decoded.apiKey);assertEquals("synthetic-pin",decoded.pin)}
    @Test fun newRandomIvOnEveryWrite() {val c=codec();val key=TvdbCredentials("synthetic-key");assertNotEquals(c.encrypt(key),c.encrypt(key))}
    @Test fun tamperingFailsClosed() {val c=codec();val obj=com.google.gson.JsonParser.parseString(c.encrypt(TvdbCredentials("synthetic-key"))).asJsonObject;val bytes=java.util.Base64.getDecoder().decode(obj.text("data"));bytes[0]=(bytes[0].toInt() xor 1).toByte();obj.addProperty("data",java.util.Base64.getEncoder().encodeToString(bytes));assertThrows(Exception::class.java){c.decrypt(obj.toString())}}
    @Test fun differentDeviceKeyCannotDecrypt() {assertThrows(Exception::class.java){codec().decrypt(codec().encrypt(TvdbCredentials("synthetic-key")))}}
    @Test fun credentialReplacementDoesNotChangeCatalogConfiguration() {val config=AdminConfig("https://example.invalid","worker-synthetic","tmdb-synthetic");val c=codec();val old=c.encrypt(TvdbCredentials("synthetic-old"));val newer=c.encrypt(TvdbCredentials("synthetic-new"));assertEquals("synthetic-new",c.decrypt(newer).apiKey);assertEquals("synthetic-old",c.decrypt(old).apiKey);assertEquals("worker-synthetic",config.token)}
}
