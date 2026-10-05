package com.dextre.primerpaso.vacantes;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;
import com.dextre.primerpaso.vacantes.DatosVacantes.Catalogos;
import com.dextre.primerpaso.vacantes.DatosVacantes.PaginaVacantes;
import com.dextre.primerpaso.vacantes.DatosVacantes.SolicitudEstadoVacante;
import com.dextre.primerpaso.vacantes.DatosVacantes.SolicitudVacante;
import com.dextre.primerpaso.vacantes.DatosVacantes.Vacante;

@Service
public class ServicioVacantes {

    private static final int TAMANIO_PAGINA = 12;
    private static final Set<String> ESTADOS = Set.of("todos", "borrador", "publicada", "cerrada", "vencida");
    private static final Set<String> MODALIDADES = Set.of("presencial", "remota", "hibrida");
    private static final Instant FECHA_MAXIMA = Instant.parse("9999-12-31T23:59:59Z");
    private final RepositorioVacantes repositorio;
    private final Clock reloj;

    @Autowired
    public ServicioVacantes(RepositorioVacantes repositorio) {
        this(repositorio, Clock.systemUTC());
    }

    ServicioVacantes(RepositorioVacantes repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Transactional
    public Vacante crear(UsuarioSesion usuario, SolicitudVacante datos) {
        long idEmpresa = exigirEmpresa(usuario);
        Instant ahora = reloj.instant();
        validarDatos(datos, ahora);
        if (datos.version() != null) {
            throw new ReglaVacanteException(400, "Una vacante nueva no necesita una versión anterior.");
        }
        comprobarMembresia(usuario, idEmpresa, true);
        validarCatalogos(datos.idCarrera(), datos.habilidades());
        validarDatos(datos, reloj.instant());
        long idVacante = repositorio.crear(idEmpresa, usuario.idUsuario(), datos);
        repositorio.sustituirHabilidades(idEmpresa, idVacante, datos.habilidades());
        return obtenerVacante(idEmpresa, idVacante, false, reloj.instant());
    }

    @Transactional
    public Vacante editar(UsuarioSesion usuario, long idVacante, SolicitudVacante datos) {
        long idEmpresa = exigirEmpresa(usuario);
        validarIdentificador(idVacante);
        Instant ahora = reloj.instant();
        validarDatos(datos, ahora);
        comprobarMembresia(usuario, idEmpresa, true);
        Vacante vacante = obtenerVacante(idEmpresa, idVacante, true, ahora);
        ahora = reloj.instant();
        validarVersion(vacante, datos.version());
        if (!("borrador".equals(vacante.estado()) || ("publicada".equals(vacante.estado())
                && (vacante.fechaVencimiento() == null || vacante.fechaVencimiento().isAfter(ahora))))) {
            throw new ReglaVacanteException(409, "Solo puedes editar borradores o vacantes publicadas vigentes.");
        }
        if (vacante.fechaPublicacion() != null && !datos.fechaVencimiento().isAfter(vacante.fechaPublicacion())) {
            throw new ReglaVacanteException(400, "El vencimiento debe ser posterior a la publicación.");
        }
        validarCatalogos(datos.idCarrera(), datos.habilidades());
        validarDatos(datos, reloj.instant());
        repositorio.editar(idEmpresa, idVacante, datos);
        repositorio.sustituirHabilidades(idEmpresa, idVacante, datos.habilidades());
        return obtenerVacante(idEmpresa, idVacante, false, reloj.instant());
    }

    @Transactional
    public Vacante publicar(UsuarioSesion usuario, long idVacante, SolicitudEstadoVacante datos) {
        long idEmpresa = exigirEmpresa(usuario);
        validarIdentificador(idVacante);
        Instant ahora = reloj.instant();
        comprobarMembresia(usuario, idEmpresa, true);
        Vacante vacante = obtenerVacante(idEmpresa, idVacante, true, ahora);
        ahora = reloj.instant();
        validarVersion(vacante, datos == null ? null : datos.version());
        if (!"borrador".equals(vacante.estado())) {
            throw new ReglaVacanteException(409, "Solo puedes publicar una vacante en borrador.");
        }
        List<Long> habilidades = vacante.habilidades().stream().map(habilidad -> habilidad.id()).toList();
        SolicitudVacante solicitud = new SolicitudVacante(vacante.titulo(), vacante.descripcion(),
                vacante.idCarrera(), habilidades, vacante.modalidad(), vacante.ciudad(),
                vacante.codigoPais(), vacante.fechaVencimiento(), vacante.version());
        validarDatos(solicitud, ahora);
        validarCatalogos(solicitud.idCarrera(), habilidades);
        validarDatos(solicitud, reloj.instant());
        repositorio.publicar(idEmpresa, idVacante);
        return obtenerVacante(idEmpresa, idVacante, false, reloj.instant());
    }

    @Transactional
    public Vacante cerrar(UsuarioSesion usuario, long idVacante, SolicitudEstadoVacante datos) {
        long idEmpresa = exigirEmpresa(usuario);
        validarIdentificador(idVacante);
        Instant ahora = reloj.instant();
        comprobarMembresia(usuario, idEmpresa, true);
        Vacante vacante = obtenerVacante(idEmpresa, idVacante, true, ahora);
        validarVersion(vacante, datos == null ? null : datos.version());
        if (!"publicada".equals(vacante.estado())) {
            throw new ReglaVacanteException(409, "Solo puedes cerrar una vacante publicada.");
        }
        repositorio.cerrar(idEmpresa, idVacante);
        return obtenerVacante(idEmpresa, idVacante, false, reloj.instant());
    }

    @Transactional(readOnly = true)
    public Vacante consultar(UsuarioSesion usuario, long idVacante) {
        long idEmpresa = exigirEmpresa(usuario);
        validarIdentificador(idVacante);
        comprobarMembresia(usuario, idEmpresa, false);
        return obtenerVacante(idEmpresa, idVacante, false, reloj.instant());
    }

    @Transactional(readOnly = true)
    public PaginaVacantes listar(UsuarioSesion usuario, int pagina, String estado) {
        long idEmpresa = exigirEmpresa(usuario);
        String filtro = estado == null ? "todos" : estado.strip().toLowerCase(Locale.ROOT);
        if (pagina < 0 || !ESTADOS.contains(filtro)) {
            throw new ReglaVacanteException(400, "Selecciona una página y un estado de vacante válidos.");
        }
        comprobarMembresia(usuario, idEmpresa, false);
        Instant ahora = reloj.instant();
        long total = repositorio.contar(idEmpresa, filtro, ahora);
        int totalPaginas = (int) Math.min(Integer.MAX_VALUE, total / TAMANIO_PAGINA
                + (total % TAMANIO_PAGINA == 0 ? 0 : 1));
        int paginaActual = totalPaginas == 0 ? 0 : Math.min(pagina, totalPaginas - 1);
        return new PaginaVacantes(repositorio.listar(idEmpresa, paginaActual, TAMANIO_PAGINA, filtro, ahora),
                paginaActual, totalPaginas, total);
    }

    @Transactional(readOnly = true)
    public Catalogos catalogos(UsuarioSesion usuario) {
        long idEmpresa = exigirEmpresa(usuario);
        comprobarMembresia(usuario, idEmpresa, false);
        return repositorio.catalogos();
    }

    private long exigirEmpresa(UsuarioSesion usuario) {
        if (usuario == null || !"empresa".equals(usuario.tipoCuenta()) || usuario.idUsuario() <= 0
                || usuario.idEmpresa() == null || usuario.idEmpresa() <= 0
                || !("administrador".equals(usuario.rolEmpresa()) || "reclutador".equals(usuario.rolEmpresa()))) {
            throw new ReglaVacanteException(403, "Solo los miembros de una empresa pueden gestionar vacantes.");
        }
        return usuario.idEmpresa();
    }

    private void comprobarMembresia(UsuarioSesion usuario, long idEmpresa, boolean bloquear) {
        if (!repositorio.membresiaActiva(usuario.idUsuario(), idEmpresa, bloquear)) {
            throw new ReglaVacanteException(403, "Tu cuenta o membresía empresarial ya no está activa.");
        }
    }

    private Vacante obtenerVacante(long idEmpresa, long idVacante, boolean bloquear, Instant ahora) {
        return repositorio.consultar(idEmpresa, idVacante, bloquear, ahora)
                .orElseThrow(() -> new ReglaVacanteException(404, "La vacante no está disponible para tu empresa."));
    }

    private void validarIdentificador(long idVacante) {
        if (idVacante <= 0) {
            throw new ReglaVacanteException(400, "El identificador de la vacante no es válido.");
        }
    }

    private void validarVersion(Vacante vacante, Instant version) {
        if (version == null) {
            throw new ReglaVacanteException(400, "Recarga la vacante antes de guardar los cambios.");
        }
        if (!version.equals(vacante.version())) {
            throw new ReglaVacanteException(409, "Otra persona modificó la vacante. Recarga la página antes de continuar.");
        }
    }

    private void validarDatos(SolicitudVacante datos, Instant ahora) {
        if (datos == null) {
            throw new ReglaVacanteException(400, "Completa los datos de la vacante.");
        }
        validarTexto(datos.titulo(), 180, "título");
        validarTexto(datos.descripcion(), 12000, "descripción");
        validarTexto(datos.ciudad(), 120, "ciudad");
        if (datos.idCarrera() == null || datos.idCarrera() <= 0) {
            throw new ReglaVacanteException(400, "Selecciona una carrera válida.");
        }
        if (datos.modalidad() == null || !MODALIDADES.contains(datos.modalidad())) {
            throw new ReglaVacanteException(400, "Selecciona una modalidad válida.");
        }
        if (datos.codigoPais() == null || !datos.codigoPais().matches("[A-Z]{2}")) {
            throw new ReglaVacanteException(400, "El código del país debe contener dos letras.");
        }
        if (datos.fechaVencimiento() == null || !datos.fechaVencimiento().isAfter(ahora)
                || datos.fechaVencimiento().isAfter(FECHA_MAXIMA)) {
            throw new ReglaVacanteException(400, "La fecha de vencimiento debe ser válida y posterior al momento actual.");
        }
        List<Long> habilidades = datos.habilidades();
        if (habilidades == null || habilidades.isEmpty() || habilidades.size() > 30
                || habilidades.stream().anyMatch(idHabilidad -> idHabilidad == null || idHabilidad <= 0)
                || new HashSet<>(habilidades).size() != habilidades.size()) {
            throw new ReglaVacanteException(400, "Selecciona entre 1 y 30 habilidades válidas sin duplicados.");
        }
    }

    private void validarTexto(String texto, int longitud, String campo) {
        if (texto == null || texto.isBlank() || texto.length() > longitud) {
            throw new ReglaVacanteException(400, "Completa el campo " + campo + " sin superar " + longitud + " caracteres.");
        }
    }

    private void validarCatalogos(long idCarrera, List<Long> habilidades) {
        if (!repositorio.bloquearCarreraActiva(idCarrera)) {
            throw new ReglaVacanteException(400, "La carrera seleccionada ya no está disponible.");
        }
        if (repositorio.bloquearHabilidadesActivas(habilidades) != habilidades.size()) {
            throw new ReglaVacanteException(400, "Una habilidad seleccionada ya no está disponible.");
        }
    }
}
