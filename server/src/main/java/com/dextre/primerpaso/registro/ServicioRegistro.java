package com.dextre.primerpaso.registro;

import java.net.URI;
import java.time.Year;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.primerpaso.registro.DatosRegistro.RespuestaRegistro;
import com.dextre.primerpaso.registro.DatosRegistro.SolicitudEmpresa;
import com.dextre.primerpaso.registro.DatosRegistro.SolicitudPostulante;
import com.dextre.primerpaso.registro.RepositorioRegistro.MiembroEmpresa;
import com.dextre.primerpaso.registro.RepositorioRegistro.PerfilEmpresa;
import com.dextre.primerpaso.registro.RepositorioRegistro.PerfilPostulante;

@Service
public class ServicioRegistro {

    private static final Map<String, String> HABILIDADES = Map.of(
            "python", "Python", "sql", "SQL", "java", "Java", "excel", "Excel", "figma", "Figma", "git", "Git");
    private static final Map<String, String> AREAS = Map.of(
            "technology", "Tecnología", "administration", "Administración", "marketing", "Marketing",
            "design", "Diseño", "finance", "Finanzas", "operations", "Operaciones", "sales", "Ventas",
            "logistics", "Logística", "human-resources", "Recursos humanos");
    private static final Map<String, String> SECTORES = Map.of(
            "technology", "Tecnología", "services", "Servicios", "commerce", "Comercio",
            "industry", "Industria", "other", "Otro");

    private final RepositorioRegistro repositorio;
    private final CodificadorContrasena codificador;

    public ServicioRegistro(RepositorioRegistro repositorio, CodificadorContrasena codificador) {
        this.repositorio = repositorio;
        this.codificador = codificador;
    }

    @Transactional
    public RespuestaRegistro registrarPostulante(SolicitudPostulante solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("Completa los datos del registro.");
        }
        validarCuenta(solicitud.correo(), solicitud.contrasena(), solicitud.aceptaTerminos());
        PerfilPostulante perfil = normalizarPostulante(solicitud);
        List<String> habilidades = resolverOpciones(solicitud.habilidades(), HABILIDADES, "habilidades");
        List<String> areas = resolverOpciones(solicitud.intereses(), AREAS, "intereses laborales");
        String correo = normalizarCorreo(solicitud.correo());
        String contrasenaCodificada = codificador.codificar(solicitud.contrasena());

