"use strict";

const prueba = require("node:test");
const comprobar = require("node:assert/strict");
const { readFileSync: leerArchivo } = require("node:fs");
const { resolve: resolverRuta } = require("node:path");
const { createContext: crearContexto, runInContext: ejecutarContexto } = require("node:vm");

const carpetaScripts = resolverRuta(__dirname, "../../main/resources/static/js");
const codigoSesion = leerArchivo(resolverRuta(carpetaScripts, "sesion.js"), "utf8");
const codigoVacantes = leerArchivo(resolverRuta(carpetaScripts, "vacantes.js"), "utf8");
const versionPrecisa = "2026-10-05T14:00:00.123456Z";
const crearRespuesta = (estado, contenido = null) => ({ status: estado, json: async () => contenido });
const respuestaSeguridad = () => crearRespuesta(200, { tokenCsrf: "csrf-de-prueba" });
const crearEventos = () => {
    const escuchas = new Map();
    return {
        addEventListener(tipo, escucha) {
            if (!escuchas.has(tipo)) escuchas.set(tipo, []);
            escuchas.get(tipo).push(escucha);
        },
        async emitir(tipo, evento = {}) {
            await Promise.all((escuchas.get(tipo) || []).map((escucha) => escucha(evento)));
            await new Promise((resolver) => setImmediate(resolver));
        }
    };
};
const crearElemento = (datos = {}) => {
    const clases = new Set();
    return {
        ...crearEventos(), dataset: {}, hidden: false, disabled: false, textContent: "", value: "",
        atributos: {}, hijos: [], validacion: "", requerido: false, retirado: false, ...datos,
        setAttribute(nombre, valor) { this.atributos[nombre] = valor; },
        setCustomValidity(mensaje) { this.validacion = mensaje; },
        checkValidity() { return !this.validacion && (!this.requerido || this.value.trim() !== ""); },
        reportValidity() { return this.checkValidity(); },
        focus() { this.enfocado = true; }, scrollIntoView() { this.desplazado = true; },
        replaceChildren() { this.hijos = []; },
        appendChild(hijo) { hijo.padre = this; this.hijos.push(hijo); },
        after(elemento) { this.siguiente = elemento; }, remove() { this.retirado = true; },
        get childElementCount() { return this.hijos.length; },
        classList: {
            add(nombre) { clases.add(nombre); },
            toggle(nombre, activo) { if (activo) clases.add(nombre); else clases.delete(nombre); },
            contains(nombre) { return clases.has(nombre); }
        }
    };
};

