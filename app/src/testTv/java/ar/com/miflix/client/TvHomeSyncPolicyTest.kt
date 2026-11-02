package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvHomeSyncPolicyTest {
    private fun desired(key:String)=HomeProbeDesired(HOME_PROBE_PREFIX+key,mapOf("title" to key))
    private class Fake(val values:MutableList<HomeProbeStored>):HomeProgramStore {
        var writes=0;var deletes=0;var reads=0
        var failQuery=false;var failWrite=false;var discardWrites=false;var failDelete=false
        override fun rows():List<HomeProbeStored> { reads++;check(!failQuery);return values.toList() }
        override fun upsert(change:HomeProbeUpsert) {
            check(!failWrite);writes++
            if(discardWrites)return
            values.removeAll { it.id==change.existingId }
            values+=HomeProbeStored(change.existingId ?: 100L+values.size,change.desired.key,change.desired.payload)
        }
        override fun delete(id:Long) { check(!failDelete);deletes++;values.removeAll { it.id==id } }
    }
    private fun previous()=Fake((1..5).map { HomeProbeStored(it.toLong(),HOME_PROBE_PREFIX+"old$it",emptyMap()) }.toMutableList())
    @Test fun emptySelectionDoesNotQueryOrWriteEvenIfProviderIsUnavailable() {
        val store=previous().apply { failQuery=true }
        homeApplyVerified(store,emptyList())
        assertEquals(5,store.values.size);assertEquals(0,store.reads);assertEquals(0,store.writes);assertEquals(0,store.deletes)
    }
    @Test fun providerUnavailableCannotDeletePrograms() {
        val store=previous().apply { failQuery=true }
        try { homeApplyVerified(store,listOf(desired("new")));fail() }catch(_:IllegalStateException){}
        assertEquals(5,store.values.size);assertEquals(0,store.deletes)
    }
    @Test fun failedUpsertConservesOldPrograms() {
        val store=previous().apply { failWrite=true }
        try { homeApplyVerified(store,listOf(desired("new")));fail() }catch(_:IllegalStateException){}
        assertEquals(5,store.values.size);assertEquals(0,store.deletes)
    }
    @Test fun unconfirmedReplacementBlocksAllDeletions() {
        val store=previous().apply { discardWrites=true }
        try { homeApplyVerified(store,listOf(desired("new")));fail() }catch(_:IllegalStateException){}
        assertEquals(5,store.values.size);assertEquals(0,store.deletes)
    }
    @Test fun realReplacementReadbackPrecedesRemovalAndForeignProgramsSurvive() {
        val store=previous().apply { values+=HomeProbeStored(99,"foreign",emptyMap()) }
        homeApplyVerified(store,listOf(desired("new")))
        assertEquals(1,store.writes);assertEquals(5,store.deletes)
        assertTrue(store.values.any { it.key=="foreign" })
        assertTrue(store.values.any { it.key==HOME_PROBE_PREFIX+"new" })
        val writes=store.writes;val deletes=store.deletes
        val repeated=homeApplyVerified(store,listOf(desired("new")))
        assertTrue(repeated.plan.upserts.isEmpty());assertTrue(repeated.plan.deleteIds.isEmpty())
        assertEquals(writes,store.writes);assertEquals(deletes,store.deletes)
    }
    @Test fun deletionFailureLeavesVerifiedReplacementAndCanBeReconciledLater() {
        val store=previous().apply { failDelete=true }
        try { homeApplyVerified(store,listOf(desired("new")));fail() }catch(_:IllegalStateException){}
        assertTrue(store.values.any { it.key==HOME_PROBE_PREFIX+"new" })
        store.failDelete=false
        homeApplyVerified(store,listOf(desired("new")))
        assertEquals(1,store.values.size)
    }
    @Test fun processGateCoalescesIdenticalSuccessAndBacksOffFailuresButAllowsChangedCatalog() {
        val gate=HomeSyncGate()
        assertTrue(gate.shouldAttempt("a",1000));gate.failed("a",1000)
        assertFalse(gate.shouldAttempt("a",2000));assertTrue(gate.shouldAttempt("b",2000))
        assertTrue(gate.shouldAttempt("a",601000));assertTrue(gate.shouldAttempt("a",500))
        gate.succeeded("a");assertFalse(gate.shouldAttempt("a",999999))
        assertTrue(HomeSyncGate().shouldAttempt("a",999999))
    }
    @Test fun ambiguousOwnChannelsNeverUseLocalIdTwoAsUniversalIdentity() {
        val a=HomeProbeChannel(2,"MiFlix - Prueba Home",null,"ours",true)
        val b=HomeProbeChannel(9,"MiFlix TV",HOME_PROBE_CHANNEL,"ours",true)
        assertNull(homeProbeChannel(listOf(a,b),"ours",2))
        assertEquals(9L,homeProbeChannel(listOf(b,a.copy(owner="other")),"ours",2)?.id)
        assertNull(homeProbeChannel(listOf(a.copy(owner="other")),"ours",2))
        assertNull(homeProbeChannel(emptyList(),"ours",2))
    }
    @Test fun providerImageLeavesRejectTraversalQueriesAndUnrelatedFiles() {
        assertEquals("a".repeat(64)+".png",homeImageLeaf(listOf("a".repeat(64)+".png"),null,null))
        for(parts in listOf(emptyList(),listOf("..","secret"),listOf("telegram.local.properties"),listOf("x.png")))
            assertNull(homeImageLeaf(parts,null,null))
        assertNull(homeImageLeaf(listOf("a".repeat(64)+".png"),"file=secret",null))
    }
    @Test fun credentialsValidationUsesOnlySyntheticValuesAndDoesNotAcceptMalformedInputs() {
        assertTrue(tvHomeCredentialsValid("12345","0".repeat(32)))
        for(id in listOf("0","-1","999999999999","x")) assertFalse(tvHomeCredentialsValid(id,"0".repeat(32)))
        for(hash in listOf("","short","g".repeat(32))) assertFalse(tvHomeCredentialsValid("12345",hash))
    }
}
