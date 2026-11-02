"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const chat = document.getElementById("chat");
    if (!chat) return;
    const sesion = window.sesionPrimerPaso;
    const panel = document.getElementById("panel");
    const formulario = document.getElementById("chat-formulario");
    const texto = document.getElementById("chat-contenido");
    const enviar = document.getElementById("chat-enviar");
    const actualizar = document.getElementById("chat-actualizar");
    const anteriores = document.getElementById("chat-anteriores");
    const historial = document.getElementById("chat-historial");
    const lista = document.getElementById("chat-mensajes");
    const estado = document.getElementById("chat-estado");
    const aviso = document.getElementById("chat-aviso");
    const vacio = document.getElementById("chat-vacio");
    const contador = document.getElementById("chat-contador");
    if (!sesion || !panel || !formulario || !texto || !lista) return;
    if (!window.crypto?.randomUUID) {
        texto.disabled = true;
        enviar.disabled = true;
        sesion.mostrarEstado(estado, "Este navegador no permite autorizar los envíos. Usa una versión actualizada y una conexión HTTPS o localhost.", true);
        return;
    }
    const ruta = `api/chat/postulaciones/${chat.dataset.idPostulacion}`;
    const nodos = new Map([...lista.children].map((nodo) => [Number(nodo.dataset.idMensaje), nodo]));
    let ultimoRecibido = Math.max(0, ...nodos.keys());
    let puedeEnviar = chat.dataset.puedeEnviar === "true";
    let historialPosicionado = false;
    let enviando = false, consultando = false, activo = true, temporizador = null, pendiente = null;
    const fecha = new Intl.DateTimeFormat("es-PE", {
        day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit",
        hour12: false, timeZone: "America/Lima"
    });

    const mostrar = (mensaje, error = false) => sesion.mostrarEstado(estado, mensaje, error);
    const contar = () => { contador.textContent = `${texto.value.length} / 4000`; texto.setCustomValidity(""); };
    const ajustarControles = () => {
        texto.disabled = enviando || !puedeEnviar;
        enviar.disabled = enviando || !puedeEnviar;
        enviar.textContent = enviando ? "Enviando…" : "Enviar mensaje";
        formulario.setAttribute("aria-busy", String(enviando));
    };
    const salir = (destino) => {
        activo = false;
        window.clearTimeout(temporizador);
        document.getElementById("panel-contenido").hidden = true;
        window.location.replace(sesion.obtenerRuta(destino));
        const error = new Error("Se debe comprobar nuevamente la cuenta.");
        error.navegando = true;
        throw error;
    };
    const comprobarIdentidad = async () => {
        const resultado = await sesion.solicitarSesion();
        if (resultado.estado === 401) salir("iniciar-sesion");
        const usuario = resultado.contenido;
        if (resultado.estado !== 200 || !sesion.validarUsuario(usuario)) {
            throw new Error("No se pudo comprobar tu sesión. Vuelve a actualizar antes de continuar.");
        }
        if (String(usuario.idUsuario) !== panel.dataset.idUsuario || usuario.tipoCuenta !== panel.dataset.tipoCuenta ||
                (usuario.tipoCuenta === "empresa" && (String(usuario.idEmpresa) !== panel.dataset.idEmpresa ||
                    usuario.rolEmpresa !== panel.dataset.rolEmpresa))) salir("panel");
    };
    const solicitar = async (sufijo, metodo = "GET", datos = null) => {
        await comprobarIdentidad();
        const controlador = new AbortController();
        const espera = window.setTimeout(() => controlador.abort(), 15000);
        const opciones = { method: metodo, credentials: "include", cache: "no-store",
            headers: { Accept: "application/json" }, signal: controlador.signal };
        try {
            if (datos !== null) {
                const seguridad = await fetch(sesion.obtenerRuta("api/sesion/seguridad"), { ...opciones, method: "GET" });
                if (seguridad.status === 401) salir("iniciar-sesion");
                const token = (await seguridad.json()).tokenCsrf;
                if (seguridad.status !== 200 || typeof token !== "string" || token.length !== 43) {
                    throw new Error("No se pudo autorizar el envío. Conservamos tu mensaje; vuelve a intentarlo.");
                }
                opciones.headers["Content-Type"] = "application/json";
                opciones.headers["X-CSRF-Token"] = token;
                opciones.body = JSON.stringify(datos);
            }
            const respuesta = await fetch(sesion.obtenerRuta(ruta) + sufijo, opciones);
            if (respuesta.status === 401) salir("iniciar-sesion");
            let contenido = null;
            try { contenido = await respuesta.json(); } catch { /* Se conserva el borrador ante respuestas incompletas. */ }
            if (![200, 201].includes(respuesta.status) || !contenido) {
                if (metodo === "GET" && [403, 404].includes(respuesta.status)) salir("panel");
                const error = new Error(contenido?.mensaje || "No se pudo completar la operación. Vuelve a intentarlo.");
                error.estadoHttp = respuesta.status;
                throw error;
            }
            return contenido;
        } finally { window.clearTimeout(espera); }
    };
    const pintarMensajes = (mensajes, cargarAnteriores = false) => {
        if (!Array.isArray(mensajes) || mensajes.some((mensaje) => !Number.isSafeInteger(mensaje.id) || mensaje.id <= 0 ||
                typeof mensaje.contenido !== "string" || typeof mensaje.nombreEmisor !== "string" ||
                !Number.isFinite(Date.parse(mensaje.fechaEnvio)))) throw new Error("El historial recibido no es válido.");
        const altura = historial.scrollHeight;
        const estabaAlFinal = altura - historial.scrollTop - historial.clientHeight < 80;
        for (const mensaje of mensajes) {
            if (!nodos.has(mensaje.id)) {
                const nodo = document.createElement("li");
                nodo.className = "chat-mensaje" + (String(mensaje.idEmisor) === panel.dataset.idUsuario ? " chat-propio" : "");
                nodo.dataset.idMensaje = String(mensaje.id);
                const autor = document.createElement("p"), cuerpo = document.createElement("p"), hora = document.createElement("time");
                autor.className = "chat-autor";
                autor.textContent = `${mensaje.nombreEmisor} · ${mensaje.tipoEmisor === "empresa" ? "Empresa" : "Postulante"}`;
                cuerpo.className = "chat-texto";
                cuerpo.textContent = mensaje.contenido;
                hora.dateTime = mensaje.fechaEnvio;
                hora.textContent = `${fecha.format(new Date(mensaje.fechaEnvio)).replace(",", "")} (Lima)`;
                nodo.append(autor, cuerpo, hora);
                const siguiente = [...lista.children].find((elemento) => Number(elemento.dataset.idMensaje) > mensaje.id);
                lista.insertBefore(nodo, siguiente || null);
                nodos.set(mensaje.id, nodo);
            }
            if (pendiente && mensaje.idEnvio === pendiente.idEnvio && String(mensaje.idEmisor) === panel.dataset.idUsuario) {
                if (texto.value.trim() === pendiente.contenido) { texto.value = ""; contar(); }
                pendiente = null;
            }
        }
        vacio.hidden = nodos.size > 0;
        if (cargarAnteriores) historial.scrollTop += historial.scrollHeight - altura;
        else if (estabaAlFinal) historial.scrollTop = historial.scrollHeight;
    };
    const programar = (demora = 5000) => {
        window.clearTimeout(temporizador);
        if (activo) temporizador = window.setTimeout(() => {
            if (document.visibilityState === "visible" && !document.getElementById("panel-contenido").hidden) {
                if (!historialPosicionado) {
                    historial.scrollTop = historial.scrollHeight;
                    historialPosicionado = true;
                }
                consultar();
            } else programar(!historialPosicionado && document.visibilityState === "visible" ? 250 : 5000);
        }, demora);
    };
    const consultar = async (cargarAnteriores = false) => {
        if (consultando || enviando || !activo) return;
        consultando = true;
        actualizar.disabled = true;
        anteriores.disabled = true;
        let haySiguientes = false;
        try {
            const primero = Math.min(...nodos.keys());
            const consulta = cargarAnteriores && Number.isFinite(primero) ? `?antesDe=${primero}` :
                ultimoRecibido ? `?despuesDe=${ultimoRecibido}` : "";
            const detalle = await solicitar(consulta);
            pintarMensajes(detalle.mensajes, cargarAnteriores);
            puedeEnviar = detalle.puedeEnviar === true;
            aviso.textContent = detalle.aviso || "";
            aviso.hidden = !detalle.aviso;
            if (cargarAnteriores || !ultimoRecibido) anteriores.hidden = !detalle.hayAnteriores;
            if (!cargarAnteriores) ultimoRecibido = Math.max(ultimoRecibido, ...detalle.mensajes.map((mensaje) => mensaje.id));
            haySiguientes = !cargarAnteriores && detalle.haySiguientes;
            mostrar("");
        } catch (error) {
            if (!error.navegando) mostrar(error.name === "AbortError" ? "La actualización tardó demasiado. Vuelve a intentarlo." : error.message, true);
        } finally {
            consultando = false;
            actualizar.disabled = false;
            anteriores.disabled = false;
            ajustarControles();
            programar(haySiguientes ? 100 : 5000);
        }
    };
    formulario.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        if (enviando || !puedeEnviar || !activo) return;
        texto.setCustomValidity(texto.value.trim() ? "" : "Escribe un mensaje antes de enviarlo.");
        if (!formulario.reportValidity()) return;
        const contenido = texto.value.replace(/\r\n?/g, "\n").trim();
        if (!pendiente || pendiente.contenido !== contenido) pendiente = { idEnvio: window.crypto.randomUUID(), contenido };
        enviando = true;
        ajustarControles();
        let guardado = false;
        try {
            const resultado = await solicitar("/mensajes", "POST", pendiente);
            pintarMensajes([resultado.mensaje]);
            historial.scrollTop = historial.scrollHeight;
            historialPosicionado = true;
            texto.value = "";
            pendiente = null;
            contar();
            mostrar("Mensaje guardado.");
            guardado = true;
        } catch (error) {
            if (!error.navegando) mostrar(error.name === "AbortError" ?
                "El envío tardó demasiado. Conservamos tu mensaje; puedes reintentar sin duplicarlo." :
                `${error.message} Conservamos lo escrito.`, true);
            if (error.estadoHttp === 409) puedeEnviar = false;
        } finally {
            enviando = false;
            ajustarControles();
            if (guardado) await consultar();
            else programar();
        }
    });
    texto.addEventListener("input", contar);
    actualizar.addEventListener("click", () => consultar());
    anteriores.addEventListener("click", () => consultar(true));
    window.addEventListener("pagehide", () => { activo = false; window.clearTimeout(temporizador); });
    window.addEventListener("pageshow", (evento) => { if (evento.persisted) { activo = true; programar(100); } });
    document.addEventListener("visibilitychange", () => {
        window.clearTimeout(temporizador);
        if (document.visibilityState === "visible") programar(100);
    });
    contar();
    ajustarControles();
    programar(100);
});
