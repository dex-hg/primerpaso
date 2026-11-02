"use strict";

const comprobar = require("node:assert/strict");
const { test: prueba } = require("node:test");
const { readFileSync: leerArchivo } = require("node:fs");
const { join: unirRuta } = require("node:path");
const { runInNewContext: ejecutarEnContexto } = require("node:vm");

const codigo = leerArchivo(unirRuta(__dirname, "../../main/resources/static/js/tema.js"), "utf8");
const clave = "primerpaso.tema";

function crearPagina(configuracion = {}) {
    const almacenamiento = configuracion.almacenamiento ?? new Map();
    if (configuracion.preferencia !== undefined) {
        almacenamiento.set(clave, configuracion.preferencia);
    }
    const atributos = new Map();
    const eventosVentana = new Map();
    const eventosDocumento = new Map();
    const eventosSistema = new Map();
    const meta = new Map();
    const selectores = Array.from({ length: 2 }, () => ({
        value: "oscuro",
        disabled: true,
        eventos: new Map(),
        addEventListener(nombre, manejador) { this.eventos.set(nombre, manejador); }
    }));
    const sistema = {
        matches: configuracion.sistemaOscuro ?? false,
        addEventListener(nombre, manejador) { eventosSistema.set(nombre, manejador); }
    };
    let domListo = configuracion.domListo ?? true;
    const documento = {
        readyState: domListo ? "complete" : "loading",
        documentElement: {
            setAttribute(nombre, valor) { atributos.set(nombre, valor); }
        },
        querySelector() { return { setAttribute(nombre, valor) { meta.set(nombre, valor); } }; },
        querySelectorAll() { return domListo ? selectores : []; },
        addEventListener(nombre, manejador) { eventosDocumento.set(nombre, manejador); }
    };
    const ventana = {
        localStorage: {
            getItem(nombre) {
                if (configuracion.lecturaBloqueada) throw new Error("Almacenamiento bloqueado");
                return almacenamiento.get(nombre) ?? null;
            },
            setItem(nombre, valor) {
                if (configuracion.escrituraBloqueada) throw new Error("Cuota excedida");
                almacenamiento.set(nombre, valor);
            }
        },
        addEventListener(nombre, manejador) { eventosVentana.set(nombre, manejador); }
    };
    if (!configuracion.sinConsultaSistema) ventana.matchMedia = () => sistema;

    ejecutarEnContexto(codigo, { document: documento, window: ventana });
    return {
        almacenamiento, atributos, meta, selectores,
        tema() { return atributos.get("data-bs-theme"); },
        cambiar(preferencia, indice = 0) {
            const selector = selectores[indice];
            selector.value = preferencia;
            selector.eventos.get("change")({ currentTarget: selector });
        },
        cargarDom() {
            domListo = true;
            eventosDocumento.get("DOMContentLoaded")();
        },
        cambiarSistema(oscuro) {
            sistema.matches = oscuro;
            eventosSistema.get("change")?.();
        },
        recibirCambio(nombre, valor) {
            eventosVentana.get("storage")({ key: nombre, newValue: valor });
        },
        restaurarDesdeHistorial() {
            eventosVentana.get("pageshow")({ persisted: true });
        }
    };
}

prueba("la primera visita usa oscuro antes de construir el contenido", () => {
    const pagina = crearPagina({ domListo: false });
    comprobar.equal(pagina.tema(), "dark");
    comprobar.equal(pagina.meta.get("content"), "#101820");
    comprobar.ok(pagina.selectores.every((selector) => selector.disabled));
    pagina.cargarDom();
    comprobar.ok(pagina.selectores.every((selector) => !selector.disabled));
});

prueba("la preferencia clara guardada se aplica desde la carga", () => {
    const pagina = crearPagina({ preferencia: "claro", sistemaOscuro: true });
    comprobar.equal(pagina.tema(), "light");
    comprobar.equal(pagina.meta.get("content"), "#ffffff");
    comprobar.ok(pagina.selectores.every((selector) => selector.value === "claro"));
});

