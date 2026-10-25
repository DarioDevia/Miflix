"""Bridge opt-in for Windows 1.13.0; requires the additive Worker checkpoint endpoint."""
import hashlib
import json
import os
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

import requests
from catalogo import telegram_url_mensaje_valida
from progreso_carga import cargar_checkpoint, ruta_checkpoint


def huella_checkpoint():
    cp = cargar_checkpoint()
    if not cp:
        return ""
    return hashlib.sha256(json.dumps(cp, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def _marcador():
    return Path(ruta_checkpoint()).with_name("checkpoint_cloudflare_confirmado.json")


def _escribir(destino, datos):
    destino = Path(destino)
    destino.parent.mkdir(parents=True, exist_ok=True)
    temporal = destino.with_name(destino.name + ".tmp")
    with temporal.open("w", encoding="utf-8") as archivo:
        json.dump(datos, archivo, ensure_ascii=False, indent=2)
        archivo.flush()
        os.fsync(archivo.fileno())
    temporal.replace(destino)


def confirmar_checkpoint():
    _escribir(_marcador(), {"huella": huella_checkpoint()})


def checkpoint_pendiente():
    try:
        confirmado = json.loads(_marcador().read_text(encoding="utf-8")).get("huella", "")
    except (OSError, ValueError):
        # A legacy checkpoint is not silently uploaded over a newer remote checkpoint.
        return False
    return huella_checkpoint() != confirmado


def _url(endpoint):
    partes = urlsplit(endpoint)
    ruta = partes.path.rstrip("/")
    if ruta.endswith("/admin/catalogo"):
        ruta = ruta[:-len("/admin/catalogo")]
    elif ruta.endswith("/catalogo.json"):
        ruta = ruta[:-len("/catalogo.json")]
    return urlunsplit((partes.scheme, partes.netloc, ruta + "/admin/checkpoint", "", ""))


def _solicitar(method, endpoint, token, cp=None):
    try:
        respuesta = requests.request(method, _url(endpoint), headers={"Authorization": f"Bearer {token}"}, json=cp, timeout=20, allow_redirects=False)
    except requests.RequestException as error:
        raise RuntimeError("No se pudo conectar con el checkpoint compartido.") from error
    if not respuesta.ok:
        raise RuntimeError(f"Checkpoint compartido: HTTP {respuesta.status_code}. Revisá la extensión del Worker.")
    try:
        datos = respuesta.json()
    except ValueError as error:
        raise RuntimeError("Respuesta de checkpoint inválida.") from error
    if not isinstance(datos, dict) or datos.get("ok") is not True:
        raise RuntimeError("El servidor no confirmó el checkpoint.")
    return datos


def descargar_checkpoint(endpoint, token):
    datos = _solicitar("GET", endpoint, token)
    cp = datos.get("checkpoint")
    if cp is not None and (not isinstance(cp, dict) or not cp.get("titulo") or cp.get("tipo") != "pelicula" or not telegram_url_mensaje_valida(cp.get("telegram_url"))):
        raise RuntimeError("El checkpoint remoto no es válido; la copia local se conserva.")
    if checkpoint_pendiente():
        return "El checkpoint local sigue pendiente; no fue reemplazado."
    if cp is not None:
        _escribir(ruta_checkpoint(), cp)  # Preserve original URL, message ID and processing time exactly.
    elif Path(ruta_checkpoint()).exists():
        # No shared checkpoint yet: retain legacy local progress as pending for explicit synchronization.
        _escribir(_marcador(), {"huella": ""})
        return "No hay checkpoint remoto. El avance local quedó pendiente para sincronizar."
    confirmar_checkpoint()
    return ""


def publicar_checkpoint(endpoint, token):
    if not checkpoint_pendiente():
        return
    cp = cargar_checkpoint()
    if not cp or not telegram_url_mensaje_valida(cp.get("telegram_url")):
        raise RuntimeError("El avance pendiente necesita el enlace original de Telegram.")
    _solicitar("PUT", endpoint, token, cp)
    confirmar_checkpoint()
