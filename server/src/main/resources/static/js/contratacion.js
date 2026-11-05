"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const panel = document.getElementById("panel");
    const estado = document.getElementById("contratacion-estado");
    const sesion = window.sesionPrimerPaso;
    const formularios = [...document.querySelectorAll("form[data-contratacion-ruta]")];
    if (!panel || !estado || !sesion || formularios.length === 0) return;

    let enProceso = false;
    const controles = formularios.flatMap((formulario) => [...formulario.elements]);

    const coincideUsuario = (usuario) => sesion.validarUsuario(usuario) &&
        String(usuario.idUsuario) === panel.dataset.idUsuario && usuario.tipoCuenta === panel.dataset.tipoCuenta &&
        (usuario.tipoCuenta !== "empresa" || (String(usuario.idEmpresa) === panel.dataset.idEmpresa &&
            usuario.rolEmpresa === panel.dataset.rolEmpresa));

    const salir = (ruta) => {
        document.getElementById("panel-contenido").hidden = true;
        window.location.replace(sesion.obtenerRuta(ruta));
    };

    const mostrarError = (mensaje) => {
        sesion.mostrarEstado(estado, mensaje, true);
        estado.focus();
    };

    const prepararDatos = (formulario, boton) => {
        const datos = Object.fromEntries(new FormData(formulario, boton));
        if ("aceptada" in datos) datos.aceptada = datos.aceptada === "true";
        if ("postulacionesAFinalizar" in datos) datos.postulacionesAFinalizar = Number(datos.postulacionesAFinalizar);
        if ("venceEn" in datos) {
            const vencimiento = new Date(datos.venceEn + "-05:00");
            if (!Number.isFinite(vencimiento.getTime())) return null;
            datos.venceEn = vencimiento.toISOString();
            datos.condiciones = datos.condiciones.trim();
        }
        if ("motivo" in datos) datos.motivo = datos.motivo.trim();
        return datos;
    };

    const enviar = async (ruta, datos) => {
        const identidad = await sesion.solicitarSesion();
        if (identidad.estado === 401) return salir("iniciar-sesion");
        if (identidad.estado !== 200) return mostrarError("No se pudo comprobar tu sesión. Inténtalo nuevamente.");
        if (!coincideUsuario(identidad.contenido)) return salir("panel");

        const controlador = new AbortController();
        const temporizador = window.setTimeout(() => controlador.abort(), 15000);
        const opciones = {
            headers: { Accept: "application/json" }, credentials: "include", cache: "no-store", signal: controlador.signal
        };
        try {
            const seguridad = await fetch(sesion.obtenerRuta("api/sesion/seguridad"), opciones);
            if (seguridad.status === 401) return salir("iniciar-sesion");
            const token = seguridad.ok ? (await seguridad.json()).tokenCsrf : null;
            if (typeof token !== "string" || token.length !== 43) {
                return mostrarError("No se pudo validar la solicitud. Recarga la página antes de reintentar.");
            }
            const respuesta = await fetch(sesion.obtenerRuta(ruta), {
                ...opciones, method: "POST",
                headers: { ...opciones.headers, "Content-Type": "application/json", "X-CSRF-Token": token },
                body: JSON.stringify(datos)
            });
            if (respuesta.status === 401) return salir("iniciar-sesion");
            if (respuesta.ok) {
                window.location.reload();
                return;
            }
            const contenido = await respuesta.json().catch(() => null);
            mostrarError(contenido?.mensaje || "No se pudo completar la operación. Revisa el estado antes de reintentar.");
        } finally {
            window.clearTimeout(temporizador);
        }
    };

    formularios.forEach((formulario) => formulario.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        if (enProceso || !formulario.reportValidity()) return;
        const datos = prepararDatos(formulario, evento.submitter);
        if (!datos) {
            mostrarError("Ingresa un plazo de respuesta válido en horario de Lima.");
            return;
        }
        enProceso = true;
        const deshabilitados = controles.map((control) => control.disabled);
        controles.forEach((control) => { control.disabled = true; });
        formulario.setAttribute("aria-busy", "true");
        sesion.mostrarEstado(estado, "Guardando el cambio…");
        try {
            await enviar(formulario.dataset.contratacionRuta, datos);
        } catch (error) {
            mostrarError(error.name === "AbortError"
                ? "La solicitud tardó demasiado. Recarga para comprobar el resultado antes de reintentar."
                : "No se pudo contactar al servidor. Comprueba el resultado antes de reintentar.");
        } finally {
            formulario.setAttribute("aria-busy", "false");
            controles.forEach((control, indice) => { control.disabled = deshabilitados[indice]; });
            enProceso = false;
        }
    }));
});
