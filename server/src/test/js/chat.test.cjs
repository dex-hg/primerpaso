"use strict";

const prueba = require("node:test");
const comprobar = require("node:assert/strict");
const { readFileSync: leerArchivo } = require("node:fs");
const { resolve: resolverRuta } = require("node:path");
const { createContext: crearContexto, runInContext: ejecutarContexto } = require("node:vm");
const carpeta = resolverRuta(__dirname, "../../main/resources/static/js");
const fuentes = ["sesion.js", "chat.js"].map((archivo) => leerArchivo(resolverRuta(carpeta, archivo), "utf8"));
const usuario = { idUsuario: 8, nombres: "Luis", apellidos: "Díaz", correo: "luis@example.test", tipoCuenta: "empresa",
    idEmpresa: 9, nombreEmpresa: "Empresa", rolEmpresa: "reclutador" };
const identificador = "4ac08510-d431-43e7-aa35-c5e12cc79ed1";
const respuesta = (estado, contenido) => ({ status: estado, json: async () => contenido });
const identidad = () => respuesta(200, usuario);
const seguridad = () => respuesta(200, { tokenCsrf: "a".repeat(43) });
const mensaje = (id = 1, cambios = {}) => ({ id, idEmisor: 8, idEnvio: identificador, nombreEmisor: "Luis Díaz",
    tipoEmisor: "empresa", contenido: "Hola\n¿Tienes disponibilidad?", fechaEnvio: "2026-11-02T18:00:00Z", ...cambios });
const detalle = (mensajes = [], cambios = {}) => ({ mensajes, puedeEnviar: true, aviso: "", hayAnteriores: false, haySiguientes: false, ...cambios });

const elemento = (datos = {}) => {
    const eventos = new Map();
    return { value: "", textContent: "", hidden: false, disabled: false, dataset: {}, atributos: {}, children: [],
        scrollHeight: 300, scrollTop: 0, clientHeight: 300, ...datos,
        addEventListener(tipo, accion) { eventos.set(tipo, accion); },
        emitir(tipo, evento = {}) { return eventos.get(tipo)?.(evento); },
        setAttribute(nombre, valor) { this.atributos[nombre] = valor; },
        setCustomValidity(valor) { this.validacion = valor; },
        append(...nodos) { this.children.push(...nodos); },
        insertBefore(nodo, siguiente) {
            const indice = siguiente ? this.children.indexOf(siguiente) : this.children.length;
            this.children.splice(indice, 0, nodo);
        }, classList: { toggle() {} }
    };
};

const preparar = (respuestas, opciones = {}) => {
    const eventos = new Map(), eventosVentana = new Map(), temporizadores = new Map();
    let numero = 0;
    const elementos = Object.fromEntries(["chat", "panel", "panel-contenido", "chat-formulario", "chat-contenido", "chat-enviar",
        "chat-anteriores", "chat-historial", "chat-mensajes", "chat-estado", "chat-aviso", "chat-vacio"]
        .map((id) => [id, elemento()]));
    elementos.panel.dataset = { idUsuario: "8", tipoCuenta: "empresa", idEmpresa: "9", rolEmpresa: "reclutador" };
    elementos.chat.dataset = { idPostulacion: "15", puedeEnviar: String(opciones.puedeEnviar ?? true) };
    elementos["chat-contenido"].value = "  Hola\n¿Tienes disponibilidad?  ";
    elementos["chat-formulario"].reportValidity = () => !elementos["chat-contenido"].validacion && elementos["chat-contenido"].value.length <= 4000;
    elementos["chat-mensajes"].children = (opciones.iniciales || []).map((id) => elemento({ dataset: { idMensaje: String(id) } }));
    const documento = { visibilityState: "visible", body: { dataset: { rutaBase: opciones.contexto || "/" } },
        getElementById: (id) => elementos[id] || null, createElement: () => elemento(),
        addEventListener: (tipo, accion) => eventos.set(tipo, [...(eventos.get(tipo) || []), accion]) };
    const destinos = [], solicitudes = [], pendientes = [...respuestas];
    const ventana = { crypto: { randomUUID: () => identificador },
        setTimeout: (accion, demora) => { temporizadores.set(++numero, { accion, demora }); return numero; },
        clearTimeout: (id) => temporizadores.delete(id), addEventListener: (tipo, accion) => eventosVentana.set(tipo, accion),
        location: { origin: "http://localhost:8080", hostname: "localhost", port: "8080", replace: (ruta) => destinos.push(ruta) } };
    const contexto = crearContexto({ document: documento, window: ventana, URL, AbortController,
        fetch: async (url, opciones) => {
            solicitudes.push({ url, ...opciones });
            comprobar.ok(pendientes.length, `Solicitud inesperada: ${url}`);
            const siguiente = pendientes.shift();
            if (siguiente instanceof Error) throw siguiente;
            return typeof siguiente === "function" ? siguiente() : siguiente;
        } });
    fuentes.forEach((fuente) => ejecutarContexto(fuente, contexto));
    eventos.get("DOMContentLoaded").forEach((accion) => accion());
    return { elementos, documento, destinos, solicitudes, temporizadores, eventosVentana,
        enviar: () => elementos["chat-formulario"].emitir("submit", { preventDefault() {} }),
        esperarActualizacion: async () => {
            const [id, temporizador] = [...temporizadores].sort((primero, segundo) => primero[1].demora - segundo[1].demora)[0];
            temporizadores.delete(id);
            temporizador.accion();
            await new Promise(setImmediate);
        },
        anteriores: () => elementos["chat-anteriores"].emitir("click") };
};

