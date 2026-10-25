"""Unit tests; no real server, token, or user state."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

sys.modules.setdefault('catalogo', types.SimpleNamespace(telegram_url_mensaje_valida=lambda u: u if isinstance(u,str) and u.startswith('https://t.me/') else None))
local = None
sys.modules.setdefault('progreso_carga', types.SimpleNamespace(ruta_checkpoint=lambda: local, cargar_checkpoint=lambda: json.loads(local.read_text()) if local.exists() else None))
spec = importlib.util.spec_from_file_location('checkpoint_compartido', Path(__file__).with_name('checkpoint_compartido.py'))
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)

class BridgeTests(unittest.TestCase):
    def setUp(self):
        global local
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        local=Path(self.tmp.name)/'checkpoint.json'
        self.cp={'tipo':'pelicula','titulo':'Nueva','telegram_url':'https://t.me/c/123/55/77?single','telegram_message_id':77,'procesado_at':'2026-10-06T00:00:00Z'}
    def put(self, cp): local.write_text(json.dumps(cp))
    def test_remote_exact_url_and_fields(self):
        with patch.object(bridge,'_solicitar',return_value={'ok':True,'checkpoint':self.cp}): bridge.descargar_checkpoint('https://worker/admin/catalogo','test')
        self.assertEqual(json.loads(local.read_text()),self.cp)
        self.assertFalse(bridge.checkpoint_pendiente())
    def test_legacy_does_not_silently_publish(self):
        self.put(self.cp)
        with patch.object(bridge,'_solicitar') as request: bridge.publicar_checkpoint('https://worker','test');request.assert_not_called()
    def test_pending_local_not_overwritten(self):
        self.put(self.cp);bridge.confirmar_checkpoint()
        newer=dict(self.cp,titulo='Pendiente');self.put(newer)
        with patch.object(bridge,'_solicitar',return_value={'ok':True,'checkpoint':self.cp}): self.assertIn('pendiente',bridge.descargar_checkpoint('https://worker','test'))
        self.assertEqual(json.loads(local.read_text()),newer)
    def test_null_remote_retains_legacy_and_explicit_publish(self):
        self.put(self.cp)
        with patch.object(bridge,'_solicitar',return_value={'ok':True,'checkpoint':None}): bridge.descargar_checkpoint('https://worker','test')
        self.assertTrue(bridge.checkpoint_pendiente())
        with patch.object(bridge,'_solicitar',return_value={'ok':True}) as request:
            bridge.publicar_checkpoint('https://worker','test');self.assertEqual(request.call_args.args[0],'PUT')
        self.assertFalse(bridge.checkpoint_pendiente())
    def test_failure_preserves_pending(self):
        self.put(self.cp);bridge.confirmar_checkpoint();self.put(dict(self.cp,titulo='Pendiente'))
        with patch.object(bridge,'_solicitar',side_effect=RuntimeError('fallo')):
            with self.assertRaises(RuntimeError): bridge.publicar_checkpoint('https://worker','test')
        self.assertTrue(bridge.checkpoint_pendiente())
    def test_invalid_remote_preserves_local(self):
        self.put(self.cp)
        with patch.object(bridge,'_solicitar',return_value={'ok':True,'checkpoint':{'titulo':'Mal','tipo':'anime'}}):
            with self.assertRaises(RuntimeError): bridge.descargar_checkpoint('https://worker','test')
        self.assertEqual(json.loads(local.read_text()),self.cp)
    def test_endpoint_normalization(self):
        for url in ['https://worker','https://worker/admin/catalogo','https://worker/catalogo.json']:
            self.assertEqual(bridge._url(url),'https://worker/admin/checkpoint')

if __name__=='__main__': unittest.main()
