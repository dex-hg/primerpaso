"use strict";

const prueba = require("node:test");
const comprobar = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const codigo = fs.readFileSync(path.join(__dirname, "../../main/resources/static/js/contratacion.js"), "utf8");
const versionPrecisa = "2026-11-05T14:10:32.123456Z";
const usuario = { idUsuario: 7, tipoCuenta: "postulante" };

const preparar = (ruta, datos, opciones = {}) => {
    const llamadas = [];
    const cambios = [];
    const destinos = [];
    const control = { disabled: false };
    const formulario = {
        dataset: { contratacionRuta: ruta },
        elements: [control],
        datos,
        reportValidity: () => true,
        setAttribute() {},
        addEventListener(nombre, accion) { this[nombre] = accion; }
    };
    const panel = { dataset: { idUsuario: "7", tipoCuenta: "postulante" } };
    const contenido = { hidden: false };
    const estado = { focus() { this.enfocado = true; } };
    const elementos = { panel, "contratacion-estado": estado, "panel-contenido": contenido };
    const documento = {
        getElementById: (id) => elementos[id],
        querySelectorAll: () => [formulario],
        addEventListener: (nombre, accion) => accion()
    };
    const sesion = {
        obtenerRuta: (ruta) => "/primerpaso/" + ruta,
        validarUsuario: (identidad) => identidad != null,
        solicitarSesion: async () => ({ estado: 200, contenido: opciones.identidad ?? usuario }),
        mostrarEstado: (elemento, mensaje, error) => cambios.push({ mensaje, error })
    };

    vm.runInNewContext(codigo, {
        document: documento, AbortController,
        FormData: class {
            constructor(formulario, boton) {
                const campos = { ...formulario.datos };
                if (boton?.name) campos[boton.name] = boton.value;
                return Object.entries(campos);
            }
        },
        window: {
            sesionPrimerPaso: sesion, setTimeout, clearTimeout,
            location: { reload: () => destinos.push("recarga"), replace: (ruta) => destinos.push(ruta) }
        },
        fetch: async (url, parametros) => {
            llamadas.push({ url, parametros });
            if (url.endsWith("/seguridad")) {
                return { status: 200, ok: true, json: async () => ({ tokenCsrf: "x".repeat(43) }) };
            }
            const codigo = opciones.estado ?? 200;
            return { status: codigo, ok: codigo < 300, json: async () => ({ mensaje: opciones.mensaje }) };
        }
    });

    return {
        llamadas, cambios, destinos, contenido, estado, control,
        enviar: (boton) => formulario.submit({ preventDefault() {}, submitter: boton })
    };
};

prueba("envía condiciones y plazo de Lima con la versión original y el token CSRF", async () => {
    const datos = {
        idEnvio: "52c5c2a1-d961-41d8-a159-c3d3ab6a66e1", version: versionPrecisa,
        condiciones: "  Inicio acordado.\nJornada de 30 horas.  ", fechaInicio: "2040-07-01",
        venceEn: "2040-06-28T14:30"
    };
    const vista = preparar("api/postulaciones/12/ofertas", datos);
    await vista.enviar();
    const solicitud = vista.llamadas[1];
    comprobar.equal(solicitud.url, "/primerpaso/api/postulaciones/12/ofertas");
    comprobar.equal(solicitud.parametros.headers["X-CSRF-Token"], "x".repeat(43));
    comprobar.deepEqual(JSON.parse(solicitud.parametros.body), {
        ...datos, condiciones: "Inicio acordado.\nJornada de 30 horas.", venceEn: "2040-06-28T19:30:00.000Z"
    });
    comprobar.deepEqual(vista.destinos, ["recarga"]);
});

prueba("distingue aceptar y rechazar mediante el botón pulsado", async () => {
    for (const aceptada of [true, false]) {
        const vista = preparar("api/postulaciones/12/ofertas/5/respuesta", {});
        await vista.enviar({ name: "aceptada", value: String(aceptada) });
        comprobar.deepEqual(JSON.parse(vista.llamadas[1].parametros.body), { aceptada });
    }
});

prueba("transmite la vista previa del cierre como cantidad y tolera la respuesta vacía", async () => {
    const vista = preparar("api/vacantes/8/cancelar-proceso", {
        versionVacante: versionPrecisa, postulacionesAFinalizar: "0", motivo: "  Cancelación acordada  "
    }, { estado: 204 });
    await vista.enviar();
    comprobar.deepEqual(JSON.parse(vista.llamadas[1].parametros.body), {
        versionVacante: versionPrecisa, postulacionesAFinalizar: 0, motivo: "Cancelación acordada"
    });
    comprobar.deepEqual(vista.destinos, ["recarga"]);
});

prueba("un cambio de sesión bloquea la mutación", async () => {
    const vista = preparar("api/postulaciones/12/retirar", { version: versionPrecisa }, {
        identidad: { ...usuario, idUsuario: 11 }
    });
    await vista.enviar();
    comprobar.equal(vista.llamadas.length, 0);
    comprobar.equal(vista.contenido.hidden, true);
    comprobar.deepEqual(vista.destinos, ["/primerpaso/panel"]);
});

prueba("un conflicto deja visibles el mensaje y los controles para revisar", async () => {
    const vista = preparar("api/postulaciones/12/ofertas/5/confirmar", {
        versionVacante: versionPrecisa, postulacionesAFinalizar: "2"
    }, { estado: 409, mensaje: "Cambió el número de postulaciones por finalizar." });
    await vista.enviar();
    comprobar.deepEqual(vista.cambios.at(-1), { mensaje: "Cambió el número de postulaciones por finalizar.", error: true });
    comprobar.equal(vista.estado.enfocado, true);
    comprobar.equal(vista.control.disabled, false);
    comprobar.equal(vista.destinos.length, 0);
});

