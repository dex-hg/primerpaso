"""Pruebas HTTP y PostgreSQL del chat. Ejecutar desde server, con la aplicación en 8082."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import json
import sys
import uuid

import soporte_chat as soporte


def verificar():
    datos = json.loads(soporte.ESTADO.read_text(encoding='utf-8'))
    cuentas = datos['cuentas']
    empresa = soporte.iniciar_sesion(cuentas['empresa'], 'empresa')
    reclutador = soporte.iniciar_sesion(cuentas['reclutador'], 'empresa')
    otra = soporte.iniciar_sesion(cuentas['otra'], 'empresa')
    postulante = soporte.iniciar_sesion(cuentas['postulante'], 'postulante')
    segundo = soporte.iniciar_sesion(cuentas['segundo'], 'postulante')
    comprobar = soporte.comprobar
    comprobar(soporte.cliente(), '/api/chat', 401)
    comprobar(postulante, '/panel/empresa/mensajes', 403)
    comprobar(empresa, '/panel/postulante/mensajes', 403)
    comprobar(empresa, '/api/chat?pagina=-1', 400)
    assert comprobar(empresa, '/api/chat')['total'] == 0
    assert comprobar(postulante, '/api/chat')['total'] == 0
    vacante = comprobar(empresa, '/api/vacantes/publicar', 201, 'POST', dict(
        titulo='Practicante de desarrollo web QA Chat', descripcion='Coordinación de entrevista para pruebas del chat.',
        idCarrera=datos['carrera'], habilidades=[datos['habilidad']], modalidad='remota', ciudad='Lima', codigoPais='PE',
        fechaVencimiento=(datetime.now(timezone.utc) + timedelta(days=10)).isoformat()))
    postulacion = comprobar(postulante, '/api/postulaciones', 201, 'POST',
        dict(idVacante=vacante['id'], cartaPresentacion='Soy estudiante y deseo coordinar una entrevista.'))
    identificador = postulacion['postulacion']['id']
    ruta = f'/api/chat/postulaciones/{identificador}'
    datos.update(idPostulacion=identificador, idVacante=vacante['id'])
    soporte.ESTADO.write_text(json.dumps(datos), encoding='utf-8')

    def envio(contenido='Hola, Ana.\n¿Tienes disponibilidad para una entrevista?', **cambios):
        return dict(idEnvio=str(uuid.uuid4()), contenido=contenido) | cambios

    def cambiar(estado):
        actual = comprobar(empresa, f'/api/postulaciones/{identificador}')['postulacion']
        return comprobar(empresa, f'/api/postulaciones/{identificador}/estado', 200, 'PUT',
            dict(estado=estado, version=actual['version']))

    assert not comprobar(empresa, ruta)['puedeEnviar']
    comprobar(empresa, ruta + '/mensajes', 409, 'POST', envio())
    assert not comprobar(empresa, ruta)['iniciada']
    cambiar('seleccionada')
    assert comprobar(empresa, ruta)['puedeEnviar']
    assert not comprobar(postulante, ruta)['puedeEnviar']
    comprobar(postulante, ruta + '/mensajes', 409, 'POST', envio())
    comprobar(empresa, ruta + '/mensajes', 403, 'POST', envio(), False)
    comprobar(otra, ruta, 404)
    comprobar(segundo, ruta, 404)
    comprobar(otra, ruta + '/mensajes', 404, 'POST', envio())
    comprobar(segundo, ruta + '/mensajes', 404, 'POST', envio())
    for invalido in [envio(' \n\t'), envio('a' * 4001), envio('Hola\u0000equipo'), envio(idEmisor=datos['usuarios'][cuentas['postulante']]),
            envio(idEnvio='incorrecto')]:
        comprobar(empresa, ruta + '/mensajes', 400, 'POST', invalido)
    contenido = "Hola, Ana.\n\nPodemos coordinar una entrevista.\n<script>alert('QA')</script>"
    primero = envio(contenido)
    guardado = comprobar(empresa, ruta + '/mensajes', 201, 'POST', primero)
    repetido = comprobar(empresa, ruta + '/mensajes', 200, 'POST', primero)
    assert guardado['mensaje'] == repetido['mensaje'] and not repetido['creado']
    comprobar(empresa, ruta + '/mensajes', 409, 'POST', primero | dict(contenido='Otro mensaje'))
    assert comprobar(postulante, ruta)['puedeEnviar']
    respuesta = comprobar(postulante, ruta + '/mensajes', 201, 'POST', envio('Sí, tengo disponibilidad.\nGracias.'))
    assert respuesta['mensaje']['tipoEmisor'] == 'postulante'
    assert respuesta['mensaje']['idEmisor'] == datos['usuarios'][cuentas['postulante']]
    atendido = comprobar(reclutador, ruta + '/mensajes', 201, 'POST', envio('Soy Elena, continuaré la coordinación.'))
    assert atendido['mensaje']['nombreEmisor'] == 'Elena Prueba QA'
    html = comprobar(postulante, f'/panel/postulante/postulaciones/{identificador}/chat')
    assert '&lt;script&gt;' in html and '<script>alert' not in html and 'maxlength="4000"' in html
    assert 'Mensajes' in comprobar(empresa, '/panel/empresa/mensajes')
    comprobar(empresa, ruta + '?antesDe=0', 400)
    comprobar(empresa, ruta + '?antesDe=1&despuesDe=2', 400)
    comprobar(empresa, ruta + '?despuesDe=abc', 400)
    simultaneo = envio('Mensaje concurrente con idempotencia.')
    token = comprobar(empresa, '/api/sesion/seguridad')['tokenCsrf']
    with ThreadPoolExecutor(max_workers=2) as conjunto:
        resultados = list(conjunto.map(lambda _: soporte.solicitar(empresa, ruta + '/mensajes', 'POST', simultaneo, token)[0], range(2)))
    assert sorted(resultados) == [200, 201], resultados
    assert int(soporte.ejecutar_sql(f"SELECT count(*) FROM mensajes_chat WHERE id_postulacion={identificador} AND id_envio='{simultaneo['idEnvio']}'")) == 1
    comprobar(empresa, ruta + '/mensajes', 201, 'POST', envio('a' * 4000))
    # Los mensajes históricos de QA se insertan con fechas anteriores al límite de frecuencia.
    soporte.ejecutar_sql(f"""INSERT INTO mensajes_chat(id_postulacion,id_emisor,id_envio,nombre_emisor,tipo_emisor,contenido,fecha_envio)
        SELECT {identificador},{datos['usuarios'][cuentas['empresa']]},md5('{datos['etiqueta']}' || numero::text)::uuid,
            'Luis Prueba QA','empresa','Historial QA ' || numero,clock_timestamp() - interval '2 minutes'
        FROM generate_series(1,55) numero;""")
    ultimo = comprobar(postulante, ruta)
    assert len(ultimo['mensajes']) == 50 and ultimo['hayAnteriores']
    anterior = comprobar(postulante, ruta + '?antesDe=' + str(ultimo['mensajes'][0]['id']))
    ids = [mensaje['id'] for mensaje in anterior['mensajes'] + ultimo['mensajes']]
    assert len(ids) == len(set(ids)) == 60 and ids == sorted(ids)
    bloques = []
    cursor = ids[0]
    while True:
        pagina = comprobar(postulante, ruta + f'?despuesDe={cursor}')
        bloques.extend(mensaje['id'] for mensaje in pagina['mensajes'])
        if not pagina['haySiguientes']:
            break
        cursor = bloques[-1]
    assert bloques == ids[1:]
    comprobar(empresa, f"/api/vacantes/{vacante['id']}/cerrar", 200, 'POST', dict(version=vacante['version']))
    comprobar(empresa, ruta + '/mensajes', 201, 'POST', envio('La recepción cerró; seguimos coordinando.'))
    cambiar('descartada')
    assert not comprobar(postulante, ruta)['puedeEnviar']
    comprobar(postulante, ruta + '/mensajes', 409, 'POST', envio())
    comprobar(empresa, ruta + '/mensajes', 409, 'POST', envio())
    comprobar(empresa, ruta + '/mensajes', 200, 'POST', primero)
    cambiar('en_revision')
    assert not comprobar(empresa, ruta)['puedeEnviar']
    cambiar('seleccionada')
    # El retiro todavía no tiene acción de usuario; se comprueba el estado existente del esquema.
    soporte.ejecutar_sql(f"UPDATE applications SET status_application='retirada' WHERE id_application={identificador}")
    assert not comprobar(empresa, ruta)['puedeEnviar']
    comprobar(empresa, ruta + '/mensajes', 409, 'POST', envio())
    soporte.ejecutar_sql(f"UPDATE applications SET status_application='seleccionada' WHERE id_application={identificador}")
    id_reclutador = datos['usuarios'][cuentas['reclutador']]
    soporte.ejecutar_sql(f'UPDATE company_members SET active_company_member=false WHERE user_id_company_member={id_reclutador}')
    comprobar(reclutador, ruta, 401)
    soporte.ejecutar_sql(f'UPDATE company_members SET active_company_member=true WHERE user_id_company_member={id_reclutador}')
    assert comprobar(otra, '/api/chat')['total'] == 0
    assert comprobar(segundo, '/api/chat')['total'] == 0
    assert comprobar(postulante, '/api/chat')['total'] == 1
    otra_vacante = comprobar(empresa, '/api/vacantes/publicar', 201, 'POST', dict(
        titulo='Segunda vacante QA Chat', descripcion='Prueba de separación entre postulaciones.',
        idCarrera=datos['carrera'], habilidades=[datos['habilidad']], modalidad='remota', ciudad='Lima', codigoPais='PE',
        fechaVencimiento=(datetime.now(timezone.utc) + timedelta(days=10)).isoformat()))
    otra_postulacion = comprobar(postulante, '/api/postulaciones', 201, 'POST',
        dict(idVacante=otra_vacante['id'], cartaPresentacion='Segunda postulación de QA.'))['postulacion']
    otro_id = otra_postulacion['id']
    otra_ruta = f'/api/chat/postulaciones/{otro_id}'
    comprobar(empresa, f'/api/postulaciones/{otro_id}/estado', 200, 'PUT',
        dict(estado='seleccionada', version=otra_postulacion['version']))
    assert not comprobar(empresa, otra_ruta)['iniciada'] and comprobar(empresa, otra_ruta)['mensajes'] == []
    for numero in range(20):
        comprobar(empresa, otra_ruta + '/mensajes', 201, 'POST', envio(f'Control de frecuencia QA {numero}'))
    comprobar(empresa, otra_ruta + '/mensajes', 429, 'POST', envio())
    assert comprobar(postulante, '/api/chat')['total'] == 2
    assert all(not mensaje['contenido'].startswith('Control de frecuencia') for mensaje in comprobar(postulante, ruta)['mensajes'])
    soporte.ejecutar_sql(f"UPDATE mensajes_chat SET fecha_envio=clock_timestamp() - interval '2 minutes' WHERE id_postulacion={otro_id}")
    evaluacion = comprobar(empresa, f'/api/postulaciones/{otro_id}')['postulacion']
    carrera = envio('Mensaje que compite con el cambio de estado.')
    with ThreadPoolExecutor(max_workers=2) as conjunto:
        envio_concurrente = conjunto.submit(soporte.solicitar, empresa, otra_ruta + '/mensajes', 'POST', carrera, token)
        cambio_concurrente = conjunto.submit(soporte.solicitar, empresa, f'/api/postulaciones/{otro_id}/estado', 'PUT',
            dict(estado='descartada', version=evaluacion['version']), token)
        codigo_envio, codigo_cambio = envio_concurrente.result()[0], cambio_concurrente.result()[0]
    assert codigo_cambio == 200 and codigo_envio in (201, 409), (codigo_envio, codigo_cambio)
    assert not comprobar(empresa, otra_ruta)['puedeEnviar']
    comprobar(empresa, otra_ruta + '/mensajes', 409, 'POST', envio())
    existentes = int(soporte.ejecutar_sql(f"SELECT count(*) FROM mensajes_chat WHERE id_postulacion={otro_id} AND id_envio='{carrera['idEnvio']}'"))
    assert existentes == (1 if codigo_envio == 201 else 0)
    informe = dict(verificacion='correcta', permisos=True, seleccion=True, inicio_empresarial=True, csrf=True,
        idempotencia_concurrente=True, historial_paginado=True, orden_incremental=True, texto_seguro=True,
        vacante_cerrada=True, solo_lectura=True, membresia_revocada=True, aislamiento_por_postulacion=True,
        limite_frecuencia=True, estado_concurrente=True)
    (soporte.RAIZ / 'target/verificacion_chat_bd.json').write_text(json.dumps(informe), encoding='utf-8')
    print(json.dumps(informe))


if __name__ == '__main__':
    accion = sys.argv[1] if len(sys.argv) == 2 else 'integral'
    if accion == 'preparar':
        soporte.preparar()
    elif accion == 'verificar':
        verificar()
    elif accion == 'limpiar':
        soporte.limpiar()
    elif accion == 'integral':
        soporte.preparar()
        try:
            verificar()
        finally:
            soporte.limpiar()
    else:
        raise SystemExit('Usa preparar, verificar, limpiar o integral.')
