"""Conexión local, cuentas sintéticas y restauración para verificar el chat real."""
import base64
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import build_opener, HTTPCookieProcessor, HTTPRedirectHandler, Request

RAIZ = Path(__file__).resolve().parents[3]
ESTADO = RAIZ / 'target/estado_chat_prueba.json'
CLAVE_PRUEBA = 'Chat-Prueba-2026!'
BASE = os.environ.get('PRIMERPASO_URL_PRUEBA', 'http://localhost:8082')
if urlparse(BASE).hostname not in ('localhost', '127.0.0.1', '::1'):
    raise RuntimeError('El servidor de verificación debe estar en localhost.')


def ejecutar_sql(consulta):
    propiedades = {}
    for linea in (RAIZ / '.env').read_text(encoding='utf-8-sig').splitlines():
        if linea.strip() and not linea.lstrip().startswith(('#', '!')) and '=' in linea:
            nombre, valor = linea.split('=', 1)
            propiedades[nombre.strip()] = re.sub(r'\\(u[0-9a-fA-F]{4}|.)', lambda coincidencia:
                chr(int(coincidencia[1][1:], 16)) if coincidencia[1].startswith('u') else
                {'t': '\t', 'n': '\n', 'r': '\r', 'f': '\f'}.get(coincidencia[1], coincidencia[1]), valor.lstrip())
    conexion = urlparse(propiedades.get('PRIMERPASO_BD_URL', 'jdbc:postgresql://localhost:5432/PrimerPaso')[5:])
    if conexion.hostname not in ('localhost', '127.0.0.1', '::1'):
        raise RuntimeError('Esta verificación crea fixtures únicamente en PostgreSQL local.')
    ejecutable = shutil.which('psql') or 'C:/Program Files/PostgreSQL/18/bin/psql.exe'
    entorno = dict(os.environ, PGPASSWORD=propiedades.get('PRIMERPASO_BD_CLAVE', ''),
        PGCLIENTENCODING='UTF8', PGCONNECT_TIMEOUT='5')
    resultado = subprocess.run([ejecutable, '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1',
        '-h', conexion.hostname, '-p', str(conexion.port or 5432), '-U', propiedades.get('PRIMERPASO_BD_USUARIO', 'postgres'),
        '-d', conexion.path[1:]], input=consulta.encode('utf-8'), capture_output=True, env=entorno)
    if resultado.returncode:
        raise RuntimeError('Falló la operación PostgreSQL de verificación; no se muestran consultas ni credenciales.')
    return resultado.stdout.decode('utf-8').strip()


def huellas():
    tablas = json.loads(ejecutar_sql('SELECT json_agg(tablename ORDER BY tablename) FROM pg_tables WHERE schemaname=current_schema()'))
    return {tabla: ejecutar_sql('SELECT md5(COALESCE(jsonb_agg(to_jsonb(f) ORDER BY to_jsonb(f)::text)::text,\'[]\')) FROM "'
        + tabla.replace('"', '""') + '" f') for tabla in tablas}


def esquema():
    return ejecutar_sql("""SELECT json_build_object(
        'columnas',(SELECT json_agg(f ORDER BY table_name,ordinal_position) FROM
            (SELECT table_name,column_name,data_type,is_nullable,column_default,ordinal_position
             FROM information_schema.columns WHERE table_schema=current_schema()) f),
        'restricciones',(SELECT json_agg(f ORDER BY tabla,nombre) FROM
            (SELECT conrelid::regclass::text AS tabla,conname AS nombre,pg_get_constraintdef(oid) AS definicion
             FROM pg_constraint WHERE connamespace=current_schema()::regnamespace) f),
        'indices',(SELECT json_agg(f ORDER BY tablename,indexname) FROM
            (SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname=current_schema()) f));""")


class SinRedireccion(HTTPRedirectHandler):
    def redirect_request(self, solicitud, archivo, codigo, mensaje, cabeceras, destino):
        return None


def cliente():
    return build_opener(HTTPCookieProcessor(http.cookiejar.CookieJar()), SinRedireccion())


