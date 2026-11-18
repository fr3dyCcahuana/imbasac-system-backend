"""Fixed-config CRL maintenance jobs, generation and privileged publication separated."""
import datetime as dt
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile

CONFIGURATION = Path('/etc/imbasac/crl-publication.json')
LIBRARY = Path('/usr/local/lib/imbasac-ca')
KEYS = {'issuerDirectory', 'passphraseFile', 'spoolDirectory', 'rootCertificate',
        'rootCrl', 'issuerCertificate', 'targetBundle'}


def validate_configuration(configuration, ca_uid, ca_gid):
    if not isinstance(configuration, dict) or set(configuration) != KEYS:
        raise ValueError('Invalid fixed CRL configuration')
    for key, raw in configuration.items():
        path = Path(raw)
        if not path.is_absolute() or path.is_symlink():
            raise ValueError('Invalid CRL path')
        if key in ('issuerDirectory', 'spoolDirectory', 'passphraseFile'):
            if path.stat().st_uid != ca_uid or path.stat().st_mode & 0o077:
                raise ValueError('CA state must remain private')
        else:
            candidate = path.parent if key == 'targetBundle' else path
            if candidate.stat().st_uid != 0 or candidate.stat().st_mode & 0o022:
                raise ValueError('Public trust material must be root controlled')
    if not Path(configuration['issuerDirectory']).is_dir() or not Path(configuration['spoolDirectory']).is_dir():
        raise ValueError('CA directories missing')
    return configuration


def load_configuration(action):
    import pwd
    import grp
    ca = pwd.getpwnam('imbasac-ca')
    group = grp.getgrnam('imbasac-ca')
    expected_uid = ca.pw_uid if action == 'generate' else 0
    if os.geteuid() != expected_uid:
        raise ValueError('Wrong CRL job identity')
    for path in (CONFIGURATION, LIBRARY / 'crl_jobs.py', LIBRARY / 'pki.py', LIBRARY / 'publish_crl.py'):
        if path.is_symlink() or path.stat().st_uid != 0 or path.stat().st_mode & 0o022:
            raise ValueError('Untrusted installed CRL helper')
        for parent in path.parents:
            if parent.is_symlink() or parent.stat().st_uid != 0 or parent.stat().st_mode & 0o022:
                raise ValueError('Untrusted installed CRL directory')
    if CONFIGURATION.stat().st_gid != group.gr_gid or CONFIGURATION.stat().st_mode & 0o007:
        raise ValueError('CRL configuration must be root:imbasac-ca 0640')
    configuration = json.loads(CONFIGURATION.read_text('utf-8'))
    return validate_configuration(configuration, ca.pw_uid, group.gr_gid)


def installed_module(name):
    specification = importlib.util.spec_from_file_location(name, LIBRARY / (name + '.py'))
    module = importlib.util.module_from_spec(specification)
    sys.modules[name] = module
    specification.loader.exec_module(module)
    return module


def generate(configuration, pki, publication):
    spool = Path(configuration['spoolDirectory'])
    if spool.is_symlink() or not spool.is_dir():
        raise ValueError('Spool is not a private directory')
    with pki.locked(spool), tempfile.TemporaryDirectory(prefix='.crl-generate-', dir=spool) as temporary:
        output = Path(temporary) / 'issuer.crl.pem'
        pki.generate_crl(configuration['issuerDirectory'], output, configuration['passphraseFile'])
        publication.validate(output, configuration['issuerCertificate'])
        with output.open('r+b') as stream:
            os.fsync(stream.fileno())
        os.chmod(output, 0o600)
        os.replace(output, spool / 'issuer.crl.pem')
        if os.name == 'posix':
            descriptor = os.open(spool, os.O_RDONLY)
            try:
                os.fsync(descriptor)
            finally:
                os.close(descriptor)


def publish(configuration, publication, reload_nginx=True):
    publication.publish(configuration['rootCrl'], configuration['rootCertificate'],
                        Path(configuration['spoolDirectory']) / 'issuer.crl.pem', configuration['issuerCertificate'],
                        configuration['targetBundle'], reload_nginx=reload_nginx)


def main(action):
    os.umask(0o077)
    prefix = 'CRL_REFRESH' if action == 'generate' else 'CRL_PUBLISH'
    try:
        if len(sys.argv) != 1 or action not in ('generate', 'publish'):
            raise ValueError('No CRL job arguments allowed')
        os.environ.clear()
        os.environ.update({'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'OPENSSL': '/usr/bin/openssl', 'LC_ALL': 'C'})
        configuration = load_configuration(action)
        pki = installed_module('pki')
        publication = installed_module('publish_crl')
        if action == 'generate':
            generate(configuration, pki, publication)
        else:
            publish(configuration, publication)
        print(prefix + '_OK')
        return 0
    except Exception:
        print(prefix + '_FAILED', file=sys.stderr)
        return 2
