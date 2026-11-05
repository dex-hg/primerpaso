"""Verifica contratación y concurrencia por HTTP sobre PostgreSQL desechable."""
import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import hashlib
import json
from pathlib import Path
import secrets
from threading import Barrier
from uuid import uuid4

from entorno_contratacion import entorno_contratacion
from soporte_chat import cliente, solicitar


CLAVE = secrets.token_urlsafe(24)
POSTULANTES = ('ana', 'bruno', 'carla', 'diego', 'eva', 'fabio')
CUENTAS = (*POSTULANTES, 'empresa', 'reclutador', 'ajena')


def correo(cuenta):
    return f'{cuenta}.contratacion@example.test'


def preparar_datos(entorno):
    sal = secrets.token_bytes(16)
    hash_clave = hashlib.pbkdf2_hmac('sha256', CLAVE.encode(), sal, 600000)
    codificada = '{pbkdf2-sha256}600000$' + '$'.join(base64.b64encode(valor).decode() for valor in (sal, hash_clave))
    usuarios = ','.join(f"('{correo(cuenta)}','{codificada}',now(),'qa-contratacion')" for cuenta in CUENTAS)
    candidatos = ','.join(f"'{correo(cuenta)}'" for cuenta in POSTULANTES)
    entorno.sql(f"""
        INSERT INTO careers (name_career) VALUES ('Sistemas QA');
        INSERT INTO skills (name_skill) VALUES ('Java QA');
        INSERT INTO users (email_user, password_hash_user, terms_accepted_at_user, terms_version_user) VALUES {usuarios};
        INSERT INTO companies (trade_name_company, tax_id_company, industry_company, city_company)
        VALUES ('Empresa QA', '20000000001', 'Tecnología', 'Lima'), ('Empresa ajena QA', '20000000002', 'Tecnología', 'Lima');
        INSERT INTO company_members (company_id_company_member, user_id_company_member, role_company_member)
        SELECT e.id_company, u.id_user, CASE WHEN u.email_user = '{correo('reclutador')}' THEN 'reclutador' ELSE 'administrador' END
        FROM companies e CROSS JOIN users u WHERE
            (e.trade_name_company = 'Empresa QA' AND u.email_user IN ('{correo('empresa')}', '{correo('reclutador')}'))
            OR (e.trade_name_company = 'Empresa ajena QA' AND u.email_user = '{correo('ajena')}');
        INSERT INTO candidates (id_candidate, first_name_candidate, last_name_candidate,
            institution_candidate, career_id_candidate, academic_status_candidate, current_term_candidate)
        SELECT id_user, split_part(email_user, '.', 1), 'Prueba', 'Institución QA', (SELECT min(id_career) FROM careers),
            'estudiante', 6 FROM users WHERE email_user IN ({candidatos});
        INSERT INTO jobs (company_id_job, creator_user_id_job, career_id_job, title_job, description_job,
            work_mode_job, status_job, created_at_job, published_at_job, expires_at_job)
        SELECT e.id_company, u.id_user, (SELECT min(id_career) FROM careers), 'Recepción cerrada anterior',
            'Convocatoria de prueba con datos anteriores', 'remota', 'cerrada', now() - interval '3 days',
            now() - interval '2 days', now() - interval '1 day'
        FROM companies e CROSS JOIN users u WHERE e.trade_name_company = 'Empresa QA' AND u.email_user = '{correo('empresa')}';
        INSERT INTO applications (job_id_application, candidate_id_application, status_application)
        SELECT j.id_job, c.id_candidate, CASE WHEN c.first_name_candidate = 'ana' THEN 'seleccionada' ELSE 'retirada' END
        FROM jobs j CROSS JOIN candidates c WHERE j.title_job = 'Recepción cerrada anterior'
            AND c.first_name_candidate IN ('ana', 'bruno');
        """)


