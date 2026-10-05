"use strict";

(() => {
    const obtenerCampo = (formulario, nombre) => formulario.elements.namedItem(nombre);
    const obtenerTexto = (formulario, nombre) => obtenerCampo(formulario, nombre)?.value.trim() || "";
    const obtenerOpciones = (formulario, nombre) => [...formulario.querySelectorAll(`input[name="${nombre}"]:checked`)]
        .map((campo) => campo.value);

    const mostrarEstado = (formulario, mensaje, esError = false) => {
        const estado = formulario.querySelector(".auth-form-status");
        if (!estado) return;
        estado.textContent = mensaje;
        estado.classList.toggle("is-error", esError);
    };

    const obtenerBaseApi = () => {
        const esServidorLocal = ["localhost", "127.0.0.1"].includes(window.location.hostname);
        const esVistaEstatica = ["5500", "4173", "8000"].includes(window.location.port);
        return esServidorLocal && esVistaEstatica ? `http://${window.location.hostname}:8080` : window.location.origin;
    };

    const construirDatos = (formulario) => {
        const esPostulante = formulario.dataset.tipoRegistro === "postulante";
        const prefijo = esPostulante ? "applicant" : "recruiter";
        const datos = {
            nombres: obtenerTexto(formulario, `${prefijo}-first-name`),
            apellidos: obtenerTexto(formulario, `${prefijo}-last-name`),
            correo: obtenerTexto(formulario, `${prefijo}-email`).toLowerCase(),
            // La contraseña conserva exactamente los caracteres ingresados.
            contrasena: obtenerCampo(formulario, `${prefijo}-password`).value,
            aceptaTerminos: obtenerCampo(formulario, esPostulante ? "applicant-terms" : "company-terms").checked
        };
        if (esPostulante) {
            const condicionAcademica = obtenerTexto(formulario, "applicant-academic-status");
            const periodo = Number(obtenerTexto(formulario, "applicant-cycle"));
            return {
                ...datos,
                institucion: obtenerTexto(formulario, "applicant-institution"),
                carrera: obtenerTexto(formulario, "applicant-career"),
                condicionAcademica,
                cicloActual: condicionAcademica === "estudiante" ? periodo : null,
                anioEgreso: condicionAcademica !== "estudiante" ? periodo : null,
                habilidades: obtenerOpciones(formulario, "applicant-skills"),
                intereses: obtenerOpciones(formulario, "applicant-interests"),
                aceptaComunicaciones: obtenerCampo(formulario, "applicant-communications").checked
            };
        }
        return {
            ...datos,
            nombreComercial: obtenerTexto(formulario, "company-name"),
            codigoPais: obtenerTexto(formulario, "company-country").toUpperCase(),
            identificacionFiscal: obtenerTexto(formulario, "company-tax-id"),
            sector: obtenerTexto(formulario, "company-sector"),
            ciudad: obtenerTexto(formulario, "company-location"),
            sitioWeb: obtenerTexto(formulario, "company-website") || null,
            telefono: obtenerTexto(formulario, "recruiter-phone"),
            intereses: obtenerOpciones(formulario, "company-interests")
        };
    };

    const validarCamposComplementarios = (formulario) => {
        formulario.querySelectorAll("input[required]").forEach((campo) => {
            if (["text", "tel"].includes(campo.type)) {
                campo.setCustomValidity(campo.value.trim() ? "" : "Completa este campo.");
            }
        });
        formulario.querySelectorAll('input[type="password"]:not([data-confirm-password])').forEach((campo) => {
            campo.setCustomValidity(campo.value.length >= 8 && campo.value.length <= 128 ?
                "" : "Usa entre 8 y 128 caracteres.");
        });
        const pais = obtenerCampo(formulario, "company-country");
        const identificacion = obtenerCampo(formulario, "company-tax-id");
        if (pais && identificacion) {
            const esPeru = pais.value.trim().toUpperCase() === "PE";
            const patron = esPeru ? /^\d{11}$/ : /^[a-zA-Z0-9]{1,32}$/;
            identificacion.inputMode = esPeru ? "numeric" : "text";
            identificacion.setCustomValidity(patron.test(identificacion.value.trim()) ? "" :
                esPeru ? "El RUC debe tener 11 dígitos." : "Usa entre 1 y 32 letras o números.");
        }
        const telefono = obtenerCampo(formulario, "recruiter-phone");
        if (telefono && telefono.value.trim()) {
            const valor = telefono.value.trim();
            const cantidadDigitos = valor.replace(/\D/g, "").length;
            telefono.setCustomValidity(/^\+?[0-9 ().-]+$/.test(valor) && cantidadDigitos >= 6 && cantidadDigitos <= 20 ?
                "" : "Ingresa un teléfono válido, con entre 6 y 20 dígitos.");
        }
        const sitioWeb = obtenerCampo(formulario, "company-website");
        if (sitioWeb) {
            const valor = sitioWeb.value.trim();
            let esValido = !valor;
            if (valor) {
                try {
                    const direccion = new URL(valor);
                    esValido = /^https?:\/\/[^/]+/i.test(valor) &&
                        ["http:", "https:"].includes(direccion.protocol) && Boolean(direccion.hostname);
                } catch { esValido = false; }
            }
            sitioWeb.setCustomValidity(esValido ? "" : "Ingresa un sitio web válido con http:// o https:// y un dominio.");
        }
    };

    const actualizarPeriodoAcademico = (formulario, limpiar = false) => {
        const condicion = obtenerCampo(formulario, "applicant-academic-status");
        const periodo = obtenerCampo(formulario, "applicant-cycle");
        if (!condicion || !periodo) return;
        const esEstudiante = condicion.value === "estudiante";
        const etiqueta = formulario.querySelector('label[for="applicant-cycle"]');
        etiqueta.textContent = esEstudiante ? "Ciclo actual" : "Año de egreso";
        periodo.min = esEstudiante ? "1" : "1950";
        periodo.max = esEstudiante ? "30" : String(new Date().getFullYear());
        periodo.placeholder = esEstudiante ? "Ej. 6" : "Ej. 2025";
        if (limpiar) periodo.value = "";
    };

    const obtenerMensajeError = (estado, respuesta) => {
        if (estado === 409) return respuesta?.mensaje || "El correo o la identificación fiscal ya están registrados.";
        if (estado === 400) {
            const detalles = Object.values(respuesta?.errores || {}).filter((mensaje) => typeof mensaje === "string");
            return detalles.length ? detalles.slice(0, 3).join(" ") :
                respuesta?.mensaje || "Revisa los datos ingresados. El servidor no pudo validarlos.";
        }
        if (estado === 503) return "El registro no está disponible en este momento. Inténtalo más tarde.";
        return "No se pudo crear la cuenta. Inténtalo nuevamente.";
    };

    window.validarCamposRegistro = validarCamposComplementarios;

    window.registrarCuenta = async (formulario) => {
        if (formulario.dataset.enviando === "true" || formulario.dataset.registrado === "true") return;
        validarCamposComplementarios(formulario);
        if (!formulario.checkValidity()) {
            mostrarEstado(formulario, "Revisa los campos indicados.", true);
            return;
        }
        const datos = construirDatos(formulario);
        const ruta = formulario.dataset.tipoRegistro === "postulante" ? "postulantes" : "empresas";
        const controles = [...formulario.querySelectorAll("input, select, textarea, button")];
        const estadosAnteriores = controles.map((campo) => campo.disabled);
        const botonEnviar = formulario.querySelector('button[type="submit"]');
        const controlador = new AbortController();
        const temporizador = window.setTimeout(() => controlador.abort(), 30000);
        formulario.dataset.enviando = "true";
        formulario.setAttribute("aria-busy", "true");
        controles.forEach((campo) => { campo.disabled = true; });
        botonEnviar.textContent = "Creando cuenta…";
        mostrarEstado(formulario, "Guardando tu registro…");

        try {
            const respuesta = await fetch(`${obtenerBaseApi()}/api/registro/${ruta}`, {
                method: "POST",
                headers: { "Content-Type": "application/json", Accept: "application/json" },
                body: JSON.stringify(datos),
                signal: controlador.signal
            });
            let contenido = null;
            try { contenido = await respuesta.json(); } catch { /* Algunas respuestas no contienen JSON. */ }
            if (respuesta.status !== 201) {
                mostrarEstado(formulario, obtenerMensajeError(respuesta.status, contenido), true);
                return;
            }
            formulario.dataset.registrado = "true";
            formulario.querySelectorAll('input[type="password"]').forEach((campo) => { campo.value = ""; });
            mostrarEstado(formulario, "Cuenta creada. Ya puedes iniciar sesión.");
            const enlaceAcceso = formulario.querySelector("[data-registro-acceso]");
            if (enlaceAcceso) enlaceAcceso.hidden = false;
        } catch (error) {
            const mensaje = error.name === "AbortError" ?
                "La solicitud tardó demasiado. Verifica la conexión antes de intentar otra vez; el registro pudo haberse guardado." :
                "No se pudo contactar al servidor. Verifica que esté iniciado y vuelve a intentarlo.";
            mostrarEstado(formulario, mensaje, true);
        } finally {
            window.clearTimeout(temporizador);
            formulario.dataset.enviando = "false";
            formulario.removeAttribute("aria-busy");
            const registrado = formulario.dataset.registrado === "true";
            controles.forEach((campo, indice) => { campo.disabled = registrado || estadosAnteriores[indice]; });
            botonEnviar.textContent = registrado ? "Cuenta creada" : "Crear cuenta";
        }
    };

    document.addEventListener("DOMContentLoaded", () => {
        document.querySelectorAll("[data-tipo-registro]").forEach((formulario) => {
            const condicion = obtenerCampo(formulario, "applicant-academic-status");
            condicion?.addEventListener("change", () => actualizarPeriodoAcademico(formulario, true));
            actualizarPeriodoAcademico(formulario);
            formulario.addEventListener("input", () => validarCamposComplementarios(formulario));
            formulario.addEventListener("change", () => validarCamposComplementarios(formulario));
            validarCamposComplementarios(formulario);
        });
    });
})();
