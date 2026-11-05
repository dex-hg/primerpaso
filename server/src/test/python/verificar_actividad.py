"""Comprueba bandeja, permisos e historial por HTTP con datos sintéticos."""
import argparse
from datetime import timedelta
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request
from uuid import uuid4

from entorno_contratacion import entorno_contratacion
from verificar_contratacion import (Sesion, ahora, evaluar, oferta_para, postular, preparar_datos, respuesta,
                                    vista_previa)


BANDEJA = '/panel/notificaciones'
HISTORIAL = '/cuenta/historial'
PENDIENTES = '/api/notificaciones/pendientes'


def preparar_actividad(entorno):
    preparar_datos(entorno)
    entorno.sql(f"""
        WITH anterior AS (
            SELECT a.id_application, j.creator_user_id_job FROM applications a
            JOIN jobs j ON j.id_job = a.job_id_application
            WHERE j.title_job = 'Recepción cerrada anterior' AND a.status_application = 'seleccionada'
        ), guardada AS (
            INSERT INTO ofertas_contratacion (id_postulacion, id_emisor, id_envio, condiciones,
                fecha_inicio, vence_en, estado, enviada_en, respondida_en)
            SELECT id_application, creator_user_id_job, '{uuid4()}', 'Acuerdo anterior de prueba',
                current_date + 2, now() + interval '1 day', 'aceptada', now() - interval '1 hour', now() - interval '30 min'
            FROM anterior RETURNING id_postulacion
        )
        INSERT INTO avisos_contratacion (id_postulacion, destinatario, tipo)
        SELECT id_postulacion, 'postulante', 'oferta_enviada' FROM guardada;
        """)


def enviar_formulario(sesion, ruta, esperado=302, datos=None, csrf=True):
    campos = dict(datos or {})
    if csrf:
        campos['tokenCsrf'] = sesion.llamar('/api/sesion/seguridad')['tokenCsrf']
    solicitud = Request(sesion.url + ruta, data=urlencode(campos).encode('utf-8'), method='POST',
                        headers={'Content-Type': 'application/x-www-form-urlencoded'})
    try:
        resultado = sesion.navegador.open(solicitud, timeout=15)
    except HTTPError as excepcion:
        resultado = excepcion
    with resultado:
        assert resultado.status == esperado, (ruta, resultado.status, esperado)
        assert resultado.headers.get('Cache-Control') == 'no-store'
        return urlsplit(resultado.headers.get('Location', '')).path


def pendientes(sesion):
    return sesion.llamar(PENDIENTES)['cantidad']


