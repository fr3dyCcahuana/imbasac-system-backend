import importlib.machinery
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import uuid
import datetime as dt

import pki

loader = importlib.machinery.SourceFileLoader('sign_request_test_module', str(Path(__file__).with_name('sign-request')))
spec = importlib.util.spec_from_loader(loader.name, loader)
signer = importlib.util.module_from_spec(spec)
loader.exec_module(signer)


class SigningBridgeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='imbasac-signer-test-')
        cls.base = Path(cls.temp.name)
        cls.password = cls.base / 'passphrase'
        pki.write_new(cls.password, 'isolated-signer-test-password\n')
        root = pki.initialize(cls.base / 'root', 'root', cls.password, offline=True)
        cls.root = root
        cls.issuer = pki.initialize(cls.base / 'issuer', 'issuer', cls.password)
        pki.sign_issuer(root, cls.issuer / 'issuer.csr.pem', cls.base / 'issuer.pem', cls.password, offline=True)
        pki.install_issuer(cls.issuer, cls.base / 'issuer.pem', root / 'ca.crt.pem')
        cls.outputs = cls.base / 'requests'
        cls.outputs.mkdir(mode=0o700)
        cls.config = {'issuerDirectory': str(cls.issuer), 'passphraseFile': str(cls.password), 'outputDirectory': str(cls.outputs)}
        csr = cls.base / 'csr.pem'
        pki.run('req', '-new', '-newkey', 'ec', '-pkeyopt', 'ec_paramgen_curve:prime256v1', '-nodes', '-sha256',
                '-keyout', cls.base / 'device.key', '-out', csr, '-subj', '/CN=ignored')
        cls.csr = csr.read_text()

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def body(self):
        return {'requestId': str(uuid.uuid4()), 'deviceId': str(uuid.uuid4()), 'csrPem': self.csr}

    def invoke(self, body):
        return signer.process_request(json.dumps(body).encode(), self.config, pki)

    def test_certificate_is_stable_for_retried_request(self):
        body = self.body()
        response = self.invoke(body)
        self.assertEqual(set(response), {'certificatePem'})
        self.assertIn('-----BEGIN CERTIFICATE-----', response['certificatePem'])
        self.assertEqual(response, self.invoke(body))
        self.assertNotIn('PRIVATE KEY', response['certificatePem'])

    def test_new_certificate_has_one_year_validity_and_retries_preserve_its_expiry(self):
        body = self.body()
        response = self.invoke(body)
        certificate = self.outputs / body['requestId'] / 'certificate.pem'
        dates = pki.run('x509', '-in', certificate, '-noout', '-dates').decode('ascii').splitlines()
        parsed = [dt.datetime.strptime(line.split('=', 1)[1], '%b %d %H:%M:%S %Y %Z') for line in dates]
        self.assertEqual(parsed[1] - parsed[0], dt.timedelta(days=365))
        self.assertEqual(self.invoke(body), response)

    def test_conflicting_request_id_is_rejected(self):
        body = self.body()
        self.invoke(body)
        body['deviceId'] = str(uuid.uuid4())
        with self.assertRaisesRegex(signer.SigningError, '^SIGN_REQUEST_CONFLICT$'):
            self.invoke(body)

    def test_oversized_unknown_duplicate_fields_or_paths_are_rejected(self):
        cases = [b'x' * 20001, b'{"requestId":"a","requestId":"b"}', b'[]']
        body = self.body()
        body['outputDirectory'] = '/caller-controlled'
        cases.append(json.dumps(body).encode())
        for raw in cases:
            with self.subTest(raw_size=len(raw)), self.assertRaisesRegex(signer.SigningError, '^SIGN_REQUEST_INVALID$'):
                signer.process_request(raw, self.config, pki)

    def test_concurrent_wrapper_is_rejected_without_signing(self):
        with pki.locked(self.outputs), self.assertRaisesRegex(signer.SigningError, '^SIGN_BUSY$'):
            self.invoke(self.body())

    def test_uncertain_ca_failure_is_not_reissued(self):
        body = self.body()
        with mock.patch.object(pki, 'issue_device', side_effect=ValueError('synthetic failure')) as issue:
            with self.assertRaisesRegex(signer.SigningError, '^SIGN_FAILED$'):
                self.invoke(body)
            with self.assertRaisesRegex(signer.SigningError, '^SIGN_STATE_UNCERTAIN$'):
                self.invoke(body)
            self.assertEqual(issue.call_count, 1)

    def test_lost_response_after_actual_signing_recovers_original_certificate(self):
        body = self.body()
        real_issue = pki.issue_device
        def signed_then_lost(*args, **kwargs):
            real_issue(*args, **kwargs)
            raise ValueError('synthetic response failure after certificate written')
        with mock.patch.object(pki, 'issue_device', side_effect=signed_then_lost):
            with self.assertRaisesRegex(signer.SigningError, '^SIGN_FAILED$'):
                self.invoke(body)
        response = self.invoke(body)
        self.assertEqual(response, self.invoke(body))

    def test_main_rejects_arguments_without_loading_private_config(self):
        with mock.patch.object(signer.sys, 'argv', ['sign-request', '--config', 'untrusted']), \
             mock.patch.object(signer, 'installed_configuration') as load, \
             mock.patch.object(signer.sys, 'stderr') as stderr:
            self.assertEqual(signer.main(), 2)
            load.assert_not_called()
            stderr.write.assert_called_once_with('SIGN_REQUEST_INVALID\n')

    def revocation(self):
        body = self.body()
        public = self.invoke(body)['certificatePem']
        return {'operation': 'revoke', 'requestId': str(uuid.uuid4()), 'deviceId': body['deviceId'], 'certificatePem': public}

    def test_revoke_is_idempotent_and_openssl_index_really_records_R(self):
        body = self.revocation()
        self.assertEqual(self.invoke(body), {'revoked': True})
        self.assertEqual(self.invoke(body), {'revoked': True})
        certificate = self.outputs / body['requestId'] / 'certificate.pem'
        serial = pki.run('x509', '-in', certificate, '-serial', '-noout').decode().strip().split('=', 1)[1]
        entries = [line.split('\t') for line in (self.issuer / 'index.txt').read_text().splitlines()]
        self.assertEqual([row[0] for row in entries if int(row[3], 16) == int(serial, 16)], ['R'])

    def test_revoke_rejects_wrong_device_or_private_key_pem(self):
        body = self.revocation()
        body['deviceId'] = str(uuid.uuid4())
        with self.assertRaisesRegex(signer.SigningError, '^SIGN_REQUEST_INVALID$'):
            self.invoke(body)
        body = self.revocation()
        body['certificatePem'] = '-----BEGIN PRIVATE KEY-----\nSYNTHETIC-NOT-A-KEY\n-----END PRIVATE KEY-----'
        with self.assertRaisesRegex(signer.SigningError, '^SIGN_REQUEST_INVALID$'):
            self.invoke(body)

    def test_revoke_rejects_sibling_issuer_even_with_same_trusted_root(self):
        foreign = pki.initialize(self.base / 'foreign-issuer', 'issuer', self.password)
        pki.sign_issuer(self.root, foreign / 'issuer.csr.pem', self.base / 'foreign.pem', self.password, offline=True)
        pki.install_issuer(foreign, self.base / 'foreign.pem', self.root / 'ca.crt.pem')
        device = str(uuid.uuid4())
        certificate = self.base / 'foreign-device.pem'
        pki.issue_device(foreign, self.base / 'csr.pem', device, certificate, self.password)
        body = {'operation': 'revoke', 'requestId': str(uuid.uuid4()), 'deviceId': device, 'certificatePem': certificate.read_text()}
        with self.assertRaisesRegex(signer.SigningError, '^SIGN_REQUEST_INVALID$'):
            self.invoke(body)

    def test_revoke_recovers_lost_response_from_actual_CA_index(self):
        body = self.revocation()
        actual = pki.revoke
        def revoked_then_lost(*args, **kwargs):
            actual(*args, **kwargs)
            raise ValueError('synthetic failure after index update')
        with mock.patch.object(pki, 'revoke', side_effect=revoked_then_lost):
            with self.assertRaisesRegex(signer.SigningError, '^SIGN_FAILED$'):
                self.invoke(body)
        self.assertEqual(self.invoke(body), {'revoked': True})


if __name__ == '__main__':
    unittest.main()
