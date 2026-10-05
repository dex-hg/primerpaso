"use strict";

const prueba = require("node:test");
const comprobar = require("node:assert/strict");
const { readFileSync: leerArchivo } = require("node:fs");
const { resolve: resolverRuta } = require("node:path");
const { createContext: crearContexto, runInContext: ejecutarContexto } = require("node:vm");

const carpetaScripts = resolverRuta(__dirname, "../../main/resources/static/js");
const codigoSesion = leerArchivo(resolverRuta(carpetaScripts, "sesion.js"), "utf8");
const codigoPanel = leerArchivo(resolverRuta(carpetaScripts, "panel.js"), "utf8");
const postulante = {
    idUsuario: 7, nombres: "Lucía", apellidos: "Ortiz", correo: "lucia@example.test",
    tipoCuenta: "postulante", idEmpresa: null, nombreEmpresa: null, rolEmpresa: null
};
const empresa = {
    ...postulante, idUsuario: 8, tipoCuenta: "empresa", idEmpresa: 3,
    nombreEmpresa: "Empresa de prueba", rolEmpresa: "administrador"
};

const crearRespuesta = (estado, contenido = null) => ({
    status: estado,
    json: async () => contenido
});

const crearEventos = () => {
    const escuchas = new Map();
    return {
        addEventListener(tipo, escucha) {
            if (!escuchas.has(tipo)) escuchas.set(tipo, []);
            escuchas.get(tipo).push(escucha);
        },
        async emitir(tipo, evento = {}) {
            await Promise.all((escuchas.get(tipo) || []).map((escucha) => escucha(evento)));
            // Los listeners de arranque y restauración disparan tareas sin devolver su promesa.
            await new Promise((resolver) => setImmediate(resolver));
        }
    };
};

const crearElemento = (dataset = {}) => {
    const clases = new Set();
    return {
        ...crearEventos(), dataset, hidden: false, disabled: false, textContent: "",
        atributos: {},
        setAttribute(nombre, valor) { this.atributos[nombre] = valor; },
        classList: {
            toggle(nombre, activo) { if (activo) clases.add(nombre); else clases.delete(nombre); },
            contains(nombre) { return clases.has(nombre); }
        }
    };
};

const prepararPanel = (respuestas, opciones = {}) => {
    const usuarioVista = opciones.usuarioVista || postulante;
    const datos = { tipoCuenta: usuarioVista.tipoCuenta, idUsuario: String(usuarioVista.idUsuario) };
    if (usuarioVista.tipoCuenta === "empresa") {
        datos.idEmpresa = String(usuarioVista.idEmpresa);
        datos.rolEmpresa = usuarioVista.rolEmpresa;
    }
    const elementos = {
        panel: crearElemento(datos),
        "panel-contenido": crearElemento(),
        "panel-estado": crearElemento(),
        "panel-reintentar": crearElemento(),
        "cerrar-sesion": crearElemento()
    };
    elementos["panel-reintentar"].hidden = true;
    const documento = {
        ...crearEventos(), visibilityState: "visible",
        body: { dataset: { rutaBase: opciones.rutaBase || "/" } },
        getElementById: (identificador) => elementos[identificador] || null
    };
    const destinos = [];
    const ventana = {
        ...crearEventos(), setTimeout, clearTimeout,
        location: {
            origin: "http://localhost:8080", hostname: "localhost", port: "8080",
            replace: (destino) => destinos.push(destino)
        }
    };
    const solicitudes = [];
    const pendientes = [...respuestas];
    const contexto = crearContexto({
        document: documento, window: ventana, URL, AbortController,
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
    ejecutarContexto(codigoPanel, contexto, { filename: "panel.js" });
    return {
        elementos, documento, ventana, destinos, solicitudes,
        iniciar: () => documento.emitir("DOMContentLoaded")
    };
};

prueba("la identidad SSR permanece oculta hasta revalidar una sesión vigente", async () => {
    let responder;
    const entorno = prepararPanel([() => new Promise((resolver) => { responder = resolver; })]);
    await entorno.iniciar();
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
    comprobar.equal(entorno.elementos["cerrar-sesion"].disabled, true);
    responder(crearRespuesta(200, postulante));
    await new Promise((resolver) => setImmediate(resolver));
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, false);
    comprobar.equal(entorno.elementos.panel.atributos["aria-busy"], "false");
    comprobar.equal(entorno.elementos["cerrar-sesion"].disabled, false);
    comprobar.deepEqual(entorno.destinos, []);
    comprobar.equal(entorno.solicitudes[0].credentials, "include");
    comprobar.equal(entorno.solicitudes[0].cache, "no-store");
});

prueba("una sesión expirada oculta la identidad y vuelve al acceso", async () => {
    const entorno = prepararPanel([crearRespuesta(401)]);
    await entorno.iniciar();
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
    comprobar.deepEqual(entorno.destinos, ["/iniciar-sesion"]);
});

prueba("un servicio indisponible permite reintentar y recupera el panel", async () => {
    const entorno = prepararPanel([crearRespuesta(503), crearRespuesta(200, postulante)]);
    await entorno.iniciar();
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
    comprobar.equal(entorno.elementos["panel-reintentar"].hidden, false);
    comprobar.equal(entorno.elementos["panel-estado"].classList.contains("is-error"), true);
    await entorno.elementos["panel-reintentar"].emitir("click");
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, false);
    comprobar.equal(entorno.elementos["panel-reintentar"].hidden, true);
    comprobar.equal(entorno.elementos["panel-estado"].classList.contains("is-error"), false);
    comprobar.equal(entorno.solicitudes.length, 2);
});

