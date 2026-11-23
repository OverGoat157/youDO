"""Regression checks for preserving local secrets when moving to the root .env."""
from pathlib import Path
import stat
import tempfile
import unittest

from setup_env import assignments, prepare_env


class SetupEnvTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'backend').mkdir()
        template = Path(__file__).resolve().parents[2] / '.env.example'
        (self.root / '.env.example').write_text(template.read_text(encoding='utf-8'), encoding='utf-8')

    def values(self):
        return {key: value for key, (_, value) in assignments((self.root / '.env').read_text()).items()}

    def test_fresh_setup_generates_private_secrets_and_is_idempotent(self):
        self.assertTrue(prepare_env(self.root))
        values = self.values()
        self.assertNotEqual(values['YOUDO_DB_PASSWORD'], 'CHANGE_ME')
        self.assertNotIn('REPLACE_WITH_', '\n'.join(values.values()))
        self.assertEqual(values['PERMANENT_DELETE_ENABLED'], 'false')
        self.assertEqual(stat.S_IMODE((self.root / '.env').stat().st_mode), 0o600)
        original = (self.root / '.env').read_bytes()
        self.assertFalse(prepare_env(self.root))
        self.assertEqual((self.root / '.env').read_bytes(), original)

    def test_migrates_legacy_secrets_without_changing_kr2_or_the_backup(self):
        original = '# local KR2\nYOUDO_DB_PASSWORD=keep-kr2\nYOUDO_DB_PORT=55439\nCUSTOM_VALUE=keep'
        legacy = 'DB_PASSWORD="keep-db$#value"\nJWT_SECRET=keep-jwt\nBOOTSTRAP_ADMIN_PASSWORD=keep-admin\nHTTP_PORT=18127\n'
        (self.root / '.env').write_text(original)
        (self.root / 'backend' / '.env').write_text(legacy)
        prepare_env(self.root)
        values = self.values()
        self.assertTrue((self.root / '.env').read_text().startswith(original + '\n'))
        self.assertEqual(values['DB_PASSWORD'], '"keep-db$#value"')
        self.assertEqual(values['JWT_SECRET'], 'keep-jwt')
        self.assertEqual(values['BOOTSTRAP_ADMIN_PASSWORD'], 'keep-admin')
        self.assertEqual(values['HTTP_PORT'], '18127')
        self.assertEqual((self.root / 'backend' / '.env').read_text(), legacy)

    def test_configured_root_values_take_precedence_over_legacy_defaults(self):
        (self.root / '.env').write_text('DB_PASSWORD=root-secret\nBOOTSTRAP_ADMIN_PASSWORD=\n')
        (self.root / 'backend' / '.env').write_text('DB_PASSWORD=old-secret\nBOOTSTRAP_ADMIN_PASSWORD=old-admin\n')
        prepare_env(self.root)
        self.assertEqual(self.values()['DB_PASSWORD'], 'root-secret')
        self.assertEqual(self.values()['BOOTSTRAP_ADMIN_PASSWORD'], '')

    def test_template_placeholders_are_replaced_with_existing_legacy_secrets(self):
        (self.root / '.env').write_text((self.root / '.env.example').read_text())
        (self.root / 'backend' / '.env').write_text('DB_PASSWORD=legacy-secret\nJWT_SECRET=legacy-jwt\n')
        prepare_env(self.root)
        self.assertEqual(self.values()['DB_PASSWORD'], 'legacy-secret')
        self.assertEqual(self.values()['JWT_SECRET'], 'legacy-jwt')
        self.assertNotIn('REPLACE_WITH_', (self.root / '.env').read_text())


if __name__ == '__main__':
    unittest.main()
