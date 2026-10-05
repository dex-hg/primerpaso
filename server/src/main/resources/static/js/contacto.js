"use strict";

(() => {
    document.addEventListener("DOMContentLoaded", () => {
        const formulario = document.getElementById("contact-form");
        const estado = document.getElementById("contact-status");
        if (!(formulario instanceof HTMLFormElement) || !estado) return;

        const boton = formulario.querySelector('button[type="submit"]');
        if (!(boton instanceof HTMLButtonElement)) return;

        const campos = [...formulario.querySelectorAll("input[required], textarea[required]")];
        const validarCampos = () => {
            campos.forEach((campo) => {
                campo.setCustomValidity(campo.value.trim() ? "" : "Completa este campo.");
            });
        };

        // Esta vista comprueba datos localmente mientras el envío queda pendiente.
        formulario.addEventListener("submit", (evento) => {
            evento.preventDefault();
            campos.forEach((campo) => { campo.value = campo.value.trim(); });
            validarCampos();
            formulario.classList.add("was-validated");
            const esValido = formulario.checkValidity();
            estado.classList.toggle("is-error", !esValido);
            estado.textContent = esValido ?
                "Los datos tienen un formato válido. El envío de mensajes aún no está disponible." :
                "Revisa los campos indicados. El envío de mensajes aún no está disponible.";
            if (!esValido) formulario.reportValidity();
        });

        formulario.addEventListener("input", validarCampos);
        formulario.addEventListener("change", validarCampos);
        validarCampos();
        boton.disabled = false;
    });
})();