class Sesion:
    def __init__(self, url, cuenta=None):
        self.url = url
        self.navegador = cliente()
        if cuenta:
            self.llamar('/api/sesion', 200, 'POST', {'correo': correo(cuenta), 'contrasena': CLAVE,
                        'tipoCuenta': 'postulante' if cuenta in POSTULANTES else 'empresa'})

    def llamar(self, ruta, esperado=200, metodo='GET', datos=None, csrf=True):
        token = self.llamar('/api/sesion/seguridad')['tokenCsrf'] if metodo != 'GET' and csrf else None
        codigo, cabeceras, cuerpo = solicitar(self.navegador, ruta, metodo, datos, token, base=self.url)
        permitidos = esperado if isinstance(esperado, tuple) else (esperado,)
        assert codigo in permitidos, f'{metodo} {ruta}: {codigo}, esperado {esperado}. {cuerpo[:400]}'
        assert cabeceras.get('Cache-Control') == 'no-store'
        contenido = json.loads(cuerpo) if cuerpo and ruta.startswith('/api/') else cuerpo
        return (codigo, contenido) if isinstance(esperado, tuple) else contenido


def ahora():
    return datetime.now(timezone.utc).replace(microsecond=0)


def postular(sesion, vacante, esperado=201):
    return sesion.llamar('/api/postulaciones', esperado, 'POST', {'idVacante': vacante,
                         'cartaPresentacion': 'Me interesa la convocatoria de prueba.'})


def evaluar(empresa, ruta, estado, esperado=200):
    version = empresa.llamar(ruta)['postulacion']['version']
    return empresa.llamar(ruta + '/estado', esperado, 'PUT', {'estado': estado, 'version': version})


def oferta_para(empresa, ruta):
    return {'idEnvio': str(uuid4()), 'version': empresa.llamar(ruta)['postulacion']['version'],
            'condiciones': 'Inicio coordinado.\n<script>qa</script>',
            'fechaInicio': (ahora() + timedelta(days=3)).date().isoformat(),
            'venceEn': (ahora() + timedelta(days=2)).isoformat()}


def respuesta(postulante, ruta, oferta, aceptada=True, esperado=200):
    return postulante.llamar(f'{ruta}/ofertas/{oferta}/respuesta', esperado, 'POST', {'aceptada': aceptada})


def vista_previa(empresa, ruta):
    progreso = empresa.llamar(ruta + '/ofertas')['progreso']
    return {'versionVacante': progreso['versionVacante'],
            'postulacionesAFinalizar': progreso['postulacionesActivas'] - 1
            if progreso['cubiertas'] + 1 == progreso['plazas'] else 0}


def enviar_chat(sesion, ruta, esperado=201):
    return sesion.llamar('/api/chat' + ruta.removeprefix('/api') + '/mensajes', esperado, 'POST',
                         {'idEnvio': str(uuid4()), 'contenido': 'Coordinación de prueba.'})


def en_paralelo(primera, segunda):
    barrera = Barrier(2)

    def ejecutar(operacion):
        barrera.wait(timeout=10)
        return operacion()

    with ThreadPoolExecutor(max_workers=2) as grupo:
        trabajos = [grupo.submit(ejecutar, operacion) for operacion in (primera, segunda)]
        return [trabajo.result() for trabajo in trabajos]