def verificar_actividad(entorno):
    sin_sesion = Sesion(entorno.url)
    sin_sesion.llamar(PENDIENTES, 401)
    sin_sesion.llamar(HISTORIAL, 302)
    sin_sesion.llamar(BANDEJA, 302)
    empresa, reclutador, ajena, ana, bruno = (
        Sesion(entorno.url, cuenta) for cuenta in ('empresa', 'reclutador', 'ajena', 'ana', 'bruno'))
    assert pendientes(ana) == 1 and pendientes(bruno) == 0
    assert 'Recepción cerrada anterior' in ana.llamar(BANDEJA)
    assert 'Aceptaste una oferta' in ana.llamar(HISTORIAL)
    print('OK · los avisos y las acciones existentes aparecen después de V003', flush=True)

    titulo = 'Vacante de actividad QA'
    datos = {'titulo': titulo, 'descripcion': 'Vacante de verificación.', 'idCarrera': 1, 'habilidades': [1],
             'modalidad': 'remota', 'ciudad': 'Lima', 'codigoPais': 'PE',
             'fechaVencimiento': (ahora() + timedelta(days=4)).isoformat()}
    vacante = empresa.llamar('/api/vacantes/publicar', 201, 'POST', datos)
    id_postulacion = postular(ana, vacante['id'])['postulacion']['id']
    ruta = f'/api/postulaciones/{id_postulacion}'
    historial = ana.llamar(HISTORIAL)
    assert 'Postulaste a una vacante' in historial and titulo in historial and 'Empresa QA' in historial
    assert titulo not in bruno.llamar(HISTORIAL)
    assert 'id="cerrar-sesion"' not in ana.llamar('/panel/postulante')
    assert 'id="cerrar-sesion"' not in ana.llamar(BANDEJA)
    assert 'id="cerrar-sesion"' in historial and 'href="/cuenta/historial"' in ana.llamar('/cuenta')

    evaluar(empresa, ruta, 'seleccionada')
    oferta = empresa.llamar(ruta + '/ofertas', 200, 'POST', oferta_para(empresa, ruta))
    assert pendientes(ana) == 2
    id_aviso = int(entorno.sql(f"""SELECT id_aviso FROM avisos_contratacion
        WHERE id_postulacion = {id_postulacion} AND tipo = 'oferta_enviada'"""))
    leer = f'{BANDEJA}/{id_aviso}/leer'
    bandeja = ana.llamar(BANDEJA + '?estado=sin_leer')
    assert 'Recibiste una oferta' in bandeja and titulo in bandeja and leer in bandeja
    assert titulo not in bruno.llamar(BANDEJA)
    enviar_formulario(bruno, leer, 404)
    enviar_formulario(empresa, leer, 404)
    enviar_formulario(ana, leer, 403, csrf=False)
    assert pendientes(ana) == 2
    assert enviar_formulario(ana, leer, datos={'abrir': 'true'}) == f'/panel/postulante/postulaciones/{id_postulacion}'
    enviar_formulario(ana, leer)
    assert pendientes(Sesion(entorno.url, 'ana')) == 1
    assert titulo not in ana.llamar(BANDEJA + '?estado=sin_leer') and titulo in ana.llamar(BANDEJA)
    print('OK · oferta en la bandeja, lectura persistente, permisos y CSRF', flush=True)

    respuesta(ana, ruta, oferta['id'])
    historial = ana.llamar(HISTORIAL)
    assert 'Aceptaste una oferta' in historial and 'Confirmaron tu contratación' not in historial
    assert pendientes(empresa) == pendientes(reclutador) == 1 and pendientes(ajena) == 0
    id_aviso_empresa = int(entorno.sql(f"""SELECT id_aviso FROM avisos_contratacion
        WHERE id_postulacion = {id_postulacion} AND tipo = 'oferta_aceptada'"""))
    enviar_formulario(ajena, f'{BANDEJA}/{id_aviso_empresa}/leer', 404)
    enviar_formulario(reclutador, f'{BANDEJA}/{id_aviso_empresa}/leer')
    assert pendientes(reclutador) == 0 and pendientes(empresa) == 1
    enviar_formulario(empresa, BANDEJA + '/leer-todas')
    enviar_formulario(ana, BANDEJA + '/leer-todas')
    assert pendientes(ana) == pendientes(empresa) == 0
    assert 'Estás al día' in ana.llamar(BANDEJA + '?estado=sin_leer')

    reclutador.llamar(f"{ruta}/ofertas/{oferta['id']}/confirmar", 200, 'POST', vista_previa(reclutador, ruta))
    assert pendientes(ana) == 1 and 'Confirmaron tu contratación' in ana.llamar(BANDEJA)
    historial = Sesion(entorno.url, 'ana').llamar(HISTORIAL)
    assert historial.count('Confirmaron tu contratación') == 1
    assert 'Aceptaste una oferta' in historial and titulo in historial
    assert f'/panel/postulante/postulaciones/{id_postulacion}' in historial
    assert 'Confirmaron tu contratación' not in bruno.llamar(HISTORIAL)
    assert 'Enviaste una oferta' in empresa.llamar(HISTORIAL)
    assert 'Confirmaste una contratación' in reclutador.llamar(HISTORIAL)
    assert 'Confirmaste una contratación' not in empresa.llamar(HISTORIAL)
    assert titulo not in ajena.llamar(HISTORIAL)
    print('OK · lectura independiente por reclutador e historial atribuido a cada actor', flush=True)

    entorno.sql(f"""INSERT INTO avisos_contratacion (id_postulacion, destinatario, tipo)
        SELECT {id_postulacion}, 'postulante', 'oferta_enviada' FROM generate_series(1, 13)""")
    assert 'pagina=1' in ana.llamar(BANDEJA + '?estado=sin_leer')
    assert 'Anterior' in ana.llamar(BANDEJA + '?estado=sin_leer&pagina=1')
    enviar_formulario(ana, BANDEJA + '/leer-todas', datos={'estado': 'sin_leer'})
    assert pendientes(ana) == 0
    ana.llamar('/api/sesion', 204, 'DELETE')
    ana.llamar(PENDIENTES, 401)
    ana.llamar(HISTORIAL, 302)
    print('OK · paginación, lectura masiva y cierre desde la cuenta', flush=True)


def principal():
    argumentos = argparse.ArgumentParser()
    argumentos.add_argument('--esquema-anterior', type=Path, help='SQL base de V002, previo a V003.')
    opciones = argumentos.parse_args()
    with entorno_contratacion() as entorno:
        entorno.preparar_esquema(opciones.esquema_anterior, preparar_actividad)
        with entorno.servidor():
            verificar_actividad(entorno)


if __name__ == '__main__':
    principal()

