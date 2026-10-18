# MiFlix Mobile 0.9.9-diagnostico / 33 — Identidad 0.1.0

## Base y alcance

Base comprobada `feature/mobile-0.9.8-home-rows`, `97ee3ae61a9efb10d632ddea2bd5e63e57b34de1`, 0.9.8 / 32, limpia. Rama nueva `feature/mobile-0.9.9-google-identity`, sin merge. Configuración añade Cuenta MiFlix; no interviene en onboarding, Home, intro, Telegram ni Player. Sin cuenta Google se puede usar toda la funcionalidad anterior. No se cambia ningún identificador ni almacenamiento local.

## Flujo y duración

Botón explícito → Credential Manager/GetSignInWithGoogleOption con nonce aleatorio de 32 bytes → ID token en memoria → POST HTTPS al Worker separado → comprobación criptográfica → identidad pública confirmada, basada en `(google, sub)`.

No se toma el correo/ID informado por Android como prueba ni se decodifica el JWT para aceptarlo. Nombre/correo solo proceden de la respuesta HTTPS del servicio configurado. Email es opcional y solo se devuelve con email_verified=true; nunca es clave ni autorización.

No hay cookie, refresh token, sesión MiFlix persistente ni JWT propio. Se descarta el ID token tras el intercambio; el estado público queda solo en memoria hasta exp. Al volver a Configuración se comprueba vencimiento; un timer elimina la confirmación al vencer, incluso si se estaba en otra sección (al suspender Android se ejecutará al recuperar el hilo). Cerrar proceso o recrear Activity descarta la cuenta y exige otro inicio explícito. Eso es restauración segura, no una sesión falsamente recuperada de preferencias. La orientación ya gestionada por la Activity no se cambia.

Cerrar sesión elimina inmediatamente la identidad y llama clearCredentialState: no cierra Telegram ni elimina la cuenta Google del teléfono ni revoca el consentimiento OAuth. No implica revocación universal del ID token ya emitido, que seguirá siendo bearer hasta exp. La operación en curso se cancela al abandonar la sección; la petición HTTP está limitada a 20 s y no se reintenta automáticamente. Un doble toque no genera otro flujo. No hay bucles de renovación. El selector Google tiene límite de 120 s y limpieza de estado 10 s. Fallos/cancelaciones no se muestran con excepciones técnicas ni tokens.

## Google Cloud: pasos del propietario

1. Crear o elegir un proyecto separado en Google Cloud Console / Google Auth Platform. Configurar Branding (nombre MiFlix, correo de soporte/contacto y datos que Google solicite) y Audience. Si es externo en modo Testing, añadir las cuentas familiares como Test users donde aplique. No habilitar APIs de Drive/Gmail ni facturación para esta tarea. Para una publicación futura revisar requisitos de verificación/privacidad reales de Google; no declarar la marca verificada ahora.
2. Crear cliente OAuth **Android** con paquete exacto `ar.com.miflix.client` y SHA-1 de la firma que realmente instalarás. Ejecutar `./gradlew signingReport` desde el proyecto en tu PC. Registrar debug local y release con sus huellas respectivas, como clientes Android separados. SHA-256 también puede aparecer en signingReport: conservarlo como dato de firma, pero no sustituye el SHA-1 que pide ese formulario. Si luego se usa Play App Signing registrar la firma distribuida por Play, no solo upload key.
3. Crear cliente OAuth **Web application** del mismo proyecto para el backend. Su **Client ID** será el serverClientId/audiencia de Android. No usar el ID Android en ese lugar. Este flujo nativo de ID token no requiere inventar redirect URIs ni descargar un client secret a la APK.
4. El propietario necesita: Client ID Web, Client ID(s) Android registrados, huellas del keystore local y origen HTTPS del Worker de usuarios una vez desplegado. Los IDs OAuth y URL son configuración pública, no contraseñas. No enviar client secret, claves de firma, tokens, sesión o credenciales Telegram al chat.
5. En tu archivo **personal** `~/.gradle/gradle.properties` (Windows: `%USERPROFILE%\.gradle\gradle.properties`) agregar valores reales:

```properties
MIFLIX_GOOGLE_WEB_CLIENT_ID=
MIFLIX_IDENTITY_URL=
```

