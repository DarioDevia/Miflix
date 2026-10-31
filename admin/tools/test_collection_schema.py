"""Formal JSON Schema checks for the local collection interchange contract.
Run with a development Python containing jsonschema; no Android runtime dependency.
"""
import copy
import json
import pathlib
import unittest
import jsonschema

SCHEMA=json.loads((pathlib.Path(__file__).resolve().parents[1]/'colecciones.schema.json').read_text())
VALID={'format':'miflix-collections','version':1,'colecciones':[{'id':'col-example','nombre':'Mundo Marvel','origen':'manual','orden':0,'visible':True,'titulos':['movie','series','anime','temporarily-absent']}]}

class CollectionSchemaTest(unittest.TestCase):
    def test_schema_itself_and_valid_mixed_collection(self):
        jsonschema.Draft202012Validator.check_schema(SCHEMA)
        jsonschema.validate(VALID,SCHEMA)
    def test_unknown_properties_are_preserved_by_contract(self):
        x=copy.deepcopy(VALID);x['future']={'nested':True};x['colecciones'][0]['future_collection']=[1,2]
        jsonschema.validate(x,SCHEMA)
    def test_old_empty_backup_is_valid(self):
        x=copy.deepcopy(VALID);x['colecciones']=[];jsonschema.validate(x,SCHEMA)
    def test_unknown_schema_rejected(self):
        x=copy.deepcopy(VALID);x['version']=2
        with self.assertRaises(jsonschema.ValidationError):jsonschema.validate(x,SCHEMA)
    def test_duplicates_rejected(self):
        x=copy.deepcopy(VALID);x['colecciones'][0]['titulos']=['movie','movie']
        with self.assertRaises(jsonschema.ValidationError):jsonschema.validate(x,SCHEMA)
    def test_full_records_cannot_be_members(self):
        x=copy.deepcopy(VALID);x['colecciones'][0]['titulos']=[{'id':'movie'}]
        with self.assertRaises(jsonschema.ValidationError):jsonschema.validate(x,SCHEMA)
    def test_tmdb_origin_requires_positive_id(self):
        x=copy.deepcopy(VALID);x['colecciones'][0]['origen']='tmdb'
        with self.assertRaises(jsonschema.ValidationError):jsonschema.validate(x,SCHEMA)
        x['colecciones'][0]['tmdb_collection_id']=10;jsonschema.validate(x,SCHEMA)
    def test_non_https_and_credentials_are_not_valid_images(self):
        for value in ['http://example.invalid/a.jpg','https://name:secret@example.invalid/a.jpg']:
            x=copy.deepcopy(VALID);x['colecciones'][0]['poster_url']=value
            with self.assertRaises(jsonschema.ValidationError):jsonschema.validate(x,SCHEMA)

if __name__=='__main__':unittest.main()
