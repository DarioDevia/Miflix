# MiFlix Identidad 0.1.0

Worker TypeScript **independiente** del catálogo. No KV, D1, Google secret, cookies ni datos de biblioteca. Sin deploy automático. Android Mobile 0.9.9 puede compilar sin configurar este servicio.

## Rutas

- `GET /health`: estado/nombre/versión, sin usuarios ni configuración.
- `POST /auth/google`: HTTPS, application/json, cuerpo máximo 12 KiB (también por streaming), solo `{idToken, nonce}`. Token máximo 8 KiB, nonce base64url de 32 bytes/43 caracteres. Lectura limitada a 5 s. Formatos/tamaños incorrectos: 400/413/415; timeout: 408.
- Verifica con **jose**/WebCrypto, RS256 obligatorio y JWKS oficial fijo `https://www.googleapis.com/oauth2/v3/certs`. No sigue URLs jku/x5u del token. Caché por isolate 10 min, cooldown 30 s para recarga por kid nuevo, timeout 5 s; las claves no están hardcodeadas. JWKS no disponible: falla cerrado 503.
- Verifica firma, iss (`https://accounts.google.com` o `accounts.google.com`), aud Web explícita, exp, iat (no futuro, edad máxima 1 h), sub no vacío, nonce exacto. aud debe ser string único. Si azp existe, debe estar entre Web/Android explícitamente configurados. exp debe ser entero, posterior a iat y con vida <=2 h. Sin tolerancia de reloj para tokens vencidos.
- Éxito 200: `{authenticated:true,identity:{provider:"google",sub,name?,email?},expiresAt,authorization:{privateDataAllowed:false}}`. No devuelve el token ni un access token. Email solo si Google lo declara verificado; es display, no clave/autorización. Rechazos JWT: 401 genérico, sin detalles de claims. Errores de infraestructura: 503. Sin configuración/binding: 503. Rate limit: 429.

## Preparar y desplegar — solo con autorización del propietario

1. Leer [Google/Android y pruebas físicas](../../docs/mobile-0.9.9-identidad.md). No necesitas un OAuth client secret para verificar ID tokens.
2. En esta carpeta: `npm ci`, `npm run lint`, `npm test`, `npm audit --omit=dev`, `npm run build`. El último usa `wrangler deploy --dry-run`, **no publica**. El lockfile fija el árbol instalado. Node 24 recomendado.
3. Completar localmente `wrangler.jsonc`: `GOOGLE_WEB_CLIENT_IDS` (ID(s) Web reales, separados por coma), `GOOGLE_ANDROID_CLIENT_IDS` (IDs Android reales para azp). Están vacíos de forma intencional: no hay IDs inventados de producción. Son datos públicos de configuración, no secrets. Nunca poner el client secret allí.
4. Revisar nombre `miflix-usuarios`: debe ser un Worker nuevo, no el de catálogo. Revisar `AUTH_RATE_LIMITER.namespace_id=1099001`; elegir un entero libre en tu cuenta para no compartir cuotas con otro Worker. 20 solicitudes/minuto por clave de red y ubicación. Revisar disponibilidad/cuotas/precios del plan y detenerse si requiere activar pagos; no se habilita un plan pago con este código.
5. **Tras autorizar el despliegue**, el propietario ejecuta `npx wrangler login` en su PC y `npm run deploy` en esta carpeta. No enviar al chat contraseñas, cookies ni API tokens. No se ejecutó deploy en esta entrega. No se modifican recursos existentes ni se enlaza KV/D1. Wrangler puede crear el Worker nuevo y su binding; revisarlos antes de confirmar.
6. Consultar `/health` del origen HTTPS generado. Una petición sin credencial a `/auth/google` debe rechazarse. Agregar ese origen y el ID Web a las propiedades locales Android, compilar con firma OAuth registrada y realizar prueba real Google. No copiar el ID token a Logcat ni al chat.

## Privacidad, amenazas y límites

Servicio solo nativo: rechaza cualquier Origin, no habilita CORS ni cookies. Esto reduce exposición web; **no autentica** por sí mismo y un cliente no navegador puede llamar el API. Toda autenticación depende de la firma y claims, nunca de CORS/headers de origen/ID enviado por el usuario.

El binding de rate limit es obligatorio (falla cerrado sin él), antes de verificar JWT. Usa hash de CF-Connecting-IP que Cloudflare fija en su edge, no sub/email declarado. Sin header se comparte la cuota unknown. Es una barrera pre-autenticación limitada: NAT familiar/red móvil comparte cuota, es por ubicación, no global; IP rotatoria/distribuida puede evadirla. No contiene un Map ilimitado en memoria ni crea almacenamiento de usuarios. No es una solución completa contra DDoS: revisar límites del plan/WAF antes de servicio público. No utilizar CF-Connecting-IP de un proxy ajeno sin garantizar que edge lo sobrescriba.

No hay logs de token/cuerpo/sub/email/errores internos, observability está deshabilitada y respuestas tienen no-store/nosniff. La plataforma sigue teniendo métricas de infraestructura y metadatos normales. No añadir query strings con tokens ni middleware que registre cuerpos. HTTPS y endpoint configurado son frontera de confianza; sin pinning, con la confianza TLS normal de Android.

El nonce se genera en Android para ligar su flujo y se verifica en el token. **No es un desafío servidor de un solo uso:** este Worker sin almacenamiento no evita la repetición de un bearer robado con su nonce dentro de su validez. No se debe presentar como protección anti-replay completa. Nadie obtiene acceso privado ni sesión con ese reenvío; solo la misma prueba mínima de identidad pública. Antes de sesiones/servicios privados se necesita desafío consumible, autorización/allowlist y protocolo de revocación. Cerrar sesión local no revoca el bearer de Google, ni el consentimiento.

Toda cuenta Google válida para la audiencia puede identificarse, pero `privateDataAllowed=false` siempre. Futuros endpoints deberán exigir autenticación nuevamente y consultar autorización por sub/allowlist; jamás confiar en ese boolean de cliente. No hay roles administrativos desde APK. No hay sync/favoritos/perfiles ni asociación de datos actuales.

Los tests son criptográficos con RSA/JWKS efímeros, sin Google real, Cloudflare real ni red upstream real. La inyección de resolver existe como argumento interno para tests; el entrypoint productivo llama handle sin resolver y no admite bypass de headers/env/request. El test de rotación verifica la biblioteca con JWKS simulado y claves firmantes reales. Validación en Google/Samsung y rate limiter desplegado quedan pendientes.
