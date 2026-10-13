package ar.com.miflix.client

/** Build configuration diagnosis, not a replacement for TDLib authorization. */
internal fun tvTelegramSetupMessage(apiId: Int, hashPresent: Boolean): String? =
    if (apiId > 0 && hashPresent) null else
        "Esta APK no incluye la configuración de Telegram. Podés explorar el catálogo, " +
        "pero para iniciar sesión necesitás compilar una APK con tu configuración privada. " +
        "Después podrás ingresar teléfono, código y, si corresponde, contraseña de dos pasos."
