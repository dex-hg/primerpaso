"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const formulario = document.getElementById("formulario-postulacion") ||
        document.getElementById("formulario-estado-postulacion");
    if (!formulario) return;
    const sesion = window.sesionPrimerPaso;
    const panel = document.getElementById("panel");
    const estado = document.getElementById("postulacion-estado");
    const carta = formulario.elements.namedItem("cartaPresentacion");
    const contador = document.getElementById("carta-contador");
    const controles = [...formulario.querySelectorAll("input, textarea, select, button")];
    const boton = formulario.querySelector('button[type="submit"]');
    let enProceso = false;

    if (!sesion || !panel || !estado || !boton) {
        controles.forEach((control) => { control.disabled = true; });
        return;
    }

    const actualizarContador = () => {
        if (carta && contador) contador.textContent = `${carta.value.length.toLocaleString("es-PE")} / 12 000`;
    };
    if (carta) {
        carta.addEventListener("input", () => { carta.setCustomValidity(""); actualizarContador(); });
        actualizarContador();
    }

    const coincideUsuario = (usuario) => sesion.validarUsuario(usuario) &&
        String(usuario.idUsuario) === panel.dataset.idUsuario && usuario.tipoCuenta === panel.dataset.tipoCuenta &&
        (usuario.tipoCuenta !== "empresa" || (String(usuario.idEmpresa) === panel.dataset.idEmpresa &&
            usuario.rolEmpresa === panel.dataset.rolEmpresa));

    const enviar = async (ruta, metodo, datos) => {
        const controlador = new AbortController();
        const temporizador = window.setTimeout(() => controlador.abort(), 15000);
        const opciones = { credentials: "include", cache: "no-store", signal: controlador.signal,
            headers: { Accept: "application/json" } };
        try {
            const identidad = await fetch(sesion.obtenerRuta("api/sesion"), opciones);
            if (identidad.status === 401) return { estado: 401 };
            if (identidad.status !== 200) return { estado: identidad.status };
            if (!coincideUsuario(await identidad.json())) {
                document.getElementById("panel-contenido").hidden = true;
                window.location.replace(sesion.obtenerRuta("panel"));
                return { estado: 403 };
            }
            const seguridad = await fetch(sesion.obtenerRuta("api/sesion/seguridad"), opciones);
            if (seguridad.status !== 200) return { estado: seguridad.status };
            const token = (await seguridad.json()).tokenCsrf;
            if (typeof token !== "string" || token.length !== 43) return { estado: 403 };
            const respuesta = await fetch(sesion.obtenerRuta(ruta), { ...opciones, method: metodo,
                headers: { ...opciones.headers, "Content-Type": "application/json", "X-CSRF-Token": token },
                body: JSON.stringify(datos) });
            let contenido = null;
            try { contenido = await respuesta.json(); } catch { /* Conserva el texto del formulario ante respuestas vacías. */ }
            return { estado: respuesta.status, contenido };
        } finally {
            window.clearTimeout(temporizador);
        }
    };

    formulario.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        if (enProceso) return;
        if (carta) carta.setCustomValidity(carta.value.trim() ? "" : "Escribe tu carta de presentación.");
        if (!formulario.reportValidity()) return;
        const esPostulacion = Boolean(carta);
        const identificador = esPostulacion ? formulario.dataset.idVacante : formulario.dataset.idPostulacion;
        if (!/^[1-9][0-9]*$/.test(identificador || "")) {
            sesion.mostrarEstado(estado, "Recarga la página para comprobar la vacante o postulación.", true);
            return;
        }
        const datos = esPostulacion ? { idVacante: identificador, cartaPresentacion: carta.value.trim() } :
            { estado: formulario.elements.namedItem("estado").value, version: formulario.dataset.version };
        const deshabilitados = controles.map((control) => control.disabled);
        const textoBoton = boton.textContent;
        enProceso = true;
        formulario.setAttribute("aria-busy", "true");
        controles.forEach((control) => { control.disabled = true; });
        boton.textContent = esPostulacion ? "Enviando…" : "Guardando…";
        sesion.mostrarEstado(estado, esPostulacion ? "Enviando tu postulación…" : "Actualizando el estado…");
        try {
            const resultado = await enviar(esPostulacion ? "api/postulaciones" :
                `api/postulaciones/${identificador}/estado`, esPostulacion ? "POST" : "PUT", datos);
            if (resultado.estado === 401) {
                document.getElementById("panel-contenido").hidden = true;
                window.location.replace(sesion.obtenerRuta("iniciar-sesion"));
                return;
            }
            const id = resultado.contenido?.postulacion?.id;
            if ([200, 201].includes(resultado.estado) && Number.isSafeInteger(id) && id > 0) {
                window.location.assign(sesion.obtenerRuta(`panel/${panel.dataset.tipoCuenta}/postulaciones/${id}`));
                return;
            }
            const mensaje = resultado.contenido?.mensaje || "No se pudo completar la operación. Conservamos lo escrito; revisa tus postulaciones o recarga para comprobar el resultado.";
            sesion.mostrarEstado(estado, mensaje, true);
        } catch (error) {
            sesion.mostrarEstado(estado, error.name === "AbortError" ?
                "La solicitud tardó demasiado. Conservamos lo escrito; comprueba el resultado en Postulaciones antes de reenviar." :
                "No se pudo contactar al servidor. Conservamos lo escrito; comprueba el resultado antes de reenviar.", true);
        } finally {
            enProceso = false;
            formulario.setAttribute("aria-busy", "false");
            controles.forEach((control, indice) => { control.disabled = deshabilitados[indice]; });
            boton.textContent = textoBoton;
        }
    });
});
