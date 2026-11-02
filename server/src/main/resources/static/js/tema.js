"use strict";

(() => {
    const clavePreferencia = "primerpaso.tema";
    const preferencias = ["oscuro", "claro", "sistema"];
    const opcionesValidas = new Set(preferencias);
    const nombres = { oscuro: "Modo oscuro", claro: "Modo claro", sistema: "Tema del sistema" };
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

    function obtenerSiguientePreferencia() {
        return preferencias[(preferencias.indexOf(preferenciaActual) + 1) % preferencias.length];
    }

    function actualizarBoton(boton) {
        const siguiente = obtenerSiguientePreferencia();
        const etiqueta = `${nombres[preferenciaActual]}. Cambiar a ${nombres[siguiente].toLocaleLowerCase("es")}`;
        boton.setAttribute("data-tema-actual", preferenciaActual);
        boton.setAttribute("aria-label", etiqueta);
        boton.setAttribute("title", etiqueta);
    }

    function aplicarPreferencia(preferencia) {
        preferenciaActual = validarPreferencia(preferencia);
        const esOscuro = preferenciaActual === "oscuro"
            || (preferenciaActual === "sistema" && consultaSistema?.matches);
        raiz.setAttribute("data-bs-theme", esOscuro ? "dark" : "light");

        document.querySelector('meta[name="theme-color"]')
            ?.setAttribute("content", esOscuro ? "#101820" : "#ffffff");
        document.querySelectorAll("[data-selector-tema]").forEach(actualizarBoton);
    }

    function alternarPreferencia() {
        const preferencia = obtenerSiguientePreferencia();
        aplicarPreferencia(preferencia);
        try {
            window.localStorage.setItem(clavePreferencia, preferencia);
        } catch {
            // La elección se conserva en esta página aunque no se pueda guardar.
        }
    }

    function inicializarBotones() {
        document.querySelectorAll("[data-selector-tema]").forEach((boton) => {
            actualizarBoton(boton);
            boton.disabled = false;
            boton.addEventListener("click", alternarPreferencia);
        });
    }

    aplicarPreferencia(leerPreferencia());
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", inicializarBotones, { once: true });
    } else {
        inicializarBotones();
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
