"use strict";

const prueba = require("node:test");
const comprobar = require("node:assert/strict");
const { readFileSync: leerArchivo } = require("node:fs");
const { resolve: resolverRuta } = require("node:path");
const { createContext: crearContexto, runInContext: ejecutarContexto } = require("node:vm");
const carpeta = resolverRuta(__dirname, "../../main/resources/static/js");
const codigo = ["sesion.js", "postulaciones.js"].map((archivo) => leerArchivo(resolverRuta(carpeta, archivo), "utf8"));
const version = "2026-10-28T15:00:00.123456Z";
const usuario = { idUsuario: 7, nombres: "Ana", apellidos: "Pérez", correo: "ana@example.test", tipoCuenta: "postulante" };
const empresa = { ...usuario, tipoCuenta: "empresa", idEmpresa: 9, nombreEmpresa: "Empresa", rolEmpresa: "reclutador" };
const respuesta = (estado, contenido) => ({ status: estado, json: async () => contenido });
const seguridad = () => respuesta(200, { tokenCsrf: "a".repeat(43) });

const crearElemento = (datos = {}) => {
    const escuchas = new Map();
    return {
        value: "", hidden: false, disabled: false, dataset: {}, atributos: {}, textContent: "", validacion: "", ...datos,
        addEventListener(tipo, escucha) { escuchas.set(tipo, escucha); },
        emitir(tipo, evento = {}) { return escuchas.get(tipo)?.(evento); },
        setAttribute(nombre, valor) { this.atributos[nombre] = valor; },
        setCustomValidity(mensaje) { this.validacion = mensaje; },
        classList: { toggle() {} }
    };
};

const preparar = (respuestas, opciones = {}) => {
    const carta = crearElemento({ value: "  Hola\nQuiero aportar con mis proyectos.  " });
    const selector = crearElemento({ value: "en_revision" });
    const boton = crearElemento({ textContent: "Enviar postulación" });
    const controles = [opciones.empresa ? selector : carta, boton];
    const formulario = crearElemento({ dataset: { idVacante: "12", idPostulacion: "15", version },
        elements: { namedItem: (nombre) => nombre === "cartaPresentacion" ?
            (opciones.empresa ? null : carta) : selector },
        querySelectorAll: () => controles, querySelector: () => boton,
        reportValidity: () => opciones.empresa ? Boolean(selector.value) : !carta.validacion && carta.value.length <= 12000 });
    const elementos = { panel: crearElemento({ dataset: { tipoCuenta: opciones.empresa ? "empresa" : "postulante",
        idUsuario: "7", idEmpresa: "9", rolEmpresa: "reclutador" } }),
        "panel-contenido": crearElemento(), "postulacion-estado": crearElemento({ hidden: true }),
        "carta-contador": crearElemento(), [opciones.empresa ? "formulario-estado-postulacion" : "formulario-postulacion"]: formulario };
    const eventos = [];
    const documento = { body: { dataset: { rutaBase: opciones.contexto || "/" } },
        getElementById: (id) => elementos[id] || null, addEventListener: (tipo, escucha) => eventos.push(escucha) };
    const destinos = [], solicitudes = [], pendientes = [...respuestas];
    const ventana = { setTimeout, clearTimeout, location: { origin: "http://localhost:8080", hostname: "localhost", port: "8080",
        replace: (destino) => destinos.push(destino), assign: (destino) => destinos.push(destino) } };
    const contexto = crearContexto({ document: documento, window: ventana, URL, AbortController,
        fetch: async (url, configuracion) => {
            solicitudes.push({ url, ...configuracion });
            comprobar.ok(pendientes.length, `Solicitud inesperada: ${url}`);
            const siguiente = pendientes.shift();
            if (siguiente instanceof Error) throw siguiente;
            return typeof siguiente === "function" ? siguiente() : siguiente;
        } });
    codigo.forEach((fuente) => ejecutarContexto(fuente, contexto));
    eventos.forEach((escucha) => escucha());
    return { carta, selector, boton, formulario, elementos, destinos, solicitudes,
        enviar: () => formulario.emitir("submit", { preventDefault() {} }) };
};