prueba("envía con identidad, CSRF y contexto; representa HTML como texto y conserva párrafos", async () => {
    const recibido = mensaje(1, { contenido: "<script>alert('prueba')</script>\nSegunda línea" });
    const entorno = preparar([identidad(), seguridad(), respuesta(201, { mensaje: recibido }), identidad(), respuesta(200, detalle([recibido]))],
        { contexto: "/primerpaso/" });
    await entorno.enviar();
    const solicitud = entorno.solicitudes.find((item) => item.method === "POST");
    comprobar.equal(solicitud.url, "/primerpaso/api/chat/postulaciones/15/mensajes");
    comprobar.equal(solicitud.headers["X-CSRF-Token"], "a".repeat(43));
    comprobar.equal(solicitud.credentials, "include");
    comprobar.equal(solicitud.cache, "no-store");
    comprobar.deepEqual(JSON.parse(solicitud.body), { idEnvio: identificador, contenido: "Hola\n¿Tienes disponibilidad?" });
    const nodo = entorno.elementos["chat-mensajes"].children[0];
    comprobar.equal(nodo.children[1].textContent, recibido.contenido);
    comprobar.equal(nodo.children[1].innerHTML, undefined);
    comprobar.equal(entorno.elementos["chat-contenido"].value, "");
});

prueba("un fallo de red conserva el borrador y el reintento usa el mismo id de envío", async () => {
    const entorno = preparar([identidad(), seguridad(), new Error("Sin conexión"), identidad(), seguridad(),
        respuesta(200, { mensaje: mensaje() }), identidad(), respuesta(200, detalle([mensaje()]))]);
    const original = entorno.elementos["chat-contenido"].value;
    await entorno.enviar();
    comprobar.equal(entorno.elementos["chat-contenido"].value, original);
    comprobar.equal(entorno.elementos["chat-enviar"].disabled, false);
    await entorno.enviar();
    const envios = entorno.solicitudes.filter((item) => item.method === "POST");
    comprobar.equal(envios.length, 2);
    comprobar.equal(envios[0].body, envios[1].body);
    comprobar.equal(entorno.elementos["chat-mensajes"].children.length, 1);
});

prueba("la actualización automática confirma un envío cuya respuesta se perdió y limpia el borrador", async () => {
    const entorno = preparar([identidad(), seguridad(), new Error("Sin conexión"), identidad(), respuesta(200, detalle([mensaje()]))]);
    await entorno.enviar();
    await entorno.esperarActualizacion();
    comprobar.equal(entorno.elementos["chat-contenido"].value, "");
    comprobar.equal(entorno.solicitudes.filter((item) => item.method === "POST").length, 1);
});

prueba("no salta mensajes concurrentes al recibir la confirmación de uno propio posterior", async () => {
    const entorno = preparar([identidad(), seguridad(), respuesta(201, { mensaje: mensaje(12) }), identidad(),
        respuesta(200, detalle([mensaje(11, { idEmisor: 7, idEnvio: "otro", contenido: "Respuesta" }), mensaje(12)]))], { iniciales: [10] });
    await entorno.enviar();
    comprobar.equal(entorno.solicitudes.at(-1).url, "/api/chat/postulaciones/15?despuesDe=10");
    comprobar.deepEqual(entorno.elementos["chat-mensajes"].children.map((nodo) => Number(nodo.dataset.idMensaje)), [10, 11, 12]);
});

prueba("un cambio de cuenta detiene el envío y oculta el contenido de la sesión anterior", async () => {
    const entorno = preparar([respuesta(200, { ...usuario, idUsuario: 10 })]);
    await entorno.enviar();
    comprobar.deepEqual(entorno.destinos, ["/panel"]);
    comprobar.equal(entorno.elementos["panel-contenido"].hidden, true);
    comprobar.equal(entorno.solicitudes.length, 1);
});

prueba("una sesión vencida vuelve al acceso sin enviar datos", async () => {
    const entorno = preparar([respuesta(401)]);
    await entorno.enviar();
    comprobar.deepEqual(entorno.destinos, ["/iniciar-sesion"]);
    comprobar.equal(entorno.solicitudes.length, 1);
});