const prepararVacantes = (respuestas, opciones = {}) => {
    const valores = {
        titulo: "  Practicante web  ", descripcion: "  Funciones y requisitos\nSegunda línea  ",
        idCarrera: "3", modalidad: "hibrida", ciudad: " Lima ", codigoPais: "pe",
        fechaVencimiento: "2040-06-05T11:30"
    };
    const campos = Object.fromEntries(Object.entries(valores).map(([nombre, value]) =>
        [nombre, crearElemento({ name: nombre, value, requerido: true })]));
    campos.idCarrera.selectedOptions = [{ disabled: false }];
    const habilidades = [2, 4, 6].map((id) => crearElemento({
        name: "habilidades", value: String(id), checked: id !== 6
    }));
    const botonGuardar = crearElemento({ disabled: Boolean(opciones.guardarDeshabilitado) });
    const formulario = crearElemento({
        dataset: { idVacante: opciones.nueva ? "" : "19", version: opciones.nueva ? "" : versionPrecisa,
            soloLectura: opciones.soloLectura ? "true" : "false" },
        elements: { namedItem: (nombre) => campos[nombre] },
        querySelectorAll: () => habilidades,
        querySelector: () => Object.values(campos).find((campo) => !campo.checkValidity()),
        checkValidity: () => Object.values(campos).every((campo) => campo.checkValidity()),
        reportValidity: () => formulario.checkValidity()
    });
    const publicar = crearElemento({ dataset: { accion: "publicar", idVacante: "19", version: versionPrecisa } });
    const cerrar = crearElemento({ dataset: { accion: "cerrar", idVacante: "19", version: versionPrecisa } });
    const acciones = [publicar, cerrar];
    const elementos = {
        panel: crearElemento(), "panel-contenido": crearElemento(), "vacantes-estado": crearElemento(),
        "vacante-errores": crearElemento({ hidden: true }), "cerrar-sesion": crearElemento(),
        "formulario-vacante": opciones.sinFormulario ? null : formulario,
        "habilidades-error": crearElemento({ hidden: true })
    };
    elementos.panel.querySelectorAll = (selector) => selector === "[data-accion]" ? acciones :
        [...Object.values(campos), ...habilidades, botonGuardar, ...acciones];
    const agregados = [];
    const estaRetirado = (elemento) => elemento.retirado || (elemento.padre && estaRetirado(elemento.padre));
    const documento = {
        ...crearEventos(), body: { dataset: { rutaBase: opciones.rutaBase || "/" } },
        getElementById: (id) => elementos[id] || agregados.find((elemento) => elemento.id === id && !estaRetirado(elemento)) || null,
        createElement: () => { const elemento = crearElemento(); agregados.push(elemento); return elemento; }
    };
    const destinos = [], solicitudes = [], pendientes = [...respuestas];
    const ventana = {
        ...crearEventos(), setTimeout, clearTimeout,
        location: { origin: "http://localhost:8080", hostname: "localhost", port: "8080",
            replace: (destino) => destinos.push(destino), assign: (destino) => destinos.push(destino) }
    };
    const contexto = crearContexto({ document: documento, window: ventana, URL, AbortController,
        fetch: async (url, configuracion) => {
            solicitudes.push({ url, ...configuracion, headers: { ...configuracion.headers } });
            comprobar.ok(pendientes.length, `Solicitud inesperada: ${url}`);
            const siguiente = pendientes.shift();
            if (typeof siguiente === "function") return siguiente();
            if (siguiente instanceof Error) throw siguiente;
            return siguiente;
        }
    });
    ejecutarContexto(codigoSesion, contexto, { filename: "sesion.js" });
    ejecutarContexto(codigoVacantes, contexto, { filename: "vacantes.js" });
    return { elementos, campos, habilidades, formulario, publicar, cerrar, botonGuardar, destinos,
        documento, solicitudes, iniciar: () => documento.emitir("DOMContentLoaded"),
        confirmar: () => documento.getElementById("vacante-confirmar").emitir("click"),
        cancelar: () => documento.getElementById("vacante-cancelar").emitir("click"),
        guardar: () => formulario.emitir("submit", { preventDefault() {} }) };
};

prueba("crear un borrador envía habilidades y fecha de Lima con CSRF y contexto", async () => {
    const entorno = prepararVacantes([respuestaSeguridad(), crearRespuesta(201, { id: 29 })], {
        nueva: true, rutaBase: "/primerpaso/"
    });
    await entorno.iniciar();
    await entorno.guardar();
    comprobar.deepEqual(entorno.solicitudes.map(({ url, method }) => ({ url, metodo: method })), [
        { url: "/primerpaso/api/sesion/seguridad", metodo: "GET" },
        { url: "/primerpaso/api/vacantes", metodo: "POST" }
    ]);
    const solicitud = entorno.solicitudes[1];
    comprobar.equal(solicitud.headers["X-CSRF-Token"], "csrf-de-prueba");
    comprobar.equal(solicitud.credentials, "include");
    comprobar.equal(solicitud.cache, "no-store");
    comprobar.deepEqual(JSON.parse(solicitud.body), {
        titulo: "Practicante web", descripcion: "Funciones y requisitos\nSegunda línea",
        idCarrera: 3, habilidades: [2, 4], modalidad: "hibrida", ciudad: "Lima",
        codigoPais: "PE", fechaVencimiento: "2040-06-05T16:30:00.000Z"
    });
    comprobar.deepEqual(entorno.destinos, ["/primerpaso/panel/empresa/vacantes/29/editar"]);
});

prueba("editar conserva la versión de PostgreSQL con sus microsegundos", async () => {
    const entorno = prepararVacantes([respuestaSeguridad(), crearRespuesta(200, { id: 19 })]);
    await entorno.iniciar();
    await entorno.guardar();
    comprobar.equal(entorno.solicitudes[1].method, "PUT");
    comprobar.equal(entorno.solicitudes[1].url, "/api/vacantes/19");
    comprobar.equal(JSON.parse(entorno.solicitudes[1].body).version, versionPrecisa);
});

prueba("los cambios pendientes deben guardarse antes de publicar", async () => {
    const entorno = prepararVacantes([]);
    await entorno.iniciar();
    await entorno.formulario.emitir("input", { target: entorno.campos.titulo });
    await entorno.publicar.emitir("click");
    comprobar.equal(entorno.solicitudes.length, 0);
    comprobar.equal(entorno.documento.getElementById("vacante-confirmacion"), null);
    comprobar.match(entorno.elementos["vacantes-estado"].textContent, /Guarda los cambios/);
});

