"""Opt-in, root-owned installation rehearsal ONLY inside a disposable container."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import uuid

import pki


@unittest.skipUnless(os.environ.get('IMBASAC_SIGNER_INSTALL_TEST') == '1' and Path('/.dockerenv').exists(),
                     'Disposable Linux installation test not requested')
class InstalledSignerTest(unittest.TestCase):
    def test_sudo_bridge_permissions_fixed_environment_and_arguments(self):
        import pwd
        self.assertEqual(os.geteuid(), 0)
        subprocess.run(['useradd', '--system', '--user-group', '--shell', '/usr/sbin/nologin', 'posapp'],
                       check=True, capture_output=True)
        subprocess.run(['sh', str(Path(__file__).with_name('install_signer.sh'))], check=True, capture_output=True)
        ca = pwd.getpwnam('imbasac-ca')
        password = Path('/var/lib/imbasac-ca/private/issuer.pass')
        pki.write_new(password, 'isolated-installation-test-password\n')
        password.chmod(0o600)
        with tempfile.TemporaryDirectory(prefix='imbasac-offline-install-fixture-') as temporary:
            temporary = Path(temporary)
            root = pki.initialize(temporary / 'root', 'root', password, offline=True)
            issuer = pki.initialize('/var/lib/imbasac-ca/device-issuer', 'issuer', password)
            signed = temporary / 'issuer.cert.pem'
            pki.sign_issuer(root, issuer / 'issuer.csr.pem', signed, password, offline=True)
            pki.install_issuer(issuer, signed, root / 'ca.crt.pem')
            for path in [issuer, *issuer.rglob('*'), password]:
                os.chown(path, ca.pw_uid, ca.pw_gid)
            csr = temporary / 'device.csr.pem'
            pki.run('req', '-new', '-newkey', 'ec', '-pkeyopt', 'ec_paramgen_curve:prime256v1', '-nodes', '-sha256',
                    '-keyout', temporary / 'device.key', '-out', csr, '-subj', '/CN=software-test-only')
            body = json.dumps({'requestId': str(uuid.uuid4()), 'deviceId': str(uuid.uuid4()), 'csrPem': csr.read_text()}).encode()
            prefix = ['runuser', '-u', 'posapp', '--', 'sudo', '-n', '-u', 'imbasac-ca']
            command = prefix + ['/usr/local/lib/imbasac-ca/sign-request']
            environment = dict(os.environ, OPENSSL='/caller/controlled/openssl', PYTHONPATH='/caller/controlled/python')
            result = subprocess.run(command, input=body, capture_output=True, timeout=20, env=environment)
            self.assertEqual(result.returncode, 0, result.stderr.decode())
            public = json.loads(result.stdout)
            self.assertEqual(set(public), {'certificatePem'})
            self.assertNotIn('PRIVATE KEY', public['certificatePem'])
            repeated = subprocess.run(command, input=body, capture_output=True, timeout=20)
            self.assertEqual(repeated.returncode, 0)
            self.assertEqual(repeated.stdout, result.stdout)
            revocation = json.dumps({'operation': 'revoke', 'requestId': str(uuid.uuid4()),
                                     'deviceId': json.loads(body)['deviceId'], 'certificatePem': public['certificatePem']}).encode()
            revoked = subprocess.run(command, input=revocation, capture_output=True, timeout=20)
            self.assertEqual(revoked.returncode, 0, revoked.stderr.decode())
            self.assertEqual(json.loads(revoked.stdout), {'revoked': True})
            repeated = subprocess.run(command, input=revocation, capture_output=True, timeout=20)
            self.assertEqual(repeated.stdout, revoked.stdout)
            self.assertTrue(any(line.startswith('R\t') for line in (issuer / 'index.txt').read_text().splitlines()))
            denied = subprocess.run(['runuser', '-u', 'posapp', '--', 'cat', str(issuer / 'private/ca.key.pem')], capture_output=True)
            self.assertNotEqual(denied.returncode, 0)
            denied = subprocess.run(prefix + ['/bin/sh', '-c', 'id'], capture_output=True)
            self.assertNotEqual(denied.returncode, 0)
            denied = subprocess.run(command + ['--config', '/caller/controlled'], input=body, capture_output=True)
            self.assertNotEqual(denied.returncode, 0)
            rejected = subprocess.run(command, input=b'x' * 20001, capture_output=True, timeout=20)
            self.assertEqual(rejected.returncode, 2)
            self.assertEqual(rejected.stdout, b'')
            self.assertEqual(rejected.stderr, b'SIGN_REQUEST_INVALID\n')


if __name__ == '__main__':
    unittest.main()