prueba("el estado de solo lectura impide enviar y conserva el texto al cambiar durante la conversación", async () => {
    const entorno = preparar([identidad(), respuesta(200, detalle([], { puedeEnviar: false, aviso: "La postulación ya no está seleccionada." }))]);
    await entorno.esperarActualizacion();
    await entorno.enviar();
    comprobar.equal(entorno.elementos["chat-contenido"].disabled, true);
    comprobar.match(entorno.elementos["chat-aviso"].textContent, /ya no está seleccionada/);
    comprobar.equal(entorno.solicitudes.length, 2);
});

prueba("un conflicto conserva el borrador y suspende el envío hasta comprobar el estado", async () => {
    const entorno = preparar([identidad(), seguridad(), respuesta(409, { mensaje: "La postulación cambió." })]);
    await entorno.enviar();
    comprobar.equal(entorno.elementos["chat-enviar"].disabled, true);
    comprobar.match(entorno.elementos["chat-contenido"].value, /Tienes disponibilidad/);
    comprobar.match(entorno.elementos["chat-estado"].textContent, /Conservamos/);
});

prueba("carga páginas anteriores sin duplicar mensajes ni modificar el cursor de mensajes nuevos", async () => {
    const entorno = preparar([identidad(), respuesta(200, detalle([mensaje(8), mensaje(9)], { hayAnteriores: false })),
        identidad(), respuesta(200, detalle([]))], { iniciales: [10] });
    await entorno.anteriores();
    comprobar.equal(entorno.solicitudes[1].url, "/api/chat/postulaciones/15?antesDe=10");
    comprobar.deepEqual(entorno.elementos["chat-mensajes"].children.map((nodo) => Number(nodo.dataset.idMensaje)), [8, 9, 10]);
    await entorno.esperarActualizacion();
    comprobar.equal(entorno.solicitudes.at(-1).url, "/api/chat/postulaciones/15?despuesDe=10");
});

prueba("no envía mensajes vacíos o excesivos y detiene la actualización al salir de la página", async () => {
    const entorno = preparar([]);
    for (const valor of [" \n\t ", "a".repeat(4001)]) {
        entorno.elementos["chat-contenido"].value = valor;
        await entorno.enviar();
    }
    comprobar.equal(entorno.solicitudes.length, 0);
    entorno.eventosVentana.get("pagehide")();
    comprobar.equal(entorno.temporizadores.size, 0);
});

prueba("un doble clic durante el envío produce una sola escritura", async () => {
    let resolver;
    const pendiente = new Promise((completar) => { resolver = completar; });
    const entorno = preparar([() => pendiente, seguridad(), respuesta(201, { mensaje: mensaje() }), identidad(), respuesta(200, detalle([mensaje()]))]);
    const primero = entorno.enviar();
    await entorno.enviar();
    resolver(identidad());
    await primero;
    comprobar.equal(entorno.solicitudes.filter((item) => item.method === "POST").length, 1);
});

prueba("posiciona el historial al final después de que el panel revalida la sesión y vuelve a ser visible", async () => {
    const entorno = preparar([identidad(), respuesta(200, detalle([]))], { iniciales: [10] });
    entorno.elementos["panel-contenido"].hidden = true;
    const tomarTemporizador = (demora) => {
        const [id, temporizador] = [...entorno.temporizadores].find(([, valor]) => valor.demora === demora);
        entorno.temporizadores.delete(id);
        temporizador.accion();
    };
    tomarTemporizador(100);
    comprobar.equal(entorno.elementos["chat-historial"].scrollTop, 0);
    comprobar.equal(entorno.solicitudes.length, 0);
    entorno.elementos["panel-contenido"].hidden = false;
    tomarTemporizador(250);
    await new Promise(setImmediate);
    comprobar.equal(entorno.elementos["chat-historial"].scrollTop, 300);
    comprobar.equal(entorno.solicitudes.at(-1).url, "/api/chat/postulaciones/15?despuesDe=10");
});

prueba("recibe mensajes nuevos automáticamente y continúa consultando sin controles de actualización manual", async () => {
    const primero = mensaje(11, { idEmisor: 7, idEnvio: "respuesta-1", contenido: "Tengo disponibilidad." });
    const segundo = mensaje(12, { idEmisor: 7, idEnvio: "respuesta-2", contenido: "Podemos coordinar el horario." });
    const entorno = preparar([identidad(), respuesta(200, detalle([primero])), identidad(), respuesta(200, detalle([segundo]))],
        { iniciales: [10] });
    await entorno.esperarActualizacion();
    comprobar.deepEqual(entorno.elementos["chat-mensajes"].children.map((nodo) => Number(nodo.dataset.idMensaje)), [10, 11]);
    comprobar.ok([...entorno.temporizadores.values()].some((temporizador) => temporizador.demora === 5000));
    await entorno.esperarActualizacion();
    comprobar.deepEqual(entorno.elementos["chat-mensajes"].children.map((nodo) => Number(nodo.dataset.idMensaje)), [10, 11, 12]);
    comprobar.equal(entorno.solicitudes.filter((solicitud) => solicitud.method === "POST").length, 0);
});
