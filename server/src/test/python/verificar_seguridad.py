"""Comprueba los controles de Render por HTTP y PostgreSQL desechable."""
import base64
from concurrent.futures import ThreadPoolExecutor
from http.client import HTTPConnection
import json
import secrets

from entorno_contratacion import ESQUEMA, entorno_contratacion
from verificar_contratacion import CLAVE, correo, preparar_datos


SESION = '/api/sesion'
TOKEN = SESION + '/seguridad'
POSTULANTES = '/api/registro/postulantes'
EMPRESAS = '/api/registro/empresas'
ORIGEN = 'https://primerpaso.example.test'


def solicitar(entorno, ruta, metodo='GET', datos=None, *, ip='198.51.100.1', cabeceras=None, fragmentado=False):
    conexion = HTTPConnection('127.0.0.1', entorno.puerto_web, timeout=10)
    encabezados = {'Host': ORIGEN.removeprefix('https://'), 'CF-Connecting-IP': ip}
    encabezados.update(cabeceras or {})
    if datos is not None:
        encabezados['Content-Type'] = 'application/json'
    cuerpo = json.dumps(datos).encode() if isinstance(datos, dict) else datos
    try:
        conexion.request(metodo, ruta, cuerpo, encabezados, encode_chunked=fragmentado)
        respuesta = conexion.getresponse()
        return respuesta.status, respuesta.headers, respuesta.read().decode('utf-8')
    finally:
        conexion.close()


def datos_acceso(correo_cuenta, contrasena=CLAVE, tipo='postulante'):
    return {'correo': correo_cuenta, 'contrasena': contrasena, 'tipoCuenta': tipo}


def acceder(entorno, datos, ip):
    codigo, cabeceras, cuerpo = solicitar(entorno, TOKEN, ip=ip)
    assert codigo == 200, (codigo, cuerpo)
    cookie = cabeceras['Set-Cookie']
    assert {'secure', 'httponly', 'samesite=strict'} <= set(cookie.lower().split('; '))
    return solicitar(entorno, SESION, 'POST', datos, ip=ip, cabeceras={
        'Cookie': cookie.split(';', 1)[0], 'X-CSRF-Token': json.loads(cuerpo)['tokenCsrf'], 'Origin': ORIGEN,
    })


def comprobar_limite(resultado, espera_minima=1):
    codigo, cabeceras, cuerpo = resultado
    assert codigo == 429, (codigo, cuerpo)
    assert int(cabeceras['Retry-After']) >= espera_minima
    assert cabeceras['Cache-Control'] == 'no-store' and json.loads(cuerpo)['mensaje']


def comprobar_perfil(entorno):
    codigo, cabeceras, cuerpo = solicitar(entorno, '/salud')
    assert (codigo, cabeceras['Cache-Control'], cabeceras.get('Set-Cookie'), cuerpo) == (200, 'no-store', None, '')
    codigo, cabeceras, cuerpo = solicitar(entorno, '/iniciar-sesion')
    assert codigo == 200 and 'src="/js/sesion.js"' in cuerpo
    politica = cabeceras['Content-Security-Policy']
    assert "frame-ancestors 'none'" in politica and 'unsafe-inline' not in politica
    assert cabeceras['Strict-Transport-Security'] == 'max-age=31536000'
    assert cabeceras['X-Content-Type-Options'] == 'nosniff' and cabeceras['X-Frame-Options'] == 'DENY'
    ubicacion = solicitar(entorno, '/panel')[1]['Location']
    assert ubicacion == '/iniciar-sesion', ubicacion
    assert solicitar(entorno, '/registro')[2].count('class="role-card"') == 0
    assert solicitar(entorno, '/registro-empresa')[1]['Location'] == '/registro'
    assert solicitar(entorno, '/registro-postulante')[1]['Location'] == '/registro'
    preflight = solicitar(entorno, SESION, 'OPTIONS', cabeceras={
        'Origin': 'http://localhost:5500', 'Access-Control-Request-Method': 'POST',
        'Access-Control-Request-Headers': 'content-type,x-csrf-token',
    })
    # El navegador rechaza el preflight si no recibe autorización para su origen.
    assert preflight[1].get('Access-Control-Allow-Origin') is None
    assert solicitar(entorno, POSTULANTES, 'POST', datos_registro())[0] == 403
    datos_empresa = {
        'nombres': 'Julia', 'apellidos': 'Prueba', 'correo': 'empresa-seguridad@example.test',
        'contrasena': CLAVE, 'aceptaTerminos': True, 'nombreComercial': 'Empresa de prueba',
        'codigoPais': 'PE', 'identificacionFiscal': '20601234567', 'sector': 'tecnologia',
        'ciudad': 'Lima', 'telefono': '987654321',
    }
    assert solicitar(entorno, EMPRESAS, 'POST', datos_empresa)[0] == 403
    assert solicitar(entorno, SESION, 'POST', datos_acceso(correo('ana')))[0] == 403
    print('OK · disponibilidad, HTTPS, CORS, CSRF y registro cerrado', flush=True)