prueba("envía carta con párrafos, identidad comprobada, CSRF y prefijo de contexto", async () => {
    const entorno = preparar([respuesta(200, usuario), seguridad(), respuesta(201, { postulacion: { id: 15 } })],
        { contexto: "/primerpaso/" });
    await entorno.enviar();
    comprobar.deepEqual(entorno.solicitudes.map((solicitud) => solicitud.url), [
        "/primerpaso/api/sesion", "/primerpaso/api/sesion/seguridad", "/primerpaso/api/postulaciones" ]);
    const solicitud = entorno.solicitudes[2];
    comprobar.equal(solicitud.method, "POST");
    comprobar.equal(solicitud.headers["X-CSRF-Token"], "a".repeat(43));
    comprobar.equal(solicitud.cache, "no-store");
    comprobar.equal(solicitud.credentials, "include");
    comprobar.deepEqual(JSON.parse(solicitud.body), { idVacante: "12", cartaPresentacion: "Hola\nQuiero aportar con mis proyectos." });
    comprobar.deepEqual(entorno.destinos, ["/primerpaso/panel/postulante/postulaciones/15"]);
});

prueba("el cambio de estado conserva la versión completa y usa el contexto empresarial", async () => {
    const entorno = preparar([respuesta(200, empresa), seguridad(), respuesta(200, { postulacion: { id: 15 } })], { empresa: true });
    await entorno.enviar();
    comprobar.equal(entorno.solicitudes[2].method, "PUT");
    comprobar.equal(entorno.solicitudes[2].url, "/api/postulaciones/15/estado");
    comprobar.deepEqual(JSON.parse(entorno.solicitudes[2].body), { estado: "en_revision", version });
    comprobar.deepEqual(entorno.destinos, ["/panel/empresa/postulaciones/15"]);
});

for (const estado of [400, 403, 404, 409, 503]) {
    prueba(`conserva la carta y habilita el reintento tras HTTP ${estado}`, async () => {
        const entorno = preparar([respuesta(200, usuario), seguridad(), respuesta(estado, { mensaje: "Revisa la solicitud." })]);
        const original = entorno.carta.value;
        await entorno.enviar();
        comprobar.equal(entorno.carta.value, original);
        comprobar.equal(entorno.carta.disabled, false);
        comprobar.equal(entorno.boton.disabled, false);
        comprobar.equal(entorno.elementos["postulacion-estado"].textContent, "Revisa la solicitud.");
        comprobar.deepEqual(entorno.destinos, []);
    });
}

prueba("no permite un envío vacío ni uno que excede el límite", async () => {
    const entorno = preparar([]);
    for (const valor of ["  \n\t", "a".repeat(12001)]) {
        entorno.carta.value = valor;
        await entorno.enviar();
    }
    comprobar.equal(entorno.solicitudes.length, 0);
});

prueba("impide enviar la carta si la cuenta cambió en otra pestaña", async () => {
    const entorno = preparar([respuesta(200, { ...usuario, idUsuario: 10 })]);
    await entorno.enviar();
    comprobar.equal(entorno.solicitudes.length, 1);
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
    comprobar.deepEqual(entorno.destinos, ["/panel"]);
});

prueba("redirige al acceso ante una sesión vencida", async () => {
    const entorno = preparar([respuesta(401)]);
    await entorno.enviar();
    comprobar.equal(entorno.solicitudes.length, 1);
    comprobar.deepEqual(entorno.destinos, ["/iniciar-sesion"]);
});

prueba("no escribe si no obtiene un token válido", async () => {
    const entorno = preparar([respuesta(200, usuario), respuesta(200, { tokenCsrf: "inválido" })]);
    await entorno.enviar();
    comprobar.equal(entorno.solicitudes.length, 2);
    comprobar.equal(entorno.carta.disabled, false);
});

prueba("un error de red conserva el contenido e indica comprobar el resultado", async () => {
    const entorno = preparar([respuesta(200, usuario), seguridad(), new Error("sin conexión")]);
    const original = entorno.carta.value;
    await entorno.enviar();
    comprobar.equal(entorno.carta.value, original);
    comprobar.match(entorno.elementos["postulacion-estado"].textContent, /comprueba el resultado/);
});

prueba("evita envíos simultáneos desde el mismo formulario", async () => {
    let resolver;
    const pendiente = new Promise((completar) => { resolver = completar; });
    const entorno = preparar([() => pendiente, seguridad(), respuesta(201, { postulacion: { id: 15 } })]);
    const primera = entorno.enviar();
    await entorno.enviar();
    comprobar.equal(entorno.solicitudes.length, 1);
    resolver(respuesta(200, usuario));
    await primera;
    comprobar.equal(entorno.solicitudes.length, 3);
});