prueba("la elección se conserva al abrir otra vista", () => {
    const primera = crearPagina();
    primera.cambiar("claro");
    const segunda = crearPagina({ almacenamiento: primera.almacenamiento });
    comprobar.equal(segunda.tema(), "light");
    comprobar.equal(segunda.selectores[0].value, "claro");
    segunda.cambiar("oscuro");
    comprobar.equal(segunda.almacenamiento.get(clave), "oscuro");
    comprobar.equal(segunda.tema(), "dark");
});

prueba("sistema sigue sus cambios sin sobrescribir la preferencia", () => {
    const pagina = crearPagina({ preferencia: "sistema" });
    comprobar.equal(pagina.tema(), "light");
    pagina.cambiarSistema(true);
    comprobar.equal(pagina.tema(), "dark");
    pagina.cambiarSistema(false);
    comprobar.equal(pagina.tema(), "light");
    comprobar.equal(pagina.almacenamiento.get(clave), "sistema");
});

prueba("el sistema no altera una elección explícita", () => {
    const pagina = crearPagina({ preferencia: "claro" });
    pagina.cambiarSistema(true);
    comprobar.equal(pagina.tema(), "light");
    pagina.cambiar("oscuro");
    pagina.cambiarSistema(false);
    comprobar.equal(pagina.tema(), "dark");
});

prueba("los valores desconocidos recuperan el tema oscuro", () => {
    for (const preferencia of ["", "dark", "null", "{invalido}"]) {
        const pagina = crearPagina({ preferencia });
        comprobar.equal(pagina.tema(), "dark");
        comprobar.equal(pagina.selectores[0].value, "oscuro");
    }
});

prueba("los controles funcionan con lectura y escritura bloqueadas", () => {
    const pagina = crearPagina({ lecturaBloqueada: true, escrituraBloqueada: true });
    comprobar.equal(pagina.tema(), "dark");
    comprobar.doesNotThrow(() => pagina.cambiar("claro"));
    comprobar.equal(pagina.tema(), "light");
    comprobar.ok(pagina.selectores.every((selector) => selector.value === "claro"));
});

prueba("todos los selectores de la misma página se mantienen sincronizados", () => {
    const pagina = crearPagina();
    pagina.cambiar("sistema", 1);
    comprobar.ok(pagina.selectores.every((selector) => selector.value === "sistema"));
});

prueba("los cambios de otra pestaña se aplican y las claves ajenas se ignoran", () => {
    const pagina = crearPagina();
    pagina.recibirCambio("otra-preferencia", "claro");
    comprobar.equal(pagina.tema(), "dark");
    pagina.recibirCambio(clave, "claro");
    comprobar.equal(pagina.tema(), "light");
    comprobar.equal(pagina.selectores[0].value, "claro");
});

prueba("borrar la preferencia en otra pestaña recupera el valor inicial", () => {
    const pagina = crearPagina({ preferencia: "claro" });
    pagina.recibirCambio(clave, null);
    comprobar.equal(pagina.tema(), "dark");
    pagina.recibirCambio(clave, "claro");
    pagina.recibirCambio(null, null);
    comprobar.equal(pagina.tema(), "dark");
});

prueba("volver desde el historial actualiza una página conservada en caché", () => {
    const pagina = crearPagina();
    pagina.almacenamiento.set(clave, "claro");
    pagina.restaurarDesdeHistorial();
    comprobar.equal(pagina.tema(), "light");
    comprobar.equal(pagina.selectores[0].value, "claro");
});

prueba("sin consulta del sistema se puede elegir un tema explícito", () => {
    const pagina = crearPagina({ sinConsultaSistema: true });
    comprobar.equal(pagina.tema(), "dark");
    comprobar.doesNotThrow(() => pagina.cambiar("sistema"));
    comprobar.equal(pagina.tema(), "light");
    pagina.cambiar("oscuro");
    comprobar.equal(pagina.tema(), "dark");
});
