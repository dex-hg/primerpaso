"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const estado = document.getElementById("vacantes-estado");
    const sesion = window.sesionPrimerPaso;
    const panel = document.getElementById("panel");
    if (!estado || !sesion || !panel) return;
    const formulario = document.getElementById("formulario-vacante");
    const errores = document.getElementById("vacante-errores");
    const acciones = [...panel.querySelectorAll("[data-accion]")];
    let enProceso = false;
    let cambiosPendientes = false;
    let confirmacionActiva = null;

    const mostrarEstado = (mensaje, esError = false) => {
        estado.textContent = mensaje;
        estado.classList.toggle("is-error", esError);
    };
    const cerrarConfirmacion = (restaurarFoco = true) => {
        if (!confirmacionActiva) return;
        const { contenedor, origen } = confirmacionActiva;
        contenedor.remove();
        confirmacionActiva = null;
        if (restaurarFoco) origen.focus();
    };
    const mostrarConfirmacion = (origen, pregunta, accion, alConfirmar) => {
        if (confirmacionActiva) {
            confirmacionActiva.cancelar.focus();
            return;
        }
        const contenedor = document.createElement("section");
        contenedor.id = "vacante-confirmacion";
        contenedor.className = "vacante-confirmacion";
        contenedor.setAttribute("role", "alert");
        const texto = document.createElement("p");
        texto.textContent = pregunta;
        const botones = document.createElement("div");
        botones.className = "vacante-confirmacion-acciones";
        const cancelar = document.createElement("button");
        cancelar.id = "vacante-cancelar";
        cancelar.type = "button";
        cancelar.className = "btn btn-soft";
        cancelar.textContent = "Cancelar";
        const confirmar = document.createElement("button");
        confirmar.id = "vacante-confirmar";
        confirmar.type = "button";
        confirmar.className = "btn btn-primary-custom";
        confirmar.textContent = accion === "publicar" ? "Confirmar publicación" : "Confirmar cierre";
        cancelar.addEventListener("click", () => cerrarConfirmacion());
        confirmar.addEventListener("click", async () => {
            if (enProceso) return;
            cerrarConfirmacion(false);
            await alConfirmar();
        });
        contenedor.addEventListener("keydown", (evento) => {
            if (evento.key === "Escape") {
                evento.preventDefault();
                cerrarConfirmacion();
            }
        });
        botones.appendChild(cancelar);
        botones.appendChild(confirmar);
        contenedor.appendChild(texto);
        contenedor.appendChild(botones);
        estado.after(contenedor);
        confirmacionActiva = { contenedor, origen, cancelar };
        contenedor.scrollIntoView({ block: "nearest", behavior: "smooth" });
        cancelar.focus();
    };
    const limpiarErrores = () => {
        if (!errores) return;
        errores.replaceChildren();
        errores.hidden = true;
    };
    const mostrarErrores = (contenido) => {
        limpiarErrores();
        if (!errores || !contenido || typeof contenido.errores !== "object" || contenido.errores === null) return;
        Object.values(contenido.errores).forEach((mensaje) => {
            if (typeof mensaje !== "string") return;
            const elemento = document.createElement("li");
            elemento.textContent = mensaje;
            errores.appendChild(elemento);
        });
        errores.hidden = !errores.childElementCount;
    };
    const mensajeError = (codigo, contenido) => {
        if (codigo === 409) return "La vacante cambió en otra operación o ya no admite esta acción. Tus datos siguen en el formulario; revisa la versión actual antes de continuar.";
        if (codigo === 503) return "El servicio no está disponible. Tus datos siguen en el formulario. Inténtalo más tarde.";
        if (codigo === 403) return "La solicitud no está autorizada. Comprueba tu sesión y recarga la página para continuar.";
        if (codigo === 404) return "La vacante ya no está disponible para tu empresa. Consulta la lista de vacantes.";
        if (contenido && typeof contenido.mensaje === "string" && contenido.mensaje.trim()) return contenido.mensaje;
        if (codigo === 400) return "Revisa los campos indicados antes de guardar la vacante.";
        return "No se pudo completar la operación. Tus datos siguen en el formulario.";
    };
    const leerRespuesta = async (respuesta) => {
        let contenido = null;
        try { contenido = await respuesta.json(); } catch { /* Algunas respuestas no contienen JSON. */ }
        return { estado: respuesta.status, contenido };
    };
    const solicitarCambio = async (ruta, metodo, datos) => {
        const controlador = new AbortController();
        const temporizador = window.setTimeout(() => controlador.abort(), 15000);
        const opciones = {
            method: "GET", headers: { Accept: "application/json" }, credentials: "include",
            cache: "no-store", signal: controlador.signal
        };
        try {
            const seguridad = await leerRespuesta(await fetch(sesion.obtenerRuta("api/sesion/seguridad"), opciones));
            if (seguridad.estado !== 200) return seguridad;
            const token = seguridad.contenido?.tokenCsrf;
            if (typeof token !== "string" || !token) return { estado: 403, contenido: null };
            return await leerRespuesta(await fetch(sesion.obtenerRuta(ruta), {
                ...opciones, method: metodo,
                headers: { ...opciones.headers, "Content-Type": "application/json", "X-CSRF-Token": token },
                body: JSON.stringify(datos)
            }));
        } finally {
            window.clearTimeout(temporizador);
        }
    };
    const ejecutarCambio = async (ruta, metodo, datos, mensaje, alCompletar) => {
        if (enProceso || document.getElementById("panel-contenido")?.hidden) return;
        enProceso = true;
        limpiarErrores();
        mostrarEstado(mensaje);
        const controles = [...panel.querySelectorAll("input, select, textarea, button"), document.getElementById("cerrar-sesion")].filter(Boolean);
        const deshabilitados = controles.map((control) => control.disabled);
        controles.forEach((control) => { control.disabled = true; });
        panel.setAttribute("aria-busy", "true");
        try {
            const resultado = await solicitarCambio(ruta, metodo, datos);
            if (resultado.estado === 401) {
                document.getElementById("panel-contenido").hidden = true;
                window.location.replace(sesion.obtenerRuta("iniciar-sesion"));
                return;
            }
            if ([200, 201].includes(resultado.estado)) {
                cambiosPendientes = false;
                alCompletar(resultado.contenido);
                return;
            }
            mostrarEstado(mensajeError(resultado.estado, resultado.contenido), true);
            mostrarErrores(resultado.contenido);
            estado.scrollIntoView({ block: "nearest", behavior: "smooth" });
        } catch (error) {
            mostrarEstado(error.name === "AbortError" ?
                "La solicitud tardó demasiado. Comprueba la lista de vacantes antes de volver a guardar." :
                "No se pudo contactar al servidor. Tus datos siguen en el formulario. Comprueba tu conexión.", true);
        } finally {
            controles.forEach((control, indice) => { control.disabled = deshabilitados[indice]; });
            panel.setAttribute("aria-busy", "false");
            enProceso = false;
        }
    };

    acciones.forEach((boton) => boton.addEventListener("click", async () => {
        if (enProceso) return;
        const accion = boton.dataset.accion;
        const identificador = boton.dataset.idVacante;
        const version = boton.dataset.version;
        if (!["publicar", "cerrar"].includes(accion) || !/^[1-9]\d*$/.test(identificador || "") || !version) {
            mostrarEstado("No se pudo identificar la vacante. Recarga la página.", true);
            return;
        }
        if (cambiosPendientes && accion === "publicar") {
            mostrarEstado("Guarda los cambios del formulario antes de publicar la vacante.", true);
            return;
        }
        const pregunta = accion === "publicar" ? "¿Publicar esta vacante con la última información guardada?" :
            "¿Cerrar esta vacante? Pasará a Cerrada y conservará su información e historial." +
            (cambiosPendientes ? " Los cambios del formulario sin guardar se descartarán." : "");
        mostrarConfirmacion(boton, pregunta, accion, () =>
            ejecutarCambio(`api/vacantes/${identificador}/${accion}`, "POST", { version },
                accion === "publicar" ? "Publicando vacante…" : "Cerrando vacante…",
                () => window.location.assign(sesion.obtenerRuta("panel/empresa/vacantes"))));
    }));

    if (!formulario || formulario.dataset.soloLectura === "true") return;
    const campo = (nombre) => formulario.elements.namedItem(nombre);
    const habilidades = [...formulario.querySelectorAll('input[name="habilidades"]')];
    const errorHabilidades = document.getElementById("habilidades-error");
    const fecha = campo("fechaVencimiento");
    const carrera = campo("idCarrera");
    const validarHabilidades = () => {
        const seleccionadas = habilidades.filter((habilidad) => habilidad.checked);
        const validas = seleccionadas.length >= 1 && seleccionadas.length <= 30;
        errorHabilidades.hidden = validas;
        errorHabilidades.textContent = validas ? "" : "Selecciona entre 1 y 30 habilidades disponibles.";
        habilidades.forEach((habilidad) => habilidad.setAttribute("aria-invalid", String(!validas)));
        return validas;
    };
    formulario.addEventListener("input", (evento) => {
        cerrarConfirmacion(false);
        cambiosPendientes = true;
        evento.target.setCustomValidity?.("");
        if (evento.target.name === "habilidades") validarHabilidades();
    });
    formulario.addEventListener("change", (evento) => {
        cerrarConfirmacion(false);
        cambiosPendientes = true;
        evento.target.setCustomValidity?.("");
    });
    formulario.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        if (enProceso) return;
        cerrarConfirmacion(false);
        ["titulo", "descripcion", "ciudad"].forEach((nombre) => {
            campo(nombre).value = campo(nombre).value.trim();
        });
        campo("codigoPais").value = campo("codigoPais").value.trim().toUpperCase();
        const fechaLimite = new Date(`${fecha.value}-05:00`);
        fecha.setCustomValidity(Number.isFinite(fechaLimite.getTime()) && fechaLimite.getTime() > Date.now() ?
            "" : "Selecciona una fecha y hora futuras en horario de Lima.");
        carrera.setCustomValidity(carrera.selectedOptions.length && !carrera.selectedOptions[0].disabled && carrera.value ?
            "" : "Selecciona una carrera disponible.");
        const habilidadesValidas = validarHabilidades();
        formulario.classList.add("was-validated");
        if (!formulario.checkValidity() || !habilidadesValidas) {
            mostrarEstado("Revisa los campos indicados antes de guardar.", true);
            formulario.querySelector(":invalid")?.focus();
            if (!habilidadesValidas && formulario.checkValidity()) habilidades[0]?.focus();
            formulario.reportValidity();
            return;
        }
        const identificador = formulario.dataset.idVacante;
        const version = formulario.dataset.version;
        if (identificador && (!/^[1-9]\d*$/.test(identificador) || !version)) {
            mostrarEstado("No se pudo identificar la versión de la vacante. Recarga la página.", true);
            return;
        }
        const datos = {
            titulo: campo("titulo").value, descripcion: campo("descripcion").value,
            idCarrera: Number(carrera.value), habilidades: habilidades.filter((habilidad) => habilidad.checked).map((habilidad) => Number(habilidad.value)),
            modalidad: campo("modalidad").value, ciudad: campo("ciudad").value,
            codigoPais: campo("codigoPais").value, fechaVencimiento: fechaLimite.toISOString()
        };
        // Conserva la precisión de la versión de PostgreSQL, sin convertirla a Date.
        if (identificador) datos.version = version;
        await ejecutarCambio(identificador ? `api/vacantes/${identificador}` : "api/vacantes",
            identificador ? "PUT" : "POST", datos, "Guardando vacante…", (vacante) => {
                if (!identificador && Number.isSafeInteger(vacante?.id) && vacante.id > 0) {
                    window.location.assign(sesion.obtenerRuta(`panel/empresa/vacantes/${vacante.id}/editar`));
                } else {
                    window.location.assign(sesion.obtenerRuta("panel/empresa/vacantes"));
                }
            });
    });
});
