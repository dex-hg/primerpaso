"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const panel = document.getElementById("panel");
    if (!panel) return;
    const sesion = window.sesionPrimerPaso;
    const contenido = document.getElementById("panel-contenido");
    const estado = document.getElementById("panel-estado");
    const reintentar = document.getElementById("panel-reintentar");
    const cerrar = document.getElementById("cerrar-sesion");
    let enProceso = false;

    if (!sesion || !contenido || !estado || !reintentar || !cerrar) {
        if (contenido) contenido.hidden = true;
        if (estado) estado.textContent = "No se pudo cargar tu panel. Recarga la página para continuar.";
        return;
    }

    const ocultarDatos = () => { contenido.hidden = true; };
    const volverAlAcceso = () => {
        ocultarDatos();
        window.location.replace(sesion.obtenerRuta("iniciar-sesion"));
    };
    const actualizarEspera = (ocupado) => {
        enProceso = ocupado;
        panel.setAttribute("aria-busy", String(ocupado));
        cerrar.disabled = ocupado;
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
            sesion.mostrarEstado(estado, "Sesión activa.");
        } catch (error) {
            mostrarError(sesion.mensajeConexion(error));
        } finally {
            actualizarEspera(false);
        }
    };

    cerrar.addEventListener("click", async () => {
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