def solicitar(navegador, ruta, metodo='GET', datos=None, token=None):
    cabeceras = {'Accept': 'application/json'}
    if datos is not None:
        cabeceras['Content-Type'] = 'application/json'
    if token:
        cabeceras['X-CSRF-Token'] = token
    solicitud = Request(BASE + ruta, method=metodo, headers=cabeceras,
        data=json.dumps(datos).encode('utf-8') if datos is not None else None)
    try:
        respuesta = navegador.open(solicitud, timeout=20)
    except HTTPError as excepcion:
        respuesta = excepcion
    return respuesta.status, respuesta.headers, respuesta.read().decode('utf-8')


def comprobar(navegador, ruta, esperado=200, metodo='GET', datos=None, csrf=True):
    token = None
    if metodo != 'GET' and csrf:
        codigo, _, cuerpo = solicitar(navegador, '/api/sesion/seguridad')
        assert codigo == 200
        token = json.loads(cuerpo)['tokenCsrf']
    codigo, cabeceras, cuerpo = solicitar(navegador, ruta, metodo, datos, token)
    assert codigo == esperado, f'{metodo} {ruta}: {codigo}, esperado {esperado}'
    assert cabeceras.get('Cache-Control') == 'no-store'
    return json.loads(cuerpo) if ruta.startswith('/api/') else cuerpo


def iniciar_sesion(correo, tipo):
    navegador = cliente()
    comprobar(navegador, '/api/sesion', 200, 'POST', {'correo': correo, 'contrasena': CLAVE_PRUEBA, 'tipoCuenta': tipo})
    return navegador


def preparar():
    assert not ESTADO.exists(), 'Hay fixtures del chat pendientes de limpiar.'
    etiqueta = uuid.uuid4().hex[:16]
    originales, esquema_original = huellas(), esquema()
    ids_originales = json.loads(ejecutar_sql("SELECT COALESCE(json_agg(id_user),'[]') FROM users"))
    cuentas = {tipo: f'qa.chat.{tipo}.{etiqueta}@example.test' for tipo in ['postulante', 'segundo', 'empresa', 'reclutador', 'otra']}
    sal = os.urandom(16)
    clave = hashlib.pbkdf2_hmac('sha256', CLAVE_PRUEBA.encode(), sal, 600000)
    codificada = '{pbkdf2-sha256}600000$' + base64.b64encode(sal).decode() + '$' + base64.b64encode(clave).decode()
    filas = ','.join(f"('{correo}','{codificada}',now(),'qa-chat-v1')" for correo in cuentas.values())
    ejecutar_sql(f"""BEGIN;
        INSERT INTO careers(name_career) VALUES ('QA Chat {etiqueta}');
        INSERT INTO skills(name_skill) VALUES ('QA Chat Java {etiqueta}');
        INSERT INTO users(email_user,password_hash_user,terms_accepted_at_user,terms_version_user) VALUES {filas};
        INSERT INTO candidates(id_candidate,first_name_candidate,last_name_candidate,institution_candidate,
            career_id_candidate,academic_status_candidate,current_term_candidate)
        SELECT id_user,'Ana','Prueba QA','Institución QA',
            (SELECT id_career FROM careers WHERE name_career='QA Chat {etiqueta}'),'estudiante',6
            FROM users WHERE email_user IN ('{cuentas['postulante']}','{cuentas['segundo']}');
        INSERT INTO companies(trade_name_company,country_code_company,tax_id_company,industry_company,city_company)
        VALUES ('Empresa QA Chat {etiqueta}','US',upper('QA-CHAT-A-{etiqueta}'),'Tecnología','Lima'),
            ('Otra QA Chat {etiqueta}','US',upper('QA-CHAT-B-{etiqueta}'),'Tecnología','Lima');
        INSERT INTO company_members(company_id_company_member,user_id_company_member,role_company_member,
            first_name_company_member,last_name_company_member)
        SELECT e.id_company,u.id_user,CASE WHEN u.email_user='{cuentas['reclutador']}' THEN 'reclutador' ELSE 'administrador' END,
            CASE WHEN u.email_user='{cuentas['reclutador']}' THEN 'Elena' ELSE 'Luis' END,'Prueba QA'
            FROM companies e CROSS JOIN users u WHERE
            (e.tax_id_company=upper('QA-CHAT-A-{etiqueta}') AND u.email_user IN ('{cuentas['empresa']}','{cuentas['reclutador']}'))
            OR (e.tax_id_company=upper('QA-CHAT-B-{etiqueta}') AND u.email_user='{cuentas['otra']}'); COMMIT;""")
    usuarios = json.loads(ejecutar_sql("SELECT json_object_agg(email_user,id_user) FROM users WHERE email_user IN (" +
        ','.join("'" + correo + "'" for correo in cuentas.values()) + ')'))
    empresas = json.loads(ejecutar_sql(f"SELECT json_object_agg(tax_id_company,id_company) FROM companies WHERE tax_id_company IN (upper('QA-CHAT-A-{etiqueta}'),upper('QA-CHAT-B-{etiqueta}'))"))
    datos = dict(etiqueta=etiqueta, cuentas=cuentas, usuarios=usuarios, empresas=list(empresas.values()), originales=originales,
        esquema=esquema_original, ids_originales=ids_originales,
        carrera=int(ejecutar_sql(f"SELECT id_career FROM careers WHERE name_career='QA Chat {etiqueta}'")),
        habilidad=int(ejecutar_sql(f"SELECT id_skill FROM skills WHERE name_skill='QA Chat Java {etiqueta}'")))
    ESTADO.write_text(json.dumps(datos), encoding='utf-8')
    print(json.dumps({'fixtures': 'creados', 'cuentas_sinteticas': 5, 'etiqueta': etiqueta}))


