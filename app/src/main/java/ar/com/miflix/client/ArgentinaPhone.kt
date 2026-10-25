package ar.com.miflix.client

internal fun normalizeArgentinaPhone(input: String): String {
    val compact = input.filterNot { it.isWhitespace() || it == '-' }
    val national = compact.removePrefix("+54")
    require(national.isNotEmpty() && national.all { it in '0'..'9' }) {
        "Ingresá tu código de área y número, usando solamente dígitos."
    }
    return "+54$national"
}

internal fun isArgentinaPhoneInput(input: String): Boolean =
    input.all { it in '0'..'9' || it.isWhitespace() || it == '-' || it == '+' }