def comprobar_bloqueo(entorno):
    def fallar(indice):
        cuenta = correo('ana').upper() if indice % 2 else correo('ana')
        return acceder(entorno, datos_acceso(cuenta, 'Contraseña incorrecta de prueba',
                       'empresa' if indice % 2 else 'postulante'), f'198.51.100.{indice + 10}')

    with ThreadPoolExecutor(max_workers=3) as grupo:
        resultados = list(grupo.map(fallar, range(8)))
    rechazados = [resultado for resultado in resultados if resultado[0] != 401]
    assert len(rechazados) == 3, [resultado[0] for resultado in resultados]
    for resultado in rechazados:
        comprobar_limite(resultado, 30)
    desconocida = acceder(entorno, datos_acceso('desconocida@example.test'), '198.51.100.30')
    assert desconocida[0] == 401
    assert desconocida[2] == next(cuerpo for codigo, _, cuerpo in resultados if codigo == 401)
    assert entorno.sql('SELECT max(intentos) FROM limites_inicio_sesion;') == '5'
    print('OK · máximo de cinco fallos concurrentes por correo, aunque cambie la IP o el rol', flush=True)


def comprobar_reinicio(entorno):
    correcta = datos_acceso(correo('ana'))
    comprobar_limite(acceder(entorno, correcta, '203.0.113.1'), 30)
    entorno.sql("UPDATE limites_inicio_sesion SET vence_en = statement_timestamp() - interval '1 second';")
    for _ in range(6):
        assert acceder(entorno, correcta, '203.0.113.1')[0] == 200
    assert entorno.sql('SELECT max(intentos) FROM limites_inicio_sesion WHERE vence_en > now();') == '0'
    print('OK · bloqueo conservado al reiniciar, vencimiento y liberación tras acceso válido', flush=True)


def comprobar_abuso_ip(entorno):
    # Variar X-Forwarded-For y el sufijo IPv6 no cambia el cupo del prefijo real.
    for indice in range(1, 26):
        resultado = solicitar(entorno, SESION, 'POST', {}, ip=f'2001:db8:42::{indice}',
                              cabeceras={'X-Forwarded-For': f'192.0.2.{indice}'})
        assert resultado[0] == 400
    comprobar_limite(solicitar(entorno, SESION, 'POST', {}, ip='2001:db8:42::80'), 30)
    assert solicitar(entorno, SESION, 'POST', {}, ip='2001:db8:43::80')[0] == 400
    codigo, cabeceras, cuerpo = solicitar(entorno, TOKEN, ip='203.0.113.8')
    assert codigo == 200
    cookie = cabeceras['Set-Cookie'].split(';', 1)[0]
    for _ in range(59):
        assert solicitar(entorno, TOKEN, 'HEAD', ip='203.0.113.8')[0] == 200
    comprobar_limite(solicitar(entorno, TOKEN, ip='203.0.113.8'))
    reutilizada = solicitar(entorno, TOKEN, ip='203.0.113.8', cabeceras={'Cookie': cookie})
    assert reutilizada[0] == 200 and reutilizada[2] == cuerpo and reutilizada[1].get('Set-Cookie') is None
    print('OK · límite por red, resistencia a X-Forwarded-For y protección de sesiones anónimas', flush=True)


def datos_registro():
    return {
        'nombres': 'Ana', 'apellidos': 'Prueba', 'correo': 'alta.seguridad@example.test',
        'contrasena': CLAVE, 'aceptaTerminos': True, 'institucion': 'Institución de prueba',
        'carrera': 'Sistemas QA', 'condicionAcademica': 'estudiante', 'cicloActual': 6,
        'habilidades': [], 'intereses': [], 'aceptaComunicaciones': False,
    }


