"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const panel = document.getElementById("panel");
    if (!panel) return;
    const sesion = window.sesionPrimerPaso;
    const contenido = document.getElementById("panel-contenido");
    const estado = document.getElementById("panel-estado");
    const reintentar = document.getElementById("panel-reintentar");
    const cerrar = document.getElementById("cerrar-sesion");
    const contador = document.getElementById("contador-notificaciones");
    let enProceso = false;
    let consultaPendientes = null;

    if (!sesion || !contenido || !estado || !reintentar) {
        if (contenido) contenido.hidden = true;
        if (estado) estado.textContent = "No se pudo cargar tu panel. Recarga la página para continuar.";
        return;
    }

    const ocultarDatos = () => {
        contenido.hidden = true;
        consultaPendientes?.abort();
        if (contador) contador.hidden = true;
    };

    const cargarPendientes = async () => {
        if (!contador) return;
        const controlador = new AbortController();
        consultaPendientes = controlador;
        const temporizador = window.setTimeout(() => controlador.abort(), 5000);
        try {
            const respuesta = await fetch(sesion.obtenerRuta("api/notificaciones/pendientes"), {
                headers: { Accept: "application/json" }, credentials: "include", cache: "no-store",
                signal: controlador.signal
            });
            if (!respuesta.ok) return;
            const { cantidad, idUsuario, idEmpresa } = await respuesta.json();
            if (controlador.signal.aborted || contenido.hidden || String(idUsuario) !== panel.dataset.idUsuario
                || String(idEmpresa ?? "") !== (panel.dataset.idEmpresa || "")) return;
            if (!Number.isSafeInteger(cantidad) || cantidad <= 0) return;
            contador.textContent = cantidad > 99 ? "99+" : String(cantidad);
            contador.setAttribute("aria-label", cantidad === 1 ? "1 notificación sin leer" : cantidad + " notificaciones sin leer");
            contador.hidden = false;
        } catch {
            // La bandeja puede consultarse aunque el contador no esté disponible.
        } finally {
            window.clearTimeout(temporizador);
            if (consultaPendientes === controlador) consultaPendientes = null;
        }
    };
    const volverAlAcceso = () => {
        ocultarDatos();
        window.location.replace(sesion.obtenerRuta("iniciar-sesion"));
    };
    const actualizarEspera = (ocupado) => {
        enProceso = ocupado;
        panel.setAttribute("aria-busy", String(ocupado));
        if (cerrar) cerrar.disabled = ocupado;
        reintentar.disabled = ocupado;
    };
    const mostrarError = (mensaje) => {
        ocultarDatos();
        sesion.mostrarEstado(estado, mensaje, true);
        reintentar.hidden = false;
    };

    const comprobarSesion = async () => {
        if (enProceso) return;
        ocultarDatos();
        actualizarEspera(true);
        reintentar.hidden = true;
        sesion.mostrarEstado(estado, "Comprobando tu sesión…");
        try {
            const resultado = await sesion.solicitarSesion();
            if (resultado.estado === 401) return volverAlAcceso();
            if (resultado.estado !== 200 || !sesion.validarUsuario(resultado.contenido)) {
                mostrarError(sesion.mensajeError(resultado.estado));
                return;
            }
            const usuario = resultado.contenido;
            if (usuario.tipoCuenta !== panel.dataset.tipoCuenta) {
                window.location.replace(sesion.obtenerRuta("panel"));
                return;
            }
            // Recarga el SSR si otra sesión cambió la identidad o el rol de esta pestaña.
            if (String(usuario.idUsuario) !== panel.dataset.idUsuario ||
                (usuario.tipoCuenta === "empresa" &&
                    (String(usuario.idEmpresa) !== panel.dataset.idEmpresa ||
                        usuario.rolEmpresa !== panel.dataset.rolEmpresa))) {
                window.location.replace(sesion.obtenerRuta("panel"));
                return;
            }
            contenido.hidden = false;
            sesion.mostrarEstado(estado, "");
            void cargarPendientes();
        } catch (error) {
            mostrarError(sesion.mensajeConexion(error));
        } finally {
            actualizarEspera(false);
        }
    };

    cerrar?.addEventListener("click", async () => {
        if (enProceso) return;
        ocultarDatos();
        actualizarEspera(true);
        reintentar.hidden = true;
        sesion.mostrarEstado(estado, "Cerrando tu sesión…");
        try {
            const resultado = await sesion.solicitarSesion("DELETE");
            if ([204, 401].includes(resultado.estado)) return volverAlAcceso();
            mostrarError(sesion.mensajeError(resultado.estado));
        } catch (error) {
            mostrarError(sesion.mensajeConexion(error));
        } finally {
            actualizarEspera(false);
        }
    });
    reintentar.addEventListener("click", comprobarSesion);
    window.addEventListener("pagehide", ocultarDatos);
    window.addEventListener("pageshow", (evento) => { if (evento.persisted) comprobarSesion(); });
    document.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "visible") comprobarSesion();
        else ocultarDatos();
    });
    comprobarSesion();
});