def verificar_contratacion(entorno, sesiones):
    empresa, reclutador, ajena = (sesiones[cuenta] for cuenta in ('empresa', 'reclutador', 'ajena'))
    datos = {'titulo': 'Practicante QA', 'descripcion': 'Vacante creada por HTTP.', 'idCarrera': 1, 'habilidades': [1],
             'modalidad': 'remota', 'ciudad': 'Lima', 'codigoPais': 'PE',
             'fechaVencimiento': (ahora() + timedelta(days=4)).isoformat()}
    empresa.llamar('/api/vacantes/publicar', 400, 'POST', {**datos, 'plazas': 0})
    vacante = empresa.llamar('/api/vacantes/publicar', 201, 'POST', {**datos, 'plazas': 2})
    otra = empresa.llamar('/api/vacantes/publicar', 201, 'POST', {**datos, 'titulo': 'Otra convocatoria QA'})
    assert vacante['plazas'] == 2 and otra['plazas'] == 1
    ruta_vacante = f"/api/vacantes/{vacante['id']}"
    empresa.llamar(ruta_vacante, 409, 'PUT', {**datos, 'plazas': 3, 'version': vacante['version']})
    aislada = postular(sesiones['ana'], otra['id'])['postulacion']['id']
    rutas = {cuenta: f"/api/postulaciones/{postular(sesiones[cuenta], vacante['id'])['postulacion']['id']}"
             for cuenta in POSTULANTES[:-1]}
    ana, bruno, carla = (rutas[cuenta] for cuenta in ('ana', 'bruno', 'carla'))
    for ruta in (ana, bruno, carla):
        evaluar(empresa, ruta, 'seleccionada')
        enviar_chat(empresa, ruta)
    evaluar(empresa, rutas['diego'], 'descartada')
    retiro = {'version': sesiones['eva'].llamar(rutas['eva'])['postulacion']['version']}
    for _ in range(2):
        sesiones['eva'].llamar(rutas['eva'] + '/retirar', 204, 'POST', retiro)

    datos_oferta = oferta_para(empresa, ana)
    Sesion(entorno.url).llamar(ana + '/ofertas', 401)
    ajena.llamar(ana + '/ofertas', 404)
    empresa.llamar(ana + '/ofertas', 403, 'POST', datos_oferta, csrf=False)
    oferta = empresa.llamar(ana + '/ofertas', 200, 'POST', datos_oferta)
    assert oferta == empresa.llamar(ana + '/ofertas', 200, 'POST', datos_oferta)
    empresa.llamar(ana + '/ofertas', 409, 'POST', {**datos_oferta, 'condiciones': 'Otro contenido'})
    evaluar(empresa, ana, 'en_revision', 409)
    respuesta(sesiones['bruno'], ana, oferta['id'], esperado=404)
    respuesta(empresa, ana, oferta['id'], esperado=403)
    confirmar_ana = f"{ana}/ofertas/{oferta['id']}/confirmar"
    empresa.llamar(confirmar_ana, 409, 'POST', vista_previa(empresa, ana))
    html = sesiones['ana'].llamar(ana.replace('/api', '/panel/postulante', 1))
    assert 'Aceptar oferta' in html and '&lt;script&gt;qa&lt;/script&gt;' in html and '<script>qa</script>' not in html
    assert sesiones['ana'].llamar(ana + '/ofertas')['progreso'] is None
    assert respuesta(sesiones['ana'], ana, oferta['id'])['estado'] == 'aceptada'
    assert reclutador.llamar(confirmar_ana, 200, 'POST', vista_previa(reclutador, ana))['estado'] == 'contratada'
    parcial = empresa.llamar(ruta_vacante)
    assert (parcial['plazasCubiertas'], parcial['estado']) == (1, 'publicada')
    assert empresa.llamar(bruno)['postulacion']['estado'] == 'seleccionada'
    enviar_chat(sesiones['ana'], ana)
    print('OK · oferta, privacidad, primera contratación y chat de ingreso', flush=True)

    oferta_bruno = empresa.llamar(bruno + '/ofertas', 200, 'POST', oferta_para(empresa, bruno))
    assert respuesta(sesiones['bruno'], bruno, oferta_bruno['id'], False)['estado'] == 'rechazada'
    vencida = empresa.llamar(bruno + '/ofertas', 200, 'POST', oferta_para(empresa, bruno))
    entorno.sql(f"""UPDATE ofertas_contratacion SET enviada_en = now() - interval '2 hours',
        vence_en = now() - interval '1 hour' WHERE id_oferta = {vencida['id']}""")
    respuesta(sesiones['bruno'], bruno, vencida['id'], esperado=409)
    assert empresa.llamar(bruno + '/ofertas')['ofertas'][0]['estado'] == 'vencida'
    propuestas = [oferta_para(sesion, ruta) for sesion, ruta in ((empresa, bruno), (reclutador, carla))]
    reservas = en_paralelo(
        lambda: empresa.llamar(bruno + '/ofertas', (200, 409), 'POST', propuestas[0]),
        lambda: reclutador.llamar(carla + '/ofertas', (200, 409), 'POST', propuestas[1]))
    assert sorted(codigo for codigo, _ in reservas) == [200, 409]
    ganado = 'bruno' if reservas[0][0] == 200 else 'carla'
    restante = 'carla' if ganado == 'bruno' else 'bruno'
    ruta = rutas[ganado]
    id_oferta = next(oferta['id'] for codigo, oferta in reservas if codigo == 200)
    respuesta(sesiones[ganado], ruta, id_oferta)
    confirmar = f'{ruta}/ofertas/{id_oferta}/confirmar'
    datos_confirmacion = vista_previa(empresa, ruta)
    assert datos_confirmacion['postulacionesAFinalizar'] == 1
    html = empresa.llamar(ruta.replace('/api', '/panel/empresa', 1))
    assert 'Registrar contratación' in html and 'la última plaza' in html
    empresa.llamar(confirmar, 409, 'POST', {**datos_confirmacion, 'postulacionesAFinalizar': 99})
    cierre, ingreso = en_paralelo(
        lambda: empresa.llamar(confirmar, (200, 409), 'POST', datos_confirmacion),
        lambda: postular(sesiones['fabio'], vacante['id'], (201, 404)))
    assert (cierre[0], ingreso[0]) in ((200, 404), (409, 201))
    if ingreso[0] == 201:
        id_fabio = ingreso[1]['postulacion']['id']
        rutas['fabio'] = f'/api/postulaciones/{id_fabio}'
        datos_confirmacion = vista_previa(empresa, ruta)
        empresa.llamar(confirmar, 200, 'POST', datos_confirmacion)
        assert empresa.llamar(rutas['fabio'])['postulacion']['estado'] == 'finalizada'
    assert empresa.llamar(confirmar, 200, 'POST', datos_confirmacion) == reclutador.llamar(
        confirmar, 200, 'POST', datos_confirmacion)

    final = empresa.llamar(ruta_vacante)
    assert (final['plazasCubiertas'], final['estado'], final['motivoFinProceso']) == (2, 'cerrada', 'vacante_cubierta')
    assert empresa.llamar(rutas['diego'])['postulacion']['estado'] == 'descartada'
    assert empresa.llamar(rutas['eva'])['postulacion']['estado'] == 'retirada'
    assert empresa.llamar(f'/api/postulaciones/{aislada}')['postulacion']['estado'] == 'enviada'
    sesiones[restante].llamar(f"/panel/postulante/vacantes/{vacante['id']}", 404)
    finalizada = sesiones[restante].llamar(rutas[restante])['postulacion']
    assert (finalizada['estado'], finalizada['motivoFinalizacion']) == ('finalizada', 'vacante_cubierta')
    evaluar(empresa, rutas[restante], 'seleccionada', 409)
    assert 'cubrió las plazas' in sesiones[restante].llamar('/panel/notificaciones')
    assert sesiones[restante].llamar('/api/chat' + rutas[restante].removeprefix('/api'))['puedeEnviar'] is False
    enviar_chat(sesiones[restante], rutas[restante], 409)
    assert entorno.sql(f"""SELECT count(*) FROM avisos_contratacion
        WHERE id_postulacion = {int(ruta.rsplit('/', 1)[1])} AND tipo = 'contratacion_confirmada'""") == '1'
    print('OK · rechazo, vencimiento, carrera por la plaza, cierre, avisos e idempotencia', flush=True)