for (const escenario of [
    { nombre: "tipo de cuenta", vista: postulante, sesion: empresa },
    { nombre: "identidad del usuario", vista: postulante, sesion: { ...postulante, idUsuario: 19 } },
    { nombre: "empresa asociada", vista: empresa, sesion: { ...empresa, idEmpresa: 11 } },
    { nombre: "rol de membresía", vista: empresa, sesion: { ...empresa, rolEmpresa: "reclutador" } }
]) {
    prueba(`un cambio de ${escenario.nombre} recarga el panel sin revelar los datos previos`, async () => {
        const entorno = prepararPanel([crearRespuesta(200, escenario.sesion)], {
            usuarioVista: escenario.vista
        });
        await entorno.iniciar();
        comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
        comprobar.deepEqual(entorno.destinos, ["/panel"]);
    });
}

prueba("el cierre obtiene CSRF antes de borrar la sesión y usa el contexto de la aplicación", async () => {
    const entorno = prepararPanel([
        crearRespuesta(200, empresa), crearRespuesta(200, { tokenCsrf: "token-de-prueba" }),
        crearRespuesta(204)
    ], { usuarioVista: empresa, rutaBase: "/primerpaso/" });
    await entorno.iniciar();
    await entorno.elementos["cerrar-sesion"].emitir("click");
    comprobar.deepEqual(entorno.solicitudes.map(({ url, method }) => ({ url, metodo: method })), [
        { url: "http://localhost:8080/primerpaso/api/sesion", metodo: "GET" },
        { url: "http://localhost:8080/primerpaso/api/sesion/seguridad", metodo: "GET" },
        { url: "http://localhost:8080/primerpaso/api/sesion", metodo: "DELETE" }
    ]);
    comprobar.equal(entorno.solicitudes[2].headers["X-CSRF-Token"], "token-de-prueba");
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
    comprobar.deepEqual(entorno.destinos, ["/primerpaso/iniciar-sesion"]);
    comprobar.equal(entorno.ventana.sesionPrimerPaso.obtenerRuta("panel"), "/primerpaso/panel");
});

prueba("un token CSRF ausente evita la solicitud DELETE y deja disponible el reintento", async () => {
    const entorno = prepararPanel([crearRespuesta(200, postulante), crearRespuesta(200, {})]);
    await entorno.iniciar();
    await entorno.elementos["cerrar-sesion"].emitir("click");
    comprobar.equal(entorno.solicitudes.length, 2);
    comprobar.ok(entorno.solicitudes.every((solicitud) => solicitud.method === "GET"));
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
    comprobar.equal(entorno.elementos["panel-reintentar"].hidden, false);
    comprobar.deepEqual(entorno.destinos, []);
});

prueba("restaurar desde bfcache revalida la sesión antes de mostrar datos privados", async () => {
    const entorno = prepararPanel([crearRespuesta(200, postulante), crearRespuesta(401)]);
    await entorno.iniciar();
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, false);
    await entorno.ventana.emitir("pagehide");
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
    await entorno.ventana.emitir("pageshow", { persisted: true });
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
    comprobar.deepEqual(entorno.destinos, ["/iniciar-sesion"]);
    comprobar.equal(entorno.solicitudes.length, 2);
});

prueba("volver a una pestaña revalida un rol empresarial cambiado", async () => {
    const entorno = prepararPanel([
        crearRespuesta(200, empresa), crearRespuesta(200, { ...empresa, rolEmpresa: "reclutador" })
    ], { usuarioVista: empresa });
    await entorno.iniciar();
    entorno.documento.visibilityState = "hidden";
    await entorno.documento.emitir("visibilitychange");
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
    entorno.documento.visibilityState = "visible";
    await entorno.documento.emitir("visibilitychange");
    comprobar.deepEqual(entorno.destinos, ["/panel"]);
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
});

prueba("un rol desconocido y los errores de conexión nunca revelan el panel", async () => {
    const entorno = prepararPanel([
        crearRespuesta(200, { ...empresa, rolEmpresa: "superusuario" }), new Error("Sin conexión")
    ], { usuarioVista: empresa });
    await entorno.iniciar();
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
    comprobar.equal(entorno.elementos["panel-reintentar"].hidden, false);
    await entorno.elementos["panel-reintentar"].emitir("click");
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
    comprobar.equal(entorno.elementos["panel-estado"].classList.contains("is-error"), true);
    comprobar.equal(entorno.elementos["panel-reintentar"].disabled, false);
    comprobar.deepEqual(entorno.destinos, []);
});
