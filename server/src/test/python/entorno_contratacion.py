"""Levanta un clúster y un JAR desechables en la interfaz local para las pruebas."""
from contextlib import contextmanager
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
from tempfile import TemporaryDirectory
from time import monotonic, sleep
from urllib.error import URLError
from urllib.request import urlopen


SERVIDOR = Path(__file__).resolve().parents[3]
ESQUEMA = SERVIDOR.parent / 'db' / 'primerpaso.sql'
MIGRACIONES = tuple(ESQUEMA.parent / 'migraciones' / nombre for nombre in (
    'V002__contratacion.sql', 'V003__lecturas_notificaciones.sql'))
JAR = SERVIDOR / 'target' / 'primerpaso-0.0.1-SNAPSHOT.jar'


def puerto_local():
    with socket.socket() as conexion:
        conexion.bind(('127.0.0.1', 0))
        return conexion.getsockname()[1]


class EntornoContratacion:
    def __init__(self, temporal):
        self.temporal = temporal
        self.datos = temporal / 'pgdata'
        self.registro_pg = temporal / 'postgresql.log'
        self.registro_web = temporal / 'servidor.log'
        self.puerto = puerto_local()
        self.puerto_web = puerto_local()
        self.url = f'http://127.0.0.1:{self.puerto_web}'
        self.usuario = 'qa_contratacion'
        self.clave = secrets.token_urlsafe(32)
        self.entorno = dict(os.environ, PGPASSWORD=self.clave, PGCLIENTENCODING='UTF8', PGCONNECT_TIMEOUT='5')
        self.postgres = Path(os.environ.get('PRIMERPASO_PG_BIN', 'C:/Program Files/PostgreSQL/18/bin'))

    def herramienta(self, nombre, *argumentos, consulta=None, tiempo=45):
        ejecutable = shutil.which(nombre) or str(self.postgres / (nombre + '.exe' if os.name == 'nt' else nombre))
        # En Windows, el servidor iniciado por pg_ctl puede heredar sus manejadores de salida.
        salida = subprocess.DEVNULL if nombre == 'pg_ctl' else subprocess.PIPE
        resultado = subprocess.run([ejecutable, *map(str, argumentos)], input=consulta, stdout=salida, stderr=salida,
                                   encoding='utf-8', errors='replace', env=self.entorno, timeout=tiempo)
        if resultado.returncode:
            detalle = resultado.stderr or (self.registro_pg.read_text(encoding='utf-8', errors='replace')[-4000:]
                                           if self.registro_pg.is_file() else '')
            raise RuntimeError(f'Falló {nombre} en el entorno de prueba.\n{detalle}')
        return (resultado.stdout or '').strip()

    def iniciar_postgres(self):
        archivo_clave = self.temporal / 'clave'
        archivo_clave.write_text(self.clave + '\n', encoding='utf-8')
        try:
            self.herramienta('initdb', '-D', self.datos, '-U', self.usuario, '--auth=scram-sha-256',
                             '--pwfile', archivo_clave, '--encoding=UTF8', '--locale=C', tiempo=90)
        finally:
            archivo_clave.unlink()
        self.herramienta('pg_ctl', '-D', self.datos, '-l', self.registro_pg, '-o',
                         f'-h 127.0.0.1 -p {self.puerto}', '-w', 'start')

    def detener_postgres(self):
        if (self.datos / 'postmaster.pid').exists():
            self.herramienta('pg_ctl', '-D', self.datos, '-m', 'fast', '-w', 'stop')

    def sql(self, consulta, base='qa_contratacion'):
        return self.herramienta('psql', '-X', '-q', '-tA', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1',
                                '-p', self.puerto, '-U', self.usuario, '-d', base, consulta=consulta)

    def preparar_esquema(self, anterior, preparar_datos):
        self.sql('CREATE DATABASE qa_contratacion;', base='postgres')
        if anterior:
            self.sql(ESQUEMA.read_text(encoding='utf-8-sig'), base='postgres')
            self.sql(anterior.read_text(encoding='utf-8-sig'))
        else:
            self.sql(ESQUEMA.read_text(encoding='utf-8-sig'))
        preparar_datos(self)
        for archivo in MIGRACIONES:
            migracion = archivo.read_text(encoding='utf-8')
            self.sql(migracion)
            self.sql(migracion)

    @contextmanager
    def servidor(self):
        directorio_java = os.environ.get('JAVA_HOME')
        java = str(Path(directorio_java) / 'bin' / 'java') if directorio_java else 'java'
        variables = dict(os.environ,
                         PRIMERPASO_BD_URL=f'jdbc:postgresql://127.0.0.1:{self.puerto}/qa_contratacion',
                         PRIMERPASO_BD_USUARIO=self.usuario, PRIMERPASO_BD_CLAVE=self.clave)
        with self.registro_web.open('wb') as registro:
            proceso = subprocess.Popen(
                [java, '-jar', str(JAR), f'--server.port={self.puerto_web}', '--server.address=127.0.0.1'],
                cwd=self.temporal, env=variables, stdout=registro, stderr=subprocess.STDOUT)
            try:
                limite = monotonic() + 40
                while monotonic() < limite and proceso.poll() is None:
                    try:
                        with urlopen(self.url + '/iniciar-sesion', timeout=2) as respuesta:
                            if respuesta.status == 200:
                                break
                    except (URLError, OSError):
                        sleep(0.2)
                else:
                    raise RuntimeError('El servidor de verificación no llegó a responder.')
                yield
            except Exception:
                print(self.registro_web.read_text(encoding='utf-8', errors='replace')[-9000:])
                print(self.registro_pg.read_text(encoding='utf-8', errors='replace')[-5000:])
                raise
            finally:
                if proceso.poll() is None:
                    proceso.terminate()
                    try:
                        proceso.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        proceso.kill()
                        proceso.wait(timeout=10)


@contextmanager
def entorno_contratacion():
    if not JAR.is_file():
        raise RuntimeError('Empaqueta el servidor antes de ejecutar la prueba de contratación.')
    destino = JAR.parent.resolve()
    with TemporaryDirectory(dir=destino, prefix='qa-contratacion-') as directorio:
        temporal = Path(directorio).resolve()
        if temporal.parent != destino:
            raise RuntimeError('El directorio de verificación debe estar dentro de target.')
        entorno = EntornoContratacion(temporal)
        try:
            entorno.iniciar_postgres()
            yield entorno
        finally:
            entorno.detener_postgres()
