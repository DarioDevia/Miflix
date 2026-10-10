package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

/** Tests diagnostic bookkeeping, not a simulated Telegram reconnection. */
class PendingPlaybackRequestsTest {
    @Test fun noPendingRequestHasZeroCountAndAge() {
        val requests = PendingPlaybackRequests()
        assertEquals(0, requests.count())
        assertEquals(0L, requests.oldestAgeMs(100))
    }
    @Test fun simultaneousRequestsAreDistinctAndOldestIsReported() {
        val requests = PendingPlaybackRequests()
        val first = requests.begin(100)
        val second = requests.begin(200)
        assertNotEquals(first, second)
        assertEquals(2, requests.count())
        assertEquals(200L, requests.oldestAgeMs(300))
        requests.end(first)
        assertEquals(1, requests.count())
        assertEquals(100L, requests.oldestAgeMs(300))
    }
    @Test fun cancelledFinishedOrLateCompletionCannotLeakOrDecrementTwice() {
        val requests = PendingPlaybackRequests()
        val id = requests.begin(100)
        requests.end(id)
        requests.end(id)
        assertEquals(0, requests.count())
        assertEquals(0L, requests.oldestAgeMs(300))
    }
    @Test fun nextRequestDoesNotReuseAnEarlierDiagnosticId() {
        val requests = PendingPlaybackRequests()
        val old = requests.begin(100)
        requests.end(old)
        val next = requests.begin(200)
        requests.end(old)
        assertNotEquals(old, next)
        assertEquals(1, requests.count())
    }
    @Test fun ageNeverBecomesNegative() {
        val requests = PendingPlaybackRequests()
        requests.begin(200)
        assertEquals(0L, requests.oldestAgeMs(100))
    }
}
