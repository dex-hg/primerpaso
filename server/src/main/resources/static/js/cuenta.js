"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const formulario = document.getElementById("formulario-cuenta");
    const panel = document.getElementById("panel");
    const sesion = window.sesionPrimerPaso;
    if (!formulario || !panel || !sesion) return;
    const estado = document.getElementById("cuenta-estado");
    const reintentar = document.getElementById("cuenta-reintentar");
    const descartar = document.getElementById("cuenta-descartar");
    const campo = (nombre) => formulario.elements.namedItem(nombre);
    let cuentaGuardada = null;
    let enProceso = false;

    const mostrarEstado = (mensaje, esError = false) => sesion.mostrarEstado(estado, mensaje, esError);
    const mismaIdentidad = (cuenta) => sesion.validarUsuario(cuenta?.usuario) &&
        String(cuenta.usuario.idUsuario) === panel.dataset.idUsuario &&
        cuenta.usuario.tipoCuenta === panel.dataset.tipoCuenta &&
        (cuenta.usuario.tipoCuenta !== "empresa" ||
            (String(cuenta.usuario.idEmpresa) === panel.dataset.idEmpresa &&
                cuenta.usuario.rolEmpresa === panel.dataset.rolEmpresa));
    const actualizarFormacion = () => {
        if (!campo("condicionAcademica")) return;
        const esEstudiante = campo("condicionAcademica").value === "estudiante";
        ["cicloActual", "anioEgreso"].forEach((nombre) => {
            const activo = nombre === "cicloActual" ? esEstudiante : !esEstudiante;
            campo(nombre).disabled = !activo;
            campo(nombre).required = activo;
            campo(nombre).closest("div").hidden = !activo;
        });
    };
    const llenarFormulario = (cuenta) => {
        cuentaGuardada = cuenta;
        Object.entries(cuenta.datos).forEach(([nombre, valor]) => {
            if (campo(nombre)) campo(nombre).value = valor ?? "";
        });
        const perfil = cuenta.formacion || cuenta.empresa;
        Object.entries(perfil).forEach(([nombre, valor]) => {
            if (campo(nombre)) campo(nombre).value = valor ?? "";
        });
        if (cuenta.formacion) {
            const carrera = campo("idCarrera");
            carrera.replaceChildren();
            const opciones = [...cuenta.carreras];
            if (!opciones.some((opcion) => opcion.id === perfil.idCarrera)) {
                opciones.push({ id: perfil.idCarrera, nombre: `${perfil.nombreCarrera} (no disponible para nuevos registros)` });
            }
            opciones.forEach((opcion) => {
                const elemento = document.createElement("option");
                elemento.value = String(opcion.id);
                elemento.textContent = opcion.nombre;
                carrera.appendChild(elemento);
            });
            carrera.value = String(perfil.idCarrera);
            actualizarFormacion();
        } else {
            document.getElementById("cuenta-empresa").textContent = perfil.nombreComercial;
            document.getElementById("cuenta-identificacion").textContent =
                `Identificación fiscal: ${perfil.identificacionFiscal} · País: ${perfil.codigoPais}`;
        }
        document.getElementById("cuenta-nombre").textContent = `${cuenta.datos.nombres || ""} ${cuenta.datos.apellidos || ""}`.trim();
        document.getElementById("cuenta-correo").textContent = cuenta.datos.correo;
        formulario.classList.remove("was-validated");
        formulario.hidden = false;
    };

    const solicitarCuenta = async (datos = null) => {
        const controlador = new AbortController();
        const temporizador = window.setTimeout(() => controlador.abort(), 15000);
        const opciones = {
            method: "GET", credentials: "include", cache: "no-store",
            headers: { Accept: "application/json" }, signal: controlador.signal
        };
        try {
            if (datos) {
                const seguridad = await fetch(sesion.obtenerRuta("api/sesion/seguridad"), opciones);
                const contenido = await seguridad.json();
                if (seguridad.status !== 200 || typeof contenido.tokenCsrf !== "string" || !contenido.tokenCsrf) {
                    return { estado: 403, contenido: null };
                }
                opciones.method = "PUT";
                opciones.headers["Content-Type"] = "application/json";
                opciones.headers["X-CSRF-Token"] = contenido.tokenCsrf;
                opciones.body = JSON.stringify(datos);
            }
            const respuesta = await fetch(sesion.obtenerRuta("api/cuenta"), opciones);
            let contenido = null;
            try { contenido = await respuesta.json(); } catch { /* El servidor puede responder sin JSON. */ }
            return { estado: respuesta.status, contenido };
        } finally {
            window.clearTimeout(temporizador);
        }
    };

    const procesarRespuesta = (resultado) => {
        if (resultado.estado === 401) {
            formulario.hidden = true;
            window.location.replace(sesion.obtenerRuta("iniciar-sesion"));
            return false;
        }
        if (resultado.estado === 200) {
            if (!mismaIdentidad(resultado.contenido)) {
                formulario.hidden = true;
                window.location.replace(sesion.obtenerRuta("panel"));
                return false;
            }
            llenarFormulario(resultado.contenido);
            return true;
        }
        const mensaje = resultado.contenido?.mensaje || "No se pudo completar la operación. Inténtalo nuevamente.";
        mostrarEstado(mensaje, true);
        reintentar.hidden = false;
        return false;
    };

    const cargarCuenta = async () => {
        if (enProceso) return;
        enProceso = true;
        formulario.hidden = true;
        reintentar.disabled = true;
        mostrarEstado("Cargando tus datos…");
        try {
            if (procesarRespuesta(await solicitarCuenta())) {
                mostrarEstado("");
                reintentar.hidden = true;
            }
        } catch (error) {
            mostrarEstado(sesion.mensajeConexion(error), true);
            reintentar.hidden = false;
        } finally {
            enProceso = false;
            reintentar.disabled = false;
        }
    };

    formulario.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        if (enProceso || !cuentaGuardada || document.getElementById("panel-contenido").hidden) return;
        formulario.querySelectorAll("input").forEach((entrada) => { entrada.value = entrada.value.trim(); });
        const telefono = campo("telefono");
        telefono.setCustomValidity(!telefono.value || /^(?=(?:\D*\d){6,20}\D*$)\+?[0-9 ().-]+$/.test(telefono.value) ?
            "" : "Ingresa un teléfono válido de 6 a 20 dígitos.");
        formulario.classList.add("was-validated");
        if (!formulario.checkValidity()) {
            mostrarEstado("Revisa los campos indicados antes de guardar.", true);
            formulario.querySelector(":invalid")?.focus();
            formulario.reportValidity();
            return;
        }
        const datos = { version: cuentaGuardada.version };
        ["nombres", "apellidos", "correo", "telefono", "ciudad", "institucion", "condicionAcademica",
            "nombreComercial", "sector", "sitioWeb"].forEach((nombre) => {
            datos[nombre] = campo(nombre)?.value.trim() || null;
        });
        ["idCarrera", "cicloActual", "anioEgreso"].forEach((nombre) => {
            const entrada = campo(nombre);
            datos[nombre] = entrada && !entrada.disabled && entrada.value ? Number(entrada.value) : null;
        });
        enProceso = true;
        const controles = [...formulario.querySelectorAll("input, select, button"), reintentar];
        const deshabilitados = controles.map((control) => control.disabled);
        controles.forEach((control) => { control.disabled = true; });
        formulario.setAttribute("aria-busy", "true");
        mostrarEstado("Guardando cambios…");
        try {
            if (procesarRespuesta(await solicitarCuenta(datos))) {
                mostrarEstado("Cambios guardados.");
                reintentar.hidden = true;
            }
        } catch (error) {
            mostrarEstado(sesion.mensajeConexion(error) + " Tus datos siguen en el formulario.", true);
        } finally {
            controles.forEach((control, indice) => { control.disabled = deshabilitados[indice]; });
            actualizarFormacion();
            formulario.setAttribute("aria-busy", "false");
            enProceso = false;
            estado.scrollIntoView({ block: "nearest", behavior: "smooth" });
        }
    });
    campo("telefono").addEventListener("input", () => campo("telefono").setCustomValidity(""));
    campo("condicionAcademica")?.addEventListener("change", actualizarFormacion);
    reintentar.addEventListener("click", cargarCuenta);
    descartar.addEventListener("click", () => {
        if (!enProceso && cuentaGuardada) {
            llenarFormulario(cuentaGuardada);
            mostrarEstado("");
        }
    });
    window.addEventListener("pageshow", (evento) => { if (evento.persisted) cargarCuenta(); });
    cargarCuenta();
});