def limpiar():
    datos = json.loads(ESTADO.read_text(encoding='utf-8'))
    etiqueta = datos['etiqueta']
    assert re.fullmatch('[a-f0-9]{16}', etiqueta)
    identificadores = set(datos['usuarios'].values())
    assert not identificadores.intersection(datos['ids_originales'])
    correos = ','.join("'" + correo + "'" for correo in datos['cuentas'].values())
    comprobados = json.loads(ejecutar_sql(f"SELECT json_object_agg(email_user,id_user) FROM users WHERE email_user IN ({correos}) AND terms_version_user='qa-chat-v1'"))
    assert comprobados == datos['usuarios'], 'No se prueba la procedencia de los fixtures; no se elimina nada.'
    usuarios = ','.join(map(str, identificadores))
    empresas = ','.join(map(str, datos['empresas']))
    ejecutar_sql(f"""BEGIN;
        DELETE FROM mensajes_chat m USING applications a,jobs j WHERE m.id_postulacion=a.id_application
            AND a.job_id_application=j.id_job AND j.company_id_job IN ({empresas}) AND a.candidate_id_application IN ({usuarios});
        DELETE FROM applications a USING jobs j WHERE a.job_id_application=j.id_job
            AND j.company_id_job IN ({empresas}) AND a.candidate_id_application IN ({usuarios});
        DELETE FROM job_skills s USING jobs j WHERE s.job_id_job_skill=j.id_job AND j.company_id_job IN ({empresas});
        DELETE FROM jobs WHERE company_id_job IN ({empresas});
        DELETE FROM candidates WHERE id_candidate IN ({usuarios});
        DELETE FROM company_members WHERE company_id_company_member IN ({empresas}) AND user_id_company_member IN ({usuarios});
        DELETE FROM companies WHERE id_company IN ({empresas}) AND tax_id_company IN (upper('QA-CHAT-A-{etiqueta}'),upper('QA-CHAT-B-{etiqueta}'));
        DELETE FROM users WHERE id_user IN ({usuarios}) AND terms_version_user='qa-chat-v1';
        DELETE FROM skills WHERE id_skill={datos['habilidad']} AND name_skill='QA Chat Java {etiqueta}';
        DELETE FROM careers WHERE id_career={datos['carrera']} AND name_career='QA Chat {etiqueta}'; COMMIT;""")
    assert huellas() == datos['originales'], 'La comparación de datos originales falló.'
    assert esquema() == datos['esquema'], 'La comparación del esquema falló.'
    informe = {'limpieza': 'correcta', 'tablas': len(datos['originales']), 'datos_originales': 'idénticos', 'esquema': 'idéntico'}
    (RAIZ / 'target/limpieza_chat_bd.json').write_text(json.dumps(informe), encoding='utf-8')
    ESTADO.unlink()
    print(json.dumps(informe, ensure_ascii=False))
