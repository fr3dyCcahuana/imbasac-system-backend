import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

import crl_jobs
import pki
import publish_crl


class CrlJobsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='imbasac-crl-jobs-test-')
        cls.base = Path(cls.temp.name)
        cls.password = cls.base / 'passphrase'
        pki.write_new(cls.password, 'synthetic-crl-test-password\n')
        cls.root = pki.initialize(cls.base / 'root', 'root', cls.password, offline=True)
        cls.issuer = pki.initialize(cls.base / 'issuer', 'issuer', cls.password)
        pki.sign_issuer(cls.root, cls.issuer / 'issuer.csr.pem', cls.base / 'issuer.pem', cls.password, offline=True)
        pki.install_issuer(cls.issuer, cls.base / 'issuer.pem', cls.root / 'ca.crt.pem')
        cls.root_crl = cls.base / 'root.crl.pem'
        pki.generate_crl(cls.root, cls.root_crl, cls.password)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def configuration(self):
        temporary = tempfile.TemporaryDirectory(prefix='case-', dir=self.base)
        self.addCleanup(temporary.cleanup)
        base = Path(temporary.name)
        spool = base / 'spool'
        spool.mkdir(mode=0o700)
        public = base / 'public'
        public.mkdir(mode=0o755)
        return {'issuerDirectory': str(self.issuer), 'passphraseFile': str(self.password),
                'spoolDirectory': str(spool), 'rootCertificate': str(self.root / 'ca.crt.pem'),
                'rootCrl': str(self.root_crl), 'issuerCertificate': str(self.issuer / 'ca.crt.pem'),
                'targetBundle': str(public / 'crls.pem')}

    def prepare(self, configuration):
        crl_jobs.generate(configuration, pki, publish_crl)
        crl_jobs.publish(configuration, publish_crl, reload_nginx=False)

    def test_generation_and_bundle_are_signed_and_use_private_spool(self):
        configuration = self.configuration()
        self.prepare(configuration)
        spool = Path(configuration['spoolDirectory']) / 'issuer.crl.pem'
        publish_crl.validate(spool, self.issuer / 'ca.crt.pem')
        self.assertEqual(Path(configuration['targetBundle']).read_bytes().count(b'BEGIN X509 CRL'), 2)
        self.assertEqual({path.name for path in spool.parent.iterdir()}, {'issuer.crl.pem'})
        if os.name == 'posix':
            self.assertEqual(spool.stat().st_mode & 0o777, 0o600)
            self.assertEqual(Path(configuration['targetBundle']).stat().st_mode & 0o777, 0o644)

    def test_expired_root_crl_is_rejected_without_changing_bundle(self):
        configuration = self.configuration()
        self.prepare(configuration)
        target = Path(configuration['targetBundle'])
        previous = target.read_bytes()
        expired = target.parent / 'expired-root.pem'
        pki.run('ca', '-config', self.root / 'openssl.cnf', '-passin', pki.secret(self.password), '-gencrl',
                '-crl_lastupdate', '20200101000000Z', '-crl_nextupdate', '20200102000000Z', '-out', expired)
        configuration['rootCrl'] = str(expired)
        with self.assertRaises(ValueError):
            crl_jobs.publish(configuration, publish_crl, reload_nginx=False)
        self.assertEqual(target.read_bytes(), previous)

    def test_wrong_root_crl_signature_keeps_previous_bundle(self):
        configuration = self.configuration()
        self.prepare(configuration)
        target = Path(configuration['targetBundle'])
        previous = target.read_bytes()
        configuration['rootCrl'] = str(Path(configuration['spoolDirectory']) / 'issuer.crl.pem')
        with self.assertRaises(ValueError):
            crl_jobs.publish(configuration, publish_crl, reload_nginx=False)
        self.assertEqual(target.read_bytes(), previous)

    def test_future_root_crl_is_rejected_without_changing_bundle(self):
        configuration = self.configuration()
        self.prepare(configuration)
        target = Path(configuration['targetBundle'])
        previous = target.read_bytes()
        future = target.parent / 'future-root.pem'
        pki.run('ca', '-config', self.root / 'openssl.cnf', '-passin', pki.secret(self.password), '-gencrl',
                '-crl_lastupdate', '20900101000000Z', '-crl_nextupdate', '20900102000000Z', '-out', future)
        configuration['rootCrl'] = str(future)
        with self.assertRaises(ValueError):
            crl_jobs.publish(configuration, publish_crl, reload_nginx=False)
        self.assertEqual(target.read_bytes(), previous)

    def test_wrong_pinned_issuer_does_not_replace_signed_spool(self):
        configuration = self.configuration()
        crl_jobs.generate(configuration, pki, publish_crl)
        spool = Path(configuration['spoolDirectory']) / 'issuer.crl.pem'
        previous = spool.read_bytes()
        configuration['issuerCertificate'] = str(self.root / 'ca.crt.pem')
        with self.assertRaises(ValueError):
            crl_jobs.generate(configuration, pki, publish_crl)
        self.assertEqual(spool.read_bytes(), previous)

    def test_nginx_timeout_restores_previous_bundle(self):
        configuration = self.configuration()
        self.prepare(configuration)
        target = Path(configuration['targetBundle'])
        previous = target.read_bytes()
        crl_jobs.generate(configuration, pki, publish_crl)
        actual = subprocess.run
        commands = []
        def run(command, **kwargs):
            if command[0] == 'nginx':
                commands.append((command, kwargs))
                raise subprocess.TimeoutExpired('nginx', 15)
            return actual(command, **kwargs)
        with mock.patch.object(subprocess, 'run', side_effect=run), self.assertRaisesRegex(ValueError, 'previous CRL restored'):
            crl_jobs.publish(configuration, publish_crl)
        self.assertEqual(target.read_bytes(), previous)
        self.assertEqual(commands[0][0], ['nginx', '-t'])
        self.assertEqual(commands[0][1]['timeout'], 15)

    @unittest.skipUnless(os.name == 'posix' and getattr(os, 'geteuid', lambda: -1)() == 0, 'POSIX ownership test')
    def test_configuration_rejects_readable_private_state_and_writable_public_trust(self):
        configuration = self.configuration()
        crl_jobs.validate_configuration(configuration, os.geteuid(), os.getegid())
        self.password.chmod(0o644)
        try:
            with self.assertRaises(ValueError):
                crl_jobs.validate_configuration(configuration, os.geteuid(), os.getegid())
        finally:
            self.password.chmod(0o600)
        target_dir = Path(configuration['targetBundle']).parent
        target_dir.chmod(0o777)
        try:
            with self.assertRaises(ValueError):
                crl_jobs.validate_configuration(configuration, os.geteuid(), os.getegid())
        finally:
            target_dir.chmod(0o755)

    def test_successful_publish_checks_configuration_then_reloads(self):
        configuration = self.configuration()
        self.prepare(configuration)
        crl_jobs.generate(configuration, pki, publish_crl)
        actual = subprocess.run
        commands = []
        def run(command, **kwargs):
            if command[0] in ('nginx', 'systemctl'):
                commands.append(command)
                return subprocess.CompletedProcess(command, 0)
            return actual(command, **kwargs)
        with mock.patch.object(subprocess, 'run', side_effect=run):
            crl_jobs.publish(configuration, publish_crl)
        self.assertEqual(commands, [['nginx', '-t'], ['systemctl', 'reload', 'nginx']])


if __name__ == '__main__':
    unittest.main()
