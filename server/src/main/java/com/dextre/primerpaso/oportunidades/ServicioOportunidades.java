package com.dextre.primerpaso.oportunidades;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.oportunidades.DatosOportunidades.Filtros;
import com.dextre.primerpaso.oportunidades.DatosOportunidades.Oportunidad;
import com.dextre.primerpaso.oportunidades.DatosOportunidades.PaginaOportunidades;
import com.dextre.primerpaso.vacantes.DatosVacantes.Catalogos;
import com.dextre.primerpaso.vacantes.RepositorioVacantes;

@Service
public class ServicioOportunidades {

    private static final int TAMANIO_PAGINA = 9;
    private final RepositorioOportunidades repositorio;
    private final RepositorioVacantes catalogos;
    private final Clock reloj;

    @Autowired
    public ServicioOportunidades(RepositorioOportunidades repositorio, RepositorioVacantes catalogos) {
        this(repositorio, catalogos, Clock.systemUTC());
    }

    ServicioOportunidades(RepositorioOportunidades repositorio, RepositorioVacantes catalogos, Clock reloj) {
        this.repositorio = repositorio;
        this.catalogos = catalogos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public PaginaOportunidades listar(Filtros filtros, int pagina) {
        if (filtros == null || pagina < 0 || filtros.busqueda().length() > 180 || filtros.ciudad().length() > 120
                || !Set.of("", "presencial", "remota", "hibrida").contains(filtros.modalidad())
                || (filtros.idCarrera() != null && filtros.idCarrera() <= 0)
                || (filtros.idHabilidad() != null && filtros.idHabilidad() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisa los filtros y el número de página.");
        }
        Instant ahora = reloj.instant();
        long total = repositorio.contar(filtros, ahora);
        int totalPaginas = (int) Math.min(Integer.MAX_VALUE, total / TAMANIO_PAGINA
                + (total % TAMANIO_PAGINA == 0 ? 0 : 1));
        int paginaActual = totalPaginas == 0 ? 0 : Math.min(pagina, totalPaginas - 1);
        return new PaginaOportunidades(repositorio.listar(filtros, paginaActual, TAMANIO_PAGINA, ahora),
                paginaActual, totalPaginas, total);
    }

    @Transactional(readOnly = true)
    public Oportunidad consultar(long idVacante) {
        if (idVacante <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador de la vacante no es válido.");
        }
        return repositorio.consultar(idVacante, reloj.instant()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Esta vacante ya no está disponible."));
    }

    @Transactional(readOnly = true)
    public Catalogos catalogos() {
        return catalogos.catalogos();
    }
}
