#!/bin/sh
# One-time reviewed upgrade; the operator stops/starts the ERP service.
set -eu
PATH=/usr/sbin:/usr/bin:/sbin:/bin
export PATH
umask 077
bundle=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
/usr/bin/python3 - "$bundle" <<'PYTHON'
import ast
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
from zipfile import ZipFile

if os.geteuid() != 0 or socket.gethostname().split('.')[0] != 'srv1283651':
    raise SystemExit('Este instalador corresponde a la consola root del VPS de IMBASAC.')
identity = subprocess.check_output(
    ['ssh-keygen', '-lf', '/etc/ssh/ssh_host_ed25519_key.pub', '-E', 'sha256'], text=True).split()[1]
if identity != 'SHA256:gGZo19aRvh/BBorI8kNNwbyyx0q1F+5y6hjXNJaPapI':
    raise SystemExit('La identidad del VPS no coincide. No se ha aplicado el cambio.')
state = subprocess.check_output(
    ['systemctl', 'show', 'pos-backend.service', '--property=ActiveState', '--value'], text=True).strip()
if state not in ('inactive', 'failed'):
    raise SystemExit('Deten primero pos-backend.service y vuelve a ejecutar este instalador.')
try:
    with ZipFile('/opt/pos-backend/app.jar') as application:
        repository = application.read('BOOT-INF/classes/com/paulfernandosr/possystembackend/accesscontrol/infrastructure/PostgresAccessControlRepository.class')
        policy = application.read('BOOT-INF/classes/com/paulfernandosr/possystembackend/deviceaccess/application/DeviceAccessPolicyService.class')
        if b'block_days' not in repository or b'strike_day' not in repository or b'findForUser' not in policy:
            raise ValueError()
except Exception:
    raise SystemExit('Sube primero el JAR actualizado a /opt/pos-backend/app.jar. La base sigue sin cambios.')

bundle = Path(__import__('sys').argv[1])
manifest = json.loads((bundle / 'manifest-servidor.json').read_text('utf-8-sig'))
names = ('20261112_daily_access_strikes.sql', 'sign-request', 'pki.py')
if set(manifest) != set(names):
    raise SystemExit('El manifiesto del paquete necesita revision.')
for name in names:
    source = bundle / name
    if source.is_symlink() or not source.is_file():
        raise SystemExit('Falta un archivo del paquete: ' + name)
    if hashlib.sha256(source.read_bytes()).hexdigest() != manifest[name]:
        raise SystemExit('La transferencia no coincide con el paquete validado: ' + name)
    if name != names[0]:
        ast.parse(source.read_text('utf-8'), filename=name)

targets = {name: Path('/usr/local/lib/imbasac-ca') / name for name in ('sign-request', 'pki.py')}
for target in targets.values():
    if target.is_symlink() or not target.is_file() or target.stat().st_uid != 0 or target.stat().st_mode & 0o022:
        raise SystemExit('La instalacion existente de la CA requiere revision. No se ha aplicado el cambio.')
    for parent in target.parents:
        if parent.is_symlink() or parent.stat().st_uid != 0 or parent.stat().st_mode & 0o022:
            raise SystemExit('Los permisos del directorio de la CA requieren revision.')

stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
backup = Path('/root') / ('imbasac-cambio-diario-' + stamp)
backup.mkdir(mode=0o700)
dump = backup / 'imbasac_prod.dump'
postgres = ['runuser', '-u', 'postgres', '--']
connection = ['-h', '/var/run/postgresql', '-p', '5432', '-U', 'postgres', '-d', 'imbasac_prod', '-w']
with dump.open('xb') as output:
    dump.chmod(0o600)
    subprocess.run(postgres + ['pg_dump'] + connection + ['-Fc'], stdout=output, check=True)
    output.flush()
    os.fsync(output.fileno())
subprocess.run(['pg_restore', '--list', str(dump)], stdout=subprocess.DEVNULL, check=True)
for name, target in targets.items():
    saved = backup / name
    saved.write_bytes(target.read_bytes())
    saved.chmod(0o600)

subprocess.run(postgres + ['psql'] + connection + ['-X', '-v', 'ON_ERROR_STOP=1'],
               input=(bundle / names[0]).read_bytes(), check=True)
for name, target in targets.items():
    descriptor, temporary = tempfile.mkstemp(prefix='.' + name + '-', dir=target.parent)
    try:
        with os.fdopen(descriptor, 'wb') as output:
            output.write((bundle / name).read_bytes())
            output.flush()
            os.fsync(output.fileno())
        os.chown(temporary, 0, 0)
        os.chmod(temporary, 0o755 if name == 'sign-request' else 0o644)
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    if hashlib.sha256(target.read_bytes()).hexdigest() != manifest[name]:
        raise SystemExit('La verificacion del firmante no coincide. Conserva el backend detenido y comunica el resultado.')

duration = subprocess.check_output(postgres + ['psql'] + connection + ['-X', '-At', '-v', 'ON_ERROR_STOP=1',
    '-c', 'BEGIN READ ONLY; SELECT block_days FROM security_access_control_settings WHERE singleton=TRUE; COMMIT;'], text=True)
print('Cambio aplicado: conteo diario de strikes y emision de nuevos certificados por 365 dias.')
print('Respaldo privado: ' + str(backup))
print('Duracion configurada en dias (valores entre BEGIN y COMMIT): ' + duration.strip().replace('\n', ' '))
print('No se han modificado claves de CA, certificados existentes, usuarios ni autorizaciones.')
print('Sube el JAR y el frontend actualizados y reinicia pos-backend.service cuando terminen las transferencias.')
PYTHON