def comprobar_registro_y_tamano(entorno):
    datos = datos_registro()
    html = solicitar(entorno, '/registro')[2]
    assert 'href="/registro-postulante"' in html and 'href="/registro-empresa"' not in html
    corto = dict(datos, contrasena='x' * 14)
    for _ in range(6):
        codigo, _, cuerpo = solicitar(entorno, POSTULANTES, 'POST', corto, ip='203.0.113.5')
        assert codigo == 400 and 'contrasena' in json.loads(cuerpo)['errores']
    comprobar_limite(solicitar(entorno, EMPRESAS, 'POST', {}, ip='203.0.113.5'), 30)
    codigo, _, cuerpo = solicitar(entorno, POSTULANTES, 'POST', datos, ip='203.0.113.6')
    assert codigo == 201, (codigo, cuerpo)
    assert acceder(entorno, datos_acceso(datos['correo']), '203.0.113.6')[0] == 200

    carga = b'x' * (64 * 1024 + 1)
    for cuerpo, fragmentado in ((carga, False), ((carga[:40000], carga[40000:]), True)):
        codigo, cabeceras, cuerpo = solicitar(entorno, SESION, 'POST', cuerpo, fragmentado=fragmentado)
        assert codigo == 413 and cabeceras['Cache-Control'] == 'no-store', (codigo, cuerpo)
    print('OK · alta controlada, contraseña mínima y límite de cuerpo con y sin Content-Length', flush=True)


def comprobar_migracion_supabase(entorno):
    entorno.sql("""
        CREATE ROLE anon;
        CREATE ROLE authenticated;
        CREATE ROLE service_role BYPASSRLS;
        CREATE ROLE authenticator NOINHERIT;
        GRANT anon, authenticated, service_role TO authenticator;
        CREATE ROLE instalador CREATEROLE;
        CREATE DATABASE qa_supabase OWNER instalador;
        """, base='postgres')

    def ejecutar_archivo(nombre):
        return entorno.herramienta(
            'psql', '-XqtA', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-p', entorno.puerto,
            '-U', entorno.usuario, '-d', 'qa_supabase', '-c', 'SET ROLE instalador',
            '-f', ESQUEMA.parent / 'supabase' / nombre,
        )

    ejecutar_archivo('01_inicializar.sql')
    entorno.sql("""
        SET ROLE instalador;
        ALTER ROLE primerpaso_app LOGIN;
        INSERT INTO primerpaso.careers(name_career) VALUES ('Dato anterior a V004');
        """, base='qa_supabase')
    assert ejecutar_archivo('02_verificar.sql') == '20'

    # Reproduce un esquema anterior a V004 dentro de esta base desechable.
    entorno.sql('DROP TABLE primerpaso.limites_inicio_sesion;', base='qa_supabase')
    for _ in range(2):
        ejecutar_archivo('04_limites_inicio_sesion.sql')
    assert ejecutar_archivo('02_verificar.sql') == '20'
    assert entorno.sql("""
        SET ROLE primerpaso_app;
        SELECT name_career FROM primerpaso.careers;
        INSERT INTO primerpaso.limites_inicio_sesion VALUES (decode(repeat('ab', 32), 'hex'), 1, now());
        UPDATE primerpaso.limites_inicio_sesion SET intentos = 2;
        SELECT intentos FROM primerpaso.limites_inicio_sesion;
        DELETE FROM primerpaso.limites_inicio_sesion;
        """, base='qa_supabase').splitlines() == ['Dato anterior a V004', '2']
    print('OK · esquema privado nuevo y migrado, RLS, permisos y datos previos', flush=True)


if __name__ == '__main__':
    with entorno_contratacion() as entorno:
        entorno.preparar_esquema(None, preparar_datos)
        variables = {
            'SPRING_PROFILES_ACTIVE': 'render',
            'PRIMERPASO_SEGURIDAD_CLAVE': base64.b64encode(secrets.token_bytes(32)).decode(),
            'PRIMERPASO_REGISTRO_POSTULANTES': 'false',
            'PRIMERPASO_REGISTRO_EMPRESAS': 'false',
        }
        with entorno.servidor(variables_extra=variables):
            comprobar_perfil(entorno)
            comprobar_bloqueo(entorno)
        variables['PRIMERPASO_REGISTRO_POSTULANTES'] = 'true'
        with entorno.servidor(variables_extra=variables):
            comprobar_reinicio(entorno)
            comprobar_abuso_ip(entorno)
            comprobar_registro_y_tamano(entorno)
        comprobar_migracion_supabase(entorno)
