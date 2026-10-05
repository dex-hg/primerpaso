"use strict";

const mostrarEstadoAutenticacion = (formulario, mensaje, esError = false) => {
    const estado = formulario.querySelector(".auth-form-status");
    if (!estado) return;
    estado.textContent = mensaje;
    estado.classList.toggle("is-error", esError);
};

const comprobarConfirmacionContrasena = (confirmacion) => {
    const selector = confirmacion.dataset.confirmPassword;
    const contrasena = selector ? document.querySelector(selector) : null;
    const coincide = !confirmacion.value || confirmacion.value === contrasena?.value;
    confirmacion.setCustomValidity(coincide ? "" : "Las contraseñas no coinciden.");
};

const iniciarConfirmacionesContrasena = () => {
    document.querySelectorAll("[data-confirm-password]").forEach((confirmacion) => {
        const contrasena = document.querySelector(confirmacion.dataset.confirmPassword);
        const comprobar = () => comprobarConfirmacionContrasena(confirmacion);
        confirmacion.addEventListener("input", comprobar);
        contrasena?.addEventListener("input", comprobar);
    });
};

const encontrarCampoInvalido = (panel) => {
    panel.querySelectorAll("[data-confirm-password]").forEach(comprobarConfirmacionContrasena);
    return [...panel.querySelectorAll("input, select, textarea")]
        .find((campo) => !campo.disabled && !campo.checkValidity());
};

const mostrarPasoRegistro = (formulario, numeroPaso) => {
    formulario.querySelectorAll("[data-step-panel]").forEach((panel) => {
        panel.hidden = Number(panel.dataset.stepPanel) !== numeroPaso;
    });
    formulario.closest(".registration-shell")?.querySelectorAll("[data-step-indicator]")
        .forEach((indicador) => {
            const paso = Number(indicador.dataset.stepIndicator);
            indicador.classList.toggle("is-active", paso === numeroPaso);
            indicador.classList.toggle("is-complete", paso < numeroPaso);
            if (paso === numeroPaso) indicador.setAttribute("aria-current", "step");
            else indicador.removeAttribute("aria-current");
        });
    formulario.dataset.currentStep = String(numeroPaso);
    formulario.classList.remove("was-validated");
    document.querySelector(".registration-heading")?.scrollIntoView({ behavior: "smooth", block: "start" });
};

const indicarCampoInvalido = (formulario, panel, campo) => {
    mostrarPasoRegistro(formulario, Number(panel.dataset.stepPanel));
    formulario.classList.add("was-validated");
    mostrarEstadoAutenticacion(formulario, "Revisa los campos indicados.", true);
    campo.focus();
    campo.reportValidity();
};

const iniciarFormulariosPorPasos = () => {
    document.querySelectorAll("[data-multi-step]").forEach((formulario) => {
        const paneles = [...formulario.querySelectorAll("[data-step-panel]")];
        formulario.dataset.currentStep = "1";

        formulario.querySelectorAll("[data-next-step]").forEach((boton) => {
            boton.addEventListener("click", () => {
                if (formulario.dataset.enviando === "true") return;
                const paso = Number(formulario.dataset.currentStep || "1");
                const panel = formulario.querySelector(`[data-step-panel="${paso}"]`);
                if (!panel) return;
                window.validarCamposRegistro?.(formulario);
                const campo = encontrarCampoInvalido(panel);
                if (campo) return indicarCampoInvalido(formulario, panel, campo);
                mostrarEstadoAutenticacion(formulario, "");
                mostrarPasoRegistro(formulario, Math.min(paso + 1, paneles.length));
            });
        });

        formulario.querySelectorAll("[data-previous-step]").forEach((boton) => {
            boton.addEventListener("click", () => {
                if (formulario.dataset.enviando === "true") return;
                mostrarEstadoAutenticacion(formulario, "");
                mostrarPasoRegistro(formulario, Math.max(Number(formulario.dataset.currentStep) - 1, 1));
            });
        });

        formulario.addEventListener("submit", async (evento) => {
            evento.preventDefault();
            if (formulario.dataset.enviando === "true" || formulario.dataset.registrado === "true") return;
            window.validarCamposRegistro?.(formulario);
            // Revisa todos los pasos, incluso los que el usuario ya completó.
            for (const panel of paneles) {
                const campo = encontrarCampoInvalido(panel);
                if (campo) return indicarCampoInvalido(formulario, panel, campo);
            }
            if (typeof window.registrarCuenta !== "function") {
                mostrarEstadoAutenticacion(formulario, "No se pudo cargar el registro. Recarga la página.", true);
                return;
            }
            await window.registrarCuenta(formulario);
        });
    });
};

document.addEventListener("DOMContentLoaded", () => {
    iniciarConfirmacionesContrasena();
    iniciarFormulariosPorPasos();
    document.querySelectorAll("[data-current-year]").forEach((elemento) => {
        elemento.textContent = String(new Date().getFullYear());
    });
});