prueba("publicar envía sólo la versión precisa después de confirmar", async () => {
    const entorno = prepararVacantes([respuestaSeguridad(), crearRespuesta(200)], { sinFormulario: true });
    await entorno.iniciar();
    await entorno.publicar.emitir("click");
    comprobar.equal(entorno.solicitudes.length, 0);
    comprobar.equal(entorno.documento.getElementById("vacante-confirmacion").atributos.role, "alert");
    comprobar.equal(entorno.documento.getElementById("vacante-cancelar").enfocado, true);
    await entorno.confirmar();
    comprobar.equal(entorno.solicitudes[1].url, "/api/vacantes/19/publicar");
    comprobar.deepEqual(JSON.parse(entorno.solicitudes[1].body), { version: versionPrecisa });
});

prueba("cancelar la confirmación evita cerrar una vacante", async () => {
    const entorno = prepararVacantes([]);
    await entorno.iniciar();
    await entorno.cerrar.emitir("click");
    comprobar.equal(entorno.solicitudes.length, 0);
    const pregunta = entorno.documento.getElementById("vacante-confirmacion").hijos[0].textContent;
    comprobar.match(pregunta, /Pasará a Cerrada/);
    comprobar.doesNotMatch(pregunta, /postulaciones/);
    await entorno.cancelar();
    comprobar.equal(entorno.documento.getElementById("vacante-confirmacion"), null);
    comprobar.equal(entorno.cerrar.enfocado, true);
});

prueba("Escape cancela la confirmación y devuelve el foco al botón original", async () => {
    const entorno = prepararVacantes([]);
    await entorno.iniciar();
    await entorno.publicar.emitir("click");
    let cancelado = false;
    await entorno.documento.getElementById("vacante-confirmacion").emitir("keydown", {
        key: "Escape", preventDefault() { cancelado = true; }
    });
    comprobar.equal(cancelado, true);
    comprobar.equal(entorno.documento.getElementById("vacante-confirmacion"), null);
    comprobar.equal(entorno.publicar.enfocado, true);
    comprobar.equal(entorno.solicitudes.length, 0);
});

prueba("sólo una confirmación conserva la acción y la versión originalmente elegidas", async () => {
    const entorno = prepararVacantes([respuestaSeguridad(), crearRespuesta(200)], { sinFormulario: true });
    await entorno.iniciar();
    await entorno.publicar.emitir("click");
    const confirmacion = entorno.documento.getElementById("vacante-confirmacion");
    entorno.publicar.dataset.version = "2026-10-05T14:30:00.999999Z";
    await entorno.cerrar.emitir("click");
    comprobar.equal(entorno.documento.getElementById("vacante-confirmacion"), confirmacion);
    await entorno.confirmar();
    comprobar.equal(entorno.solicitudes[1].url, "/api/vacantes/19/publicar");
    comprobar.deepEqual(JSON.parse(entorno.solicitudes[1].body), { version: versionPrecisa });
});

prueba("editar después de abrir una confirmación la cancela sin publicar datos anteriores", async () => {
    const entorno = prepararVacantes([]);
    await entorno.iniciar();
    await entorno.publicar.emitir("click");
    entorno.campos.titulo.value = "Título cambiado";
    await entorno.formulario.emitir("input", { target: entorno.campos.titulo });
    comprobar.equal(entorno.documento.getElementById("vacante-confirmacion"), null);
    await entorno.publicar.emitir("click");
    comprobar.match(entorno.elementos["vacantes-estado"].textContent, /Guarda los cambios/);
    comprobar.equal(entorno.solicitudes.length, 0);
});

prueba("cerrar con cambios pendientes informa el descarte antes de ejecutar", async () => {
    const entorno = prepararVacantes([respuestaSeguridad(), crearRespuesta(200)]);
    await entorno.iniciar();
    await entorno.formulario.emitir("change", { target: entorno.campos.modalidad });
    await entorno.cerrar.emitir("click");
    const pregunta = entorno.documento.getElementById("vacante-confirmacion").hijos[0].textContent;
    comprobar.match(pregunta, /sin guardar se descartarán/);
    comprobar.equal(entorno.solicitudes.length, 0);
    await entorno.confirmar();
    comprobar.equal(entorno.solicitudes[1].url, "/api/vacantes/19/cerrar");
    comprobar.deepEqual(JSON.parse(entorno.solicitudes[1].body), { version: versionPrecisa });
});