Los valores quedan vacíos aquí deliberadamente; no son credenciales falsas. Completar con ID Web real y origen `https://...` del Worker independiente, sin `/auth/google`, query, fragment ni usuario/contraseña. La aplicación añade la ruta. También se admiten `-P` de Gradle para estos dos valores públicos. No poner un OAuth secret en ninguna propiedad. Telegram mantiene telegram.local.properties privado sin cambios. No tocar .gitignore para subirlo.
6. En Worker configurar GOOGLE_WEB_CLIENT_IDS con el mismo ID Web; GOOGLE_ANDROID_CLIENT_IDS con los IDs Android habilitados para validar azp si Google lo emite. Ver instrucciones del Worker. Compilar APK con firma registrada. Un debug de Actions sin estas propiedades tiene el botón deshabilitado; no es prueba de Google real. La debug de CI además tiene su propia firma: no confundirla con debug local/release.

## Dependencias

Se añaden únicamente Credential Manager y su proveedor Play Services 1.3.0, y googleid 1.1.1. Proporcionan botón Google, extracción de credencial y cierre de estado. Se fijan versiones estables compatibles con compileSdk 34/AGP existente para evitar actualizar la arquitectura por esta tarea; no se incorporan releases alpha sugeridas por ejemplos recientes. No se cambian Kotlin, Compose, Media3 ni Gradle. El dispositivo requiere Google Play Services compatible. Worker usa jose 6.x (WebCrypto, JWKS y JWT), TypeScript, tsx para pruebas y Wrangler para empaquetado/despliegue manual; no hay SDK de Cloudflare ni JOSE en Android.

## Pruebas y límites

Los tests JVM verifican estados sin cuenta, inicio único, cancelación, fallos, confirmación, expiración, salida, configuración y contrato de respuesta. No simulan el selector de Google ni demuestran acceso a Internet real. Los tests del Worker generan pares RSA efímeros, firman y verifican con jose, prueban rechazos y caché/rotación con un JWKS de prueba. El resolver de producción solo usa el endpoint oficial fijo; no hay llave, usuario ni bypass de prueba configurable en producción.

Ejecutar `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`. Worker: `npm ci`, `npm run lint`, `npm test`, `npm audit --omit=dev`, `npm run build`. Build Worker es **dry-run**, no despliegue. CI tiene ambos jobs sin credenciales ni deploy. Informar resultados reales en entrega; no inferir validación Samsung ni Google por compilar.

En A21s y A14:

1. Instalar actualización firmada con el mismo keystore para conservar datos. Sin configuración: Cuenta MiFlix deshabilitada; Inicio, Telegram, catálogo y reproducción deben seguir funcionando.
2. Con configuración real y Worker desplegado con autorización: abrir Configuración → Iniciar sesión con Google → elegir cuenta de prueba. Ver nombre/correo opcionales e indicador confirmado. Revisar que el servicio rechace una petición sin token/malformada (sin copiar un token real al chat/log).
3. Cancelar selector; apagar Internet y probar; restaurar conexión y reintentar manualmente. No debe quedar una identidad confirmada tras un fallo. Probar cuenta/configuración no autorizada de OAuth sin alterar Telegram.
4. Cerrar sesión MiFlix. Confirmar sesión Telegram vigente y reproducción normal. Volver a iniciar sesión con otra cuenta. Expiración exige identificación nueva; cerrar/reabrir proceso comienza sin cuenta. No hay restauración de tokens.
5. Navegar/reproducir sin cuenta. Comprobar Mi Lista, Ya vistos, cobertura, Continuar viendo, filas/Home, intro, episodios, seek, audio/subtítulos, brillo/volumen, fullscreen y rotación.

## Próxima etapa, sin implementar

Antes de D1/sincronización: definir allowlist de `(provider, sub)` administrada exclusivamente en servidor, autorización denegada por defecto, protocolo de sesión/refresh/revocación y protección anti-replay de desafíos de un solo uso, privacidad y borrado. Luego un user_id interno asociado a provider/sub, tablas y migraciones D1 separadas del catálogo, versionado de datos y política explícita de asociación/conflictos. No asociar ni subir automáticamente datos locales solo porque alguien conectó Google en un teléfono compartido. No convertir el correo en clave, ni permitir que cambiar de Google mezcle bibliotecas. Nada de esto se ejecuta en 0.9.9.

## Fuentes oficiales revisadas

- https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
- https://developer.android.com/jetpack/androidx/releases/credentials
- https://developers.google.com/identity/gsi/web/guides/verify-google-id-token
- https://github.com/panva/jose/tree/main/docs
- https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/