def verificar_cancelacion(entorno, sesiones):
    empresa, postulante = sesiones['empresa'], sesiones['ana']
    id_vacante = entorno.sql("SELECT id_job FROM jobs WHERE title_job = 'Recepción cerrada anterior'")
    id_postulacion = entorno.sql(f"""SELECT id_application FROM applications WHERE job_id_application = {int(id_vacante)}
        AND status_application = 'seleccionada'""")
    ruta = f'/api/postulaciones/{id_postulacion}'
    ruta_vacante = f'/api/vacantes/{id_vacante}'
    vacante = empresa.llamar(ruta_vacante)
    assert vacante['estado'] == 'cerrada' and vacante['plazas'] == 1 and vacante['motivoFinProceso'] is None
    assert 'Proceso por resolver' in empresa.llamar('/panel/empresa/vacantes')
    assert 'Cancelar proceso' in empresa.llamar(f'/panel/empresa/vacantes/{id_vacante}/editar')
    oferta = empresa.llamar(ruta + '/ofertas', 200, 'POST', oferta_para(empresa, ruta))
    respuesta(postulante, ruta, oferta['id'])
    cancelacion = {'versionVacante': vacante['version'], 'postulacionesAFinalizar': 1, 'motivo': 'Motivo interno QA'}
    empresa.llamar(ruta_vacante + '/cancelar-proceso', 409, 'POST', cancelacion)
    empresa.llamar(f"{ruta}/ofertas/{oferta['id']}/retirar", 200, 'POST', {})
    nueva = empresa.llamar(ruta + '/ofertas', 200, 'POST', oferta_para(empresa, ruta))
    for _ in range(2):
        empresa.llamar(ruta_vacante + '/cancelar-proceso', 204, 'POST', cancelacion)
    ficha = postulante.llamar(ruta + '/ofertas')
    assert ficha['ofertas'][0]['id'] == nueva['id'] and ficha['ofertas'][0]['estado'] == 'retirada'
    html = postulante.llamar('/panel/notificaciones')
    assert 'canceló el proceso' in html and cancelacion['motivo'] not in html
    assert postulante.llamar(ruta)['postulacion']['motivoFinalizacion'] == 'proceso_cancelado'
    assert entorno.sql(f"""SELECT count(*) FROM applications WHERE job_id_application = {int(id_vacante)}
        AND status_application = 'retirada'""") == '1'
    print('OK · datos anteriores, recepción cerrada, retiro de oferta y cancelación privada', flush=True)