for (const codigo of [409, 503]) {
    prueba(`un error ${codigo} conserva las entradas y no reenvía la operación`, async () => {
        const entorno = prepararVacantes([respuestaSeguridad(), crearRespuesta(codigo)]);
        await entorno.iniciar();
        entorno.campos.titulo.value = "Título que quiero conservar";
        await entorno.guardar();
        comprobar.equal(entorno.campos.titulo.value, "Título que quiero conservar");
        comprobar.equal(entorno.formulario.dataset.version, versionPrecisa);
        comprobar.equal(entorno.habilidades[0].checked, true);
        comprobar.equal(entorno.solicitudes.length, 2);
        comprobar.deepEqual(entorno.destinos, []);
        comprobar.equal(entorno.elementos["vacantes-estado"].classList.contains("is-error"), true);
        comprobar.equal(entorno.botonGuardar.disabled, false);
    });
}

prueba("un CSRF ausente bloquea la mutación y no redirige", async () => {
    const entorno = prepararVacantes([crearRespuesta(200, {})]);
    await entorno.iniciar();
    await entorno.guardar();
    comprobar.equal(entorno.solicitudes.length, 1);
    comprobar.equal(entorno.solicitudes[0].method, "GET");
    comprobar.deepEqual(entorno.destinos, []);
    comprobar.match(entorno.elementos["vacantes-estado"].textContent, /no está autorizada/);
});

for (const duranteMutacion of [false, true]) {
    prueba(`un 401 ${duranteMutacion ? "de la mutación" : "al obtener CSRF"} oculta el panel y vuelve al acceso`, async () => {
        const respuestas = duranteMutacion ? [respuestaSeguridad(), crearRespuesta(401)] : [crearRespuesta(401)];
        const entorno = prepararVacantes(respuestas, { rutaBase: "/primerpaso/" });
        await entorno.iniciar();
        await entorno.guardar();
        comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
        comprobar.deepEqual(entorno.destinos, ["/primerpaso/iniciar-sesion"]);
        comprobar.equal(entorno.solicitudes.length, duranteMutacion ? 2 : 1);
    });
}

prueba("doble envío mientras la operación sigue pendiente genera una sola mutación", async () => {
    let responder;
    const entorno = prepararVacantes([respuestaSeguridad(), () => new Promise((resolver) => { responder = resolver; })]);
    await entorno.iniciar();
    const envio = entorno.guardar();
    await new Promise((resolver) => setImmediate(resolver));
    comprobar.equal(entorno.botonGuardar.disabled, true);
    await entorno.guardar();
    await entorno.publicar.emitir("click");
    comprobar.equal(entorno.solicitudes.length, 2);
    responder(crearRespuesta(200, { id: 19 }));
    await envio;
    comprobar.equal(entorno.botonGuardar.disabled, false);
});

prueba("una fecha pasada, habilidades vacías y carrera inactiva bloquean el envío", async () => {
    const entorno = prepararVacantes([]);
    await entorno.iniciar();
    entorno.campos.fechaVencimiento.value = "2020-01-01T12:00";
    entorno.campos.idCarrera.selectedOptions[0].disabled = true;
    entorno.habilidades.forEach((habilidad) => { habilidad.checked = false; });
    await entorno.guardar();
    comprobar.equal(entorno.solicitudes.length, 0);
    comprobar.ok(entorno.campos.fechaVencimiento.validacion);
    comprobar.ok(entorno.campos.idCarrera.validacion);
    comprobar.equal(entorno.elementos["habilidades-error"].hidden, false);
});

prueba("mensajes de validación se muestran como texto y conservan la entrada", async () => {
    const mensaje = '<img src=x onerror="alert(1)">';
    const entorno = prepararVacantes([respuestaSeguridad(), crearRespuesta(400, { errores: { titulo: mensaje } })]);
    await entorno.iniciar();
    await entorno.guardar();
    comprobar.equal(entorno.elementos["vacante-errores"].hijos[0].textContent, mensaje);
    comprobar.equal(entorno.elementos["vacante-errores"].hijos[0].innerHTML, undefined);
    comprobar.equal(entorno.elementos["vacante-errores"].hidden, false);
    comprobar.equal(entorno.campos.titulo.value, "Practicante web");
});

prueba("la vista de sólo lectura no registra un guardado", async () => {
    const entorno = prepararVacantes([], { soloLectura: true });
    await entorno.iniciar();
    await entorno.guardar();
    comprobar.equal(entorno.solicitudes.length, 0);
});