        long idUsuario = repositorio.crearUsuario(correo, contrasenaCodificada);
        long idCarrera = repositorio.resolverCarrera(perfil.carrera());
        repositorio.crearPostulante(idUsuario, idCarrera, perfil);
        for (String habilidad : habilidades) {
            repositorio.vincularHabilidad(idUsuario, repositorio.resolverHabilidad(habilidad));
        }
        for (String area : areas) {
            repositorio.vincularAreaPostulante(idUsuario, repositorio.resolverArea(area));
        }
        return new RespuestaRegistro(idUsuario, "postulante", "Tu cuenta de postulante se registró correctamente.");
    }

    @Transactional
    public RespuestaRegistro registrarEmpresa(SolicitudEmpresa solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("Completa los datos del registro.");
        }
        validarCuenta(solicitud.correo(), solicitud.contrasena(), solicitud.aceptaTerminos());
        PerfilEmpresa perfil = normalizarEmpresa(solicitud);
        MiembroEmpresa miembro = new MiembroEmpresa(textoObligatorio(solicitud.nombres(), "nombres"),
                textoObligatorio(solicitud.apellidos(), "apellidos"), textoObligatorio(solicitud.telefono(), "teléfono"));
        List<String> areas = resolverOpciones(solicitud.intereses(), AREAS, "intereses laborales");
        String correo = normalizarCorreo(solicitud.correo());
        String contrasenaCodificada = codificador.codificar(solicitud.contrasena());

        long idUsuario = repositorio.crearUsuario(correo, contrasenaCodificada);
        long idEmpresa = repositorio.crearEmpresa(perfil);
        repositorio.crearMiembroEmpresa(idEmpresa, idUsuario, miembro);
        for (String area : areas) {
            repositorio.vincularAreaEmpresa(idEmpresa, repositorio.resolverArea(area));
        }
        return new RespuestaRegistro(idUsuario, "empresa", "Tu cuenta de empresa se registró correctamente.");
    }

    private PerfilPostulante normalizarPostulante(SolicitudPostulante solicitud) {
        String condicion = textoObligatorio(solicitud.condicionAcademica(), "condición académica")
                .toLowerCase(Locale.ROOT);
        validarDatosAcademicos(condicion, solicitud.cicloActual(), solicitud.anioEgreso());
        return new PerfilPostulante(textoObligatorio(solicitud.nombres(), "nombres"),
                textoObligatorio(solicitud.apellidos(), "apellidos"),
                textoObligatorio(solicitud.institucion(), "institución"),
                textoObligatorio(solicitud.carrera(), "carrera"), condicion,
                solicitud.cicloActual(), solicitud.anioEgreso(), Boolean.TRUE.equals(solicitud.aceptaComunicaciones()));
    }

    private PerfilEmpresa normalizarEmpresa(SolicitudEmpresa solicitud) {
        String pais = textoObligatorio(solicitud.codigoPais(), "país").toUpperCase(Locale.ROOT);
        String identificacion = textoObligatorio(solicitud.identificacionFiscal(), "identificación fiscal")
                .toUpperCase(Locale.ROOT);
        if (!pais.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("El código del país debe contener dos letras.");
        }
        if ("PE".equals(pais) && !identificacion.matches("[0-9]{11}")) {
            throw new IllegalArgumentException("El RUC debe contener 11 dígitos.");
        }
        String sector = SECTORES.get(textoObligatorio(solicitud.sector(), "sector").toLowerCase(Locale.ROOT));
        if (sector == null) {
            throw new IllegalArgumentException("Selecciona un sector empresarial válido.");
        }
        String sitioWeb = solicitud.sitioWeb() == null ? null : solicitud.sitioWeb().strip();
        if (sitioWeb != null && sitioWeb.isEmpty()) {
            sitioWeb = null;
        }
        if (sitioWeb != null) {
            validarSitioWeb(sitioWeb);
        }
        return new PerfilEmpresa(textoObligatorio(solicitud.nombreComercial(), "nombre comercial"), pais,
                identificacion, sector, textoObligatorio(solicitud.ciudad(), "ciudad"), sitioWeb);
    }

    private void validarSitioWeb(String sitioWeb) {
        String mensaje = "Ingresa un sitio web válido con http:// o https:// y un nombre de servidor.";
        URI direccion;
        try {
            direccion = URI.create(sitioWeb);
        } catch (IllegalArgumentException excepcion) {
            throw new IllegalArgumentException(mensaje);
        }
        boolean esquemaValido = "http".equalsIgnoreCase(direccion.getScheme())
                || "https".equalsIgnoreCase(direccion.getScheme());
        if (!esquemaValido || direccion.getHost() == null || direccion.getHost().isBlank()
                || sitioWeb.codePoints().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    private void validarDatosAcademicos(String condicion, Integer ciclo, Integer anioEgreso) {
        switch (condicion) {
            case "estudiante" -> {
                if (ciclo == null || ciclo < 1 || ciclo > 30 || anioEgreso != null) {
                    throw new IllegalArgumentException("Para estudiantes indica un ciclo entre 1 y 30 y omite el año de egreso.");
                }
            }
            case "egresado", "titulado" -> {
                if (ciclo != null || anioEgreso == null || anioEgreso < 1950 || anioEgreso > Year.now().getValue()) {
                    throw new IllegalArgumentException("Indica un año de egreso entre 1950 y el año actual y omite el ciclo.");
                }
            }
            default -> throw new IllegalArgumentException("Selecciona una condición académica válida.");
        }
    }

    private void validarCuenta(String correo, String contrasena, Boolean aceptaTerminos) {
        textoObligatorio(correo, "correo electrónico");
        if (contrasena == null || contrasena.isBlank() || contrasena.length() < DatosRegistro.MINIMO_CONTRASENA
                || contrasena.length() > DatosRegistro.MAXIMO_CONTRASENA) {
            throw new IllegalArgumentException(DatosRegistro.MENSAJE_CONTRASENA);
        }
        if (!Boolean.TRUE.equals(aceptaTerminos)) {
            throw new IllegalArgumentException("Debes aceptar los términos para registrarte.");
        }
    }

    private String normalizarCorreo(String correo) {
        return correo.strip().toLowerCase(Locale.ROOT);
    }

    private String textoObligatorio(String texto, String nombreCampo) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Completa el campo " + nombreCampo + ".");
        }
        return texto.strip();
    }

    private List<String> resolverOpciones(List<String> opciones, Map<String, String> catalogo, String nombreCampo) {
        if (opciones == null) {
            return List.of();
        }
        LinkedHashSet<String> nombres = new LinkedHashSet<>();
        for (String opcion : opciones) {
            String nombre = opcion == null ? null : catalogo.get(opcion.strip().toLowerCase(Locale.ROOT));
            if (nombre == null) {
                throw new IllegalArgumentException("Selecciona opciones válidas en " + nombreCampo + ".");
            }
            nombres.add(nombre);
        }
        return List.copyOf(nombres);
    }
}
