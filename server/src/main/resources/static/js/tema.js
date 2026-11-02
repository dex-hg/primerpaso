"use strict";

(() => {
    const clavePreferencia = "primerpaso.tema";
    const opcionesValidas = new Set(["oscuro", "claro", "sistema"]);
    const raiz = document.documentElement;
    const consultaSistema = typeof window.matchMedia === "function"
        ? window.matchMedia("(prefers-color-scheme: dark)") : null;
    let preferenciaActual = "oscuro";

    function validarPreferencia(preferencia) {
        return opcionesValidas.has(preferencia) ? preferencia : "oscuro";
    }

    function leerPreferencia() {
        try {
            return validarPreferencia(window.localStorage.getItem(clavePreferencia));
        } catch {
            // El tema sigue funcionando si el navegador bloquea el almacenamiento.
            return "oscuro";
        }
    }

    function aplicarPreferencia(preferencia) {
        preferenciaActual = validarPreferencia(preferencia);
        const esOscuro = preferenciaActual === "oscuro"
            || (preferenciaActual === "sistema" && consultaSistema?.matches);
        raiz.setAttribute("data-bs-theme", esOscuro ? "dark" : "light");

        document.querySelector('meta[name="theme-color"]')
            ?.setAttribute("content", esOscuro ? "#101820" : "#ffffff");
        document.querySelectorAll("[data-selector-tema]").forEach((selector) => {
            selector.value = preferenciaActual;
        });
    }

    function cambiarPreferencia(evento) {
        const preferencia = validarPreferencia(evento.currentTarget.value);
        aplicarPreferencia(preferencia);
        try {
            window.localStorage.setItem(clavePreferencia, preferencia);
        } catch {
            // La elección se conserva en esta página aunque no se pueda guardar.
        }
    }

    function inicializarSelectores() {
        document.querySelectorAll("[data-selector-tema]").forEach((selector) => {
            selector.value = preferenciaActual;
            selector.disabled = false;
            selector.addEventListener("change", cambiarPreferencia);
        });
    }

    aplicarPreferencia(leerPreferencia());
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", inicializarSelectores, { once: true });
    } else {
        inicializarSelectores();
    }

    consultaSistema?.addEventListener("change", () => {
        if (preferenciaActual === "sistema") {
            aplicarPreferencia("sistema");
        }
    });
    window.addEventListener("storage", (evento) => {
        if (evento.key === clavePreferencia || evento.key === null) {
            aplicarPreferencia(validarPreferencia(evento.newValue));
        }
    });
    window.addEventListener("pageshow", (evento) => {
        if (evento.persisted) {
            aplicarPreferencia(leerPreferencia());
        }
    });
})();
