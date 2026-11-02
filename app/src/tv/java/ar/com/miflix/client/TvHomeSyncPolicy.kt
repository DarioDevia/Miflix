package ar.com.miflix.client

/** Small provider port: same validated reconciliation, testable without Android or real data. */
internal interface HomeProgramStore {
    fun rows(): List<HomeProbeStored>
    fun upsert(change: HomeProbeUpsert)
    fun delete(id: Long)
}
internal data class HomeApplied(val plan: HomeProbePlan, val rows: List<HomeProbeStored>)
internal fun homeApplyVerified(store: HomeProgramStore, desired: List<HomeProbeDesired>,
    active: () -> Unit = {}): HomeApplied {
    if(desired.isEmpty()) return HomeApplied(HomeProbePlan(emptyList(),emptyList()),emptyList())
    active()
    val plan=homeProbePlan(store.rows(),desired)
    for(change in plan.upserts) { active();store.upsert(change) }
    active()
    check(homeProbePlan(store.rows(),desired).upserts.isEmpty())
    // Replacement readback is mandatory before even the first obsolete-program deletion.
    for(id in plan.deleteIds) { active();store.delete(id) }
    active()
    val after=store.rows()
    val remaining=homeProbePlan(after,desired)
    check(remaining.upserts.isEmpty() && remaining.deleteIds.isEmpty())
    return HomeApplied(plan,after)
}

/** Process-local coalescing, no timer/service. A new process always checks the real provider. */
internal class HomeSyncGate {
    private var success: String?=null
    private var failed: String?=null
    private var failedAt=0L
    fun shouldAttempt(key:String, now:Long):Boolean = key!=success &&
        (key!=failed || now<failedAt || now-failedAt>=10*60*1000L)
    fun succeeded(key:String) { success=key;failed=null }
    fun failed(key:String,now:Long) { failed=key;failedAt=now }
}

internal fun homeImageLeaf(parts:List<String>, query:String?, fragment:String?):String? =
    parts.singleOrNull()?.takeIf { query==null && fragment==null && Regex("[a-f0-9]{64}\\.png").matches(it) }

internal fun tvHomeCredentialsValid(id:String,hash:String):Boolean =
    id.toIntOrNull()?.let { it>0 }==true && Regex("[0-9a-fA-F]{32}").matches(hash)
