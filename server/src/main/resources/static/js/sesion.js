"use strict";

(() => {
    const mensajeCredenciales = "Correo, contraseña o tipo de cuenta incorrectos.";
    let enProceso = false;

    const obtenerBaseApi = () => {
        const esServidorLocal = ["localhost", "127.0.0.1"].includes(window.location.hostname);
        const esVistaEstatica = ["5500", "4173", "8000"].includes(window.location.port);
        return esServidorLocal && esVistaEstatica ? `http://${window.location.hostname}:8080` : window.location.origin;
    };

    const leerRespuesta = async (respuesta) => {
        let contenido = null;
        try { contenido = await respuesta.json(); } catch { /* Una respuesta vacía no contiene JSON. */ }
        return { estado: respuesta.status, contenido };
    };

    const solicitarSesion = async (metodo = "GET", datos = null) => {
        const controlador = new AbortController();
        const temporizador = window.setTimeout(() => controlador.abort(), 15000);
        const opciones = {
            method: metodo,
            headers: { Accept: "application/json" },
            credentials: "include",
            cache: "no-store",
            signal: controlador.signal
        };
        try {
            if (metodo !== "GET") {
                const seguridad = await leerRespuesta(await fetch(`${obtenerBaseApi()}/api/sesion/seguridad`, {
                    ...opciones, method: "GET"
                }));
                if (seguridad.estado !== 200) return seguridad;
                const token = seguridad.contenido?.tokenCsrf;
                if (typeof token !== "string" || !token) return { estado: 403, contenido: null };
                opciones.headers["X-CSRF-Token"] = token;
            }
            if (datos !== null) {
                opciones.headers["Content-Type"] = "application/json";
                opciones.body = JSON.stringify(datos);
            }
            return await leerRespuesta(await fetch(`${obtenerBaseApi()}/api/sesion`, opciones));
        } finally {
            window.clearTimeout(temporizador);
        }
    };

    const mostrarEstado = (elemento, mensaje, esError = false) => {
        if (!elemento) return;
        elemento.textContent = mensaje;
        elemento.classList.toggle("is-error", esError);
    };

    const mensajeError = (estado) => {
        if (estado === 400) return "Revisa el correo, la contraseña y el tipo de cuenta ingresados.";
        if (estado === 401) return mensajeCredenciales;
        if (estado === 403) return "La solicitud de sesión no es válida. Recarga la página e inténtalo nuevamente.";
        if (estado === 503) return "El servicio de inicio de sesión no está disponible. Inténtalo más tarde.";
        return "No se pudo comprobar tu sesión. Inténtalo nuevamente.";
    };

    const mensajeConexion = (error) => error.name === "AbortError" ?
        "La solicitud tardó demasiado. Vuelve a intentarlo para comprobar tu sesión." :
        "No se pudo contactar al servidor. Verifica que esté iniciado y vuelve a intentarlo.";

    const validarUsuario = (usuario) => {
        if (!usuario || !Number.isSafeInteger(usuario.idUsuario) || usuario.idUsuario < 1) return false;
        const textosValidos = [usuario.nombres, usuario.correo]
            .every((valor) => typeof valor === "string" && valor.trim());
        if (!textosValidos || !["postulante", "empresa"].includes(usuario.tipoCuenta)) return false;
        if (typeof usuario.apellidos !== "string" ||
            (usuario.tipoCuenta === "postulante" && !usuario.apellidos.trim())) return false;
        return usuario.tipoCuenta !== "empresa" ||
            (Number.isSafeInteger(usuario.idEmpresa) && usuario.idEmpresa > 0 &&
                typeof usuario.nombreEmpresa === "string" && usuario.nombreEmpresa.trim() &&
                typeof usuario.rolEmpresa === "string" && usuario.rolEmpresa.trim());
    };

    const iniciarAcceso = (formulario) => {
        const estado = formulario.querySelector(".auth-form-status");
        const controles = [...formulario.querySelectorAll("input, button")];
        const deshabilitados = controles.map((control) => control.disabled);
        const correo = formulario.elements.namedItem("login-email");
        const contrasena = formulario.elements.namedItem("login-password");
        const boton = formulario.querySelector('button[type="submit"]');
        const actualizarEspera = (ocupado) => {
            enProceso = ocupado;
            formulario.setAttribute("aria-busy", String(ocupado));
            controles.forEach((control, indice) => { control.disabled = ocupado || deshabilitados[indice]; });
            boton.textContent = ocupado ? "Comprobando…" : "Iniciar sesión";
        };

        const comprobarSesion = async () => {
            if (enProceso) return;
            actualizarEspera(true);
            mostrarEstado(estado, "Comprobando tu sesión…");
            try {
                const resultado = await solicitarSesion();
                if (resultado.estado === 200 && validarUsuario(resultado.contenido)) {
                    contrasena.value = "";
                    window.location.replace("/cuenta");
                    return;
                }
                mostrarEstado(estado, resultado.estado === 401 ? "" : mensajeError(resultado.estado), resultado.estado !== 401);
            } catch (error) {
                mostrarEstado(estado, mensajeConexion(error), true);
            } finally {
                actualizarEspera(false);
            }
        };

        formulario.querySelectorAll("[data-acceso-pendiente]").forEach((elemento) => {
            elemento.addEventListener("click", (evento) => {
                evento.preventDefault();
                if (!enProceso) mostrarEstado(estado, elemento.dataset.accesoPendiente, true);
            });
        });

        formulario.addEventListener("submit", async (evento) => {
            evento.preventDefault();
            if (enProceso) return;
            correo.value = correo.value.trim().toLowerCase();
            const tipoCuenta = formulario.elements.namedItem("login-role").value;
            contrasena.setCustomValidity(contrasena.value.length >= 8 && contrasena.value.length <= 128 ?
                "" : "Usa entre 8 y 128 caracteres.");
            formulario.classList.add("was-validated");
            if (!formulario.checkValidity() || !["postulante", "empresa"].includes(tipoCuenta)) {
                mostrarEstado(estado, "Revisa los campos indicados.", true);
                formulario.querySelector(":invalid")?.focus();
                formulario.reportValidity();
                return;
            }
            const datos = { correo: correo.value, contrasena: contrasena.value, tipoCuenta };
            actualizarEspera(true);
            mostrarEstado(estado, "Iniciando sesión…");
            try {
                const resultado = await solicitarSesion("POST", datos);
                if (resultado.estado === 200 && validarUsuario(resultado.contenido)) {
                    contrasena.value = "";
                    mostrarEstado(estado, "Sesión iniciada. Abriendo tu cuenta…");
                    window.location.replace("/cuenta");
                    return;
                }
                mostrarEstado(estado, mensajeError(resultado.estado), true);
            } catch (error) {
                mostrarEstado(estado, mensajeConexion(error), true);
            } finally {
                actualizarEspera(false);
            }
        });

        contrasena.addEventListener("input", () => contrasena.setCustomValidity(""));
        window.addEventListener("pagehide", () => { contrasena.value = ""; });
        window.addEventListener("pageshow", (evento) => { if (evento.persisted) comprobarSesion(); });
        comprobarSesion();
    };

    const iniciarCuenta = (cuenta) => {
        const estado = document.getElementById("sesion-estado");
        const contenido = document.getElementById("datos-cuenta");
        const empresa = document.getElementById("datos-empresa");
        const reintentar = document.getElementById("reintentar-sesion");
        const contenedorReintento = document.getElementById("reintentar-contenedor");
        const cerrar = document.getElementById("cerrar-sesion");
        const ocultarDatos = () => {
            contenido.hidden = true;
            empresa.hidden = true;
            cuenta.querySelectorAll("dd").forEach((elemento) => { elemento.textContent = ""; });
        };
        const volverAlAcceso = () => {
            ocultarDatos();
            window.location.replace("/iniciar-sesion");
        };
        const actualizarEspera = (ocupado) => {
            enProceso = ocupado;
            cuenta.setAttribute("aria-busy", String(ocupado));
            reintentar.disabled = ocupado;
            cerrar.disabled = ocupado;
        };

        const comprobarSesion = async () => {
            if (enProceso) return;
            ocultarDatos();
            actualizarEspera(true);
            contenedorReintento.hidden = true;
            mostrarEstado(estado, "Comprobando tu sesión…");
            try {
                const resultado = await solicitarSesion();
                if (resultado.estado === 401) return volverAlAcceso();
                if (resultado.estado !== 200 || !validarUsuario(resultado.contenido)) {
                    mostrarEstado(estado, mensajeError(resultado.estado), true);
                    contenedorReintento.hidden = false;
                    return;
                }
                const usuario = resultado.contenido;
                document.getElementById("cuenta-nombre").textContent = `${usuario.nombres} ${usuario.apellidos}`.trim();
                document.getElementById("cuenta-correo").textContent = usuario.correo;
                document.getElementById("cuenta-tipo").textContent = usuario.tipoCuenta === "empresa" ? "Empresa" : "Postulante";
                if (usuario.tipoCuenta === "empresa") {
                    document.getElementById("cuenta-empresa").textContent = usuario.nombreEmpresa;
                    document.getElementById("cuenta-rol").textContent = usuario.rolEmpresa;
                    empresa.hidden = false;
                }
                mostrarEstado(estado, "Sesión activa.");
                contenido.hidden = false;
            } catch (error) {
                mostrarEstado(estado, mensajeConexion(error), true);
                contenedorReintento.hidden = false;
            } finally {
                actualizarEspera(false);
            }
        };

        cerrar.addEventListener("click", async () => {
            if (enProceso) return;
            ocultarDatos();
            actualizarEspera(true);
            mostrarEstado(estado, "Cerrando tu sesión…");
            try {
                const resultado = await solicitarSesion("DELETE");
                if ([204, 401].includes(resultado.estado)) return volverAlAcceso();
                mostrarEstado(estado, mensajeError(resultado.estado), true);
                contenedorReintento.hidden = false;
            } catch (error) {
                mostrarEstado(estado, mensajeConexion(error), true);
                contenedorReintento.hidden = false;
            } finally {
                actualizarEspera(false);
            }
        });
        reintentar.addEventListener("click", comprobarSesion);
        window.addEventListener("pagehide", ocultarDatos);
        window.addEventListener("pageshow", (evento) => { if (evento.persisted) comprobarSesion(); });
        comprobarSesion();
    };

    document.addEventListener("DOMContentLoaded", () => {
        const formulario = document.getElementById("login-form");
        const cuenta = document.getElementById("cuenta");
        if (formulario) iniciarAcceso(formulario);
        if (cuenta) iniciarCuenta(cuenta);
    });
})();