def verificar_retiro_concurrente(entorno, sesiones):
    empresa, postulante = sesiones['empresa'], sesiones['ana']
    id_vacante = int(entorno.sql("SELECT id_job FROM jobs WHERE title_job = 'Otra convocatoria QA'"))
    id_postulacion = int(entorno.sql(f'SELECT id_application FROM applications WHERE job_id_application = {id_vacante}'))
    ruta = f'/api/postulaciones/{id_postulacion}'
    ruta_vacante = f'/api/vacantes/{id_vacante}'
    empresa.llamar(ruta_vacante + '/cerrar', 200, 'POST', {'version': empresa.llamar(ruta_vacante)['version']})
    postular(sesiones['carla'], id_vacante, 404)
    evaluar(empresa, ruta, 'seleccionada')
    oferta = empresa.llamar(ruta + '/ofertas', 200, 'POST', oferta_para(empresa, ruta))
    respuesta(postulante, ruta, oferta['id'])
    vista = vista_previa(empresa, ruta)
    version = postulante.llamar(ruta)['postulacion']['version']
    confirmar = f"{ruta}/ofertas/{oferta['id']}/confirmar"
    contratacion, retiro = en_paralelo(
        lambda: empresa.llamar(confirmar, (200, 409), 'POST', vista),
        lambda: postulante.llamar(ruta + '/retirar', (204, 409), 'POST', {'version': version}))
    assert (contratacion[0], retiro[0]) in ((200, 409), (409, 204))
    assert postulante.llamar(ruta)['postulacion']['estado'] == ('contratada' if contratacion[0] == 200 else 'retirada')
    assert empresa.llamar(ruta + '/ofertas')['progreso']['reservadas'] == 0
    print('OK · cierre de recepción y concurrencia entre retiro y contratación', flush=True)


def principal():
    argumentos = argparse.ArgumentParser()
    argumentos.add_argument('--esquema-anterior', type=Path, help='SQL base anterior para verificar las migraciones.')
    opciones = argumentos.parse_args()
    with entorno_contratacion() as entorno:
        entorno.preparar_esquema(opciones.esquema_anterior, preparar_datos)
        with entorno.servidor():
            sesiones = {cuenta: Sesion(entorno.url, cuenta) for cuenta in CUENTAS}
            verificar_contratacion(entorno, sesiones)
            verificar_cancelacion(entorno, sesiones)
            verificar_retiro_concurrente(entorno, sesiones)


if __name__ == '__main__':
    principal()

