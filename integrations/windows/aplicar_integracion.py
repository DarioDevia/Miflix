"""Usage: python aplicar_integracion.py PATH_TO_AgenteVideoteca/source

Only modifies the inspected 1.13.0 source when every original SHA matches.
Creates backups first. Does not read, print or modify credentials or user catalogs.
"""
import hashlib
from pathlib import Path
import shutil
import sys


def main():
    app = Path(sys.argv[1]).resolve() / "app"
    expected = {
        # Filled from the reviewed delivery, not inferred from its filename.
        "sincronizacion_miflix.py": "b99183735fb7638b700c88330f884d993b3a1543404e910dee3ec2d1d0a92789",
        "control_catalogo.py": "d6ee1d9523c85e78318b7ffc937019248cc3573397b913099fdba14938671ae2",
        "gui.py": "eb01d9d0511539ff6e2c0d47b29fc6e11a04d272671d35aab7f0fbe0fd851e8f",
    }
    originals = {}
    for filename, digest in expected.items():
        path = app / filename
        raw = path.read_bytes()
        if hashlib.sha256(raw).hexdigest() != digest:
            raise SystemExit(f"{filename} no coincide con Windows 1.13.0 revisado. No se modificó nada.")
        originals[filename] = raw.decode("utf-8")
    if (app / "checkpoint_compartido.py").exists():
        raise SystemExit("Ya existe checkpoint_compartido.py. Revisá esa integración antes de continuar.")
    sync = originals["sincronizacion_miflix.py"]
    sync = sync.replace("import requests\n", "import requests\nfrom checkpoint_compartido import descargar_checkpoint, publicar_checkpoint\n", 1)
    sync = sync.replace("    cantidad = reemplazar_catalogo_remoto(catalogo, ruta_catalogo)\n", "    aviso_checkpoint = descargar_checkpoint(endpoint, token)\n    cantidad = reemplazar_catalogo_remoto(catalogo, ruta_catalogo)\n", 1)
    sync = sync.replace('return {"ok": True, "elementos": cantidad, "catalogo": catalogo}', 'return {"ok": True, "elementos": cantidad, "catalogo": catalogo, "aviso_checkpoint": aviso_checkpoint}', 1)
    sync = sync.replace("    return resultado\n", "    try:\n        publicar_checkpoint(endpoint, token)\n    except RuntimeError as error:\n        raise ErrorSincronizacion(\"Catálogo publicado; checkpoint todavía pendiente. \" + str(error)) from error\n    return resultado\n", 1)
    control = originals["control_catalogo.py"].replace("from catalogo import catalogo_para_android\n", "from catalogo import catalogo_para_android\nfrom checkpoint_compartido import huella_checkpoint\n", 1)
    control = control.replace("    datos = _sin_datos_efimeros(catalogo_para_android(ruta))\n", "    datos = _sin_datos_efimeros(catalogo_para_android(ruta))\n    datos[\"_checkpoint_local\"] = huella_checkpoint()\n", 1)
    gui = originals["gui.py"].replace("        cantidad = resultado.get(\"elementos\", 0)\n", "        cantidad = resultado.get(\"elementos\", 0)\n        aviso = resultado.get(\"aviso_checkpoint\")\n        if aviso:\n            messagebox.showwarning(\"Checkpoint compartido\", aviso, parent=self)\n", 1)
    changed = {"sincronizacion_miflix.py": sync, "control_catalogo.py": control, "gui.py": gui}
    for filename, text in changed.items():
        if text == originals[filename]:
            raise SystemExit("No se encontró un punto de integración. No se modificó nada.")
        compile(text, filename, "exec")
    backup = app / "backup_checkpoint_1.13.0"
    if backup.exists():
        raise SystemExit("Ya existe el backup de integración; no se sobrescribió.")
    backup.mkdir()
    for filename in changed:
        shutil.copy2(app / filename, backup / filename)
    shutil.copy2(Path(__file__).with_name("checkpoint_compartido.py"), app / "checkpoint_compartido.py")
    for filename, text in changed.items():
        (app / filename).write_text(text, encoding="utf-8", newline="\n")
    print("Integración de checkpoint aplicada sobre la fuente 1.13.0. Recompilá y validá antes de usarla.")


if __name__ == "__main__":
    main()
