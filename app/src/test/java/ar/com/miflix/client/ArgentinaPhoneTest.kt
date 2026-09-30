package ar.com.miflix.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ArgentinaPhoneTest {
    @Test fun nationalAndPastedInternationalNumbersHaveOnePrefix() {
        for (input in listOf("2611234567", "261 123 4567", "261-123-4567",
                "+542611234567", " +54 261-123-4567 ")) {
            assertEquals("+542611234567", normalizeArgentinaPhone(input))
        }
    }

    @Test fun lettersEmptyNumbersAndOtherCountryPrefixesAreRejected() {
        for (input in listOf("", " - ", "+54", "261abc4567", "+552611234567", "261+1234567")) {
            assertThrows(IllegalArgumentException::class.java) { normalizeArgentinaPhone(input) }
        }
        assertFalse(isArgentinaPhoneInput("261abc4567"))
    }

    @Test fun nationalDigitsArePreservedWithoutAreaOrMobileRewriting() {
        assertEquals("+545412345678", normalizeArgentinaPhone("5412345678"))
        assertEquals("+540261151234567", normalizeArgentinaPhone("0261151234567"))
    }
}
