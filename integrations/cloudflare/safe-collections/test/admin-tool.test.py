import importlib.util,json,pathlib,tempfile,unittest
spec=importlib.util.spec_from_file_location('admin_tool',pathlib.Path(__file__).parents[1]/'admin-tool.py')
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
class Backups(unittest.TestCase):
 def test_roundtrip_hash_and_no_overwrite(self):
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)/'snapshot.json';value={'schema_version':1,'items':[{'id':'a','titulo':'Película'}],'unknown':{'retain':True}}
   digest=mod.write(p,value);self.assertEqual(len(digest),64);self.assertEqual(mod.read(p),value);self.assertEqual(mod.verify(value)['items'],1)
   with self.assertRaises(FileExistsError):mod.write(p,{})
 def test_version_type_and_duplicate_ids(self):
  for value in [{'schema_version':'1','items':[]},{'schema_version':True,'items':[]},{'schema_version':1,'items':[{'id':'x'},{'id':'x'}]}]:
   with self.assertRaises(ValueError):mod.verify(value)
 def test_backup_counts(self):
  v={'format':'miflix-worker-backup','version':1,'state':{'catalog':{'schema_version':1,'items':[{'id':'a'}]},'collections':{'colecciones':[{'id':'c'}]}}}
  self.assertEqual(mod.verify(v),{'items':1,'colecciones':1,'schema_version_type':'number'})
if __name__=='__main__':unittest.main()
