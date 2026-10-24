package com.dextre.primerpaso.cuenta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Year;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.dextre.primerpaso.cuenta.DatosCuenta.Cuenta;
import com.dextre.primerpaso.cuenta.DatosCuenta.DatosPersonales;
import com.dextre.primerpaso.cuenta.DatosCuenta.Empresa;
import com.dextre.primerpaso.cuenta.DatosCuenta.Formacion;
import com.dextre.primerpaso.cuenta.DatosCuenta.SolicitudCuenta;
import com.dextre.primerpaso.sesion.DatosSesion.UsuarioSesion;

class ServicioCuentaTests {

    private static final UsuarioSesion POSTULANTE = new UsuarioSesion(7, "Ana", "Pérez", "ana@example.test",
            "postulante", null, null, null);
    private RepositorioCuenta repositorio;
    private ServicioCuenta servicio;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioCuenta.class);
        servicio = new ServicioCuenta(repositorio);
        when(repositorio.consultar(POSTULANTE, true)).thenReturn(Optional.of(cuentaPostulante()));
        when(repositorio.consultar(POSTULANTE, false)).thenReturn(Optional.of(cuentaPostulante()));
        when(repositorio.carreras()).thenReturn(List.of());
        when(repositorio.carreraActiva(3)).thenReturn(true);
    }

    @Test
    void guardaDatosPersonalesYFormacionDeLaCuentaActual() {
        SolicitudCuenta datos = postulante("v1", "estudiante", 6, null);
        servicio.actualizar(POSTULANTE, datos);
        verify(repositorio).guardarPostulante(7, datos);
        verify(repositorio, never()).guardarMiembro(anyLong(), anyLong(), any());
        assertEquals("ana@example.test", datos.correo());
        assertEquals("Ana", datos.nombres());
    }

    @Test
    void rechazaUnaEdicionConVersionAnteriorSinSobrescribirDatos() {
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> servicio.actualizar(POSTULANTE, postulante("anterior", "estudiante", 6, null)))
                .getStatusCode().value());
        verify(repositorio, never()).guardarPostulante(anyLong(), any());
    }

    @Test
    void validaLosCasosDeCicloYAnioDeEgresoAntesDeGuardar() {
        for (SolicitudCuenta datos : List.of(postulante("v1", "estudiante", 0, null),
                postulante("v1", "estudiante", 6, 2026), postulante("v1", "egresado", 6, 2026),
                postulante("v1", "titulado", null, Year.now().getValue() + 1))) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> servicio.actualizar(POSTULANTE, datos)).getStatusCode().value());
        }
        verify(repositorio, never()).guardarPostulante(anyLong(), any());
    }

    @Test
    void permiteCambiarDeEstudianteAEgresado() {
        SolicitudCuenta datos = postulante("v1", "egresado", null, 2026);
        servicio.actualizar(POSTULANTE, datos);
        verify(repositorio).guardarPostulante(7, datos);
    }

    @Test
    void permiteConservarUnaCarreraInactivaPeroRechazaCambiarAOtraInactiva() {
        when(repositorio.carreraActiva(3)).thenReturn(false);
        servicio.actualizar(POSTULANTE, postulante("v1", "estudiante", 6, null));
        var datos = new SolicitudCuenta("Ana", "Pérez", "ana@example.test", null, null, "Universidad", 8L,
                "estudiante", 6, null, null, null, null, "v1");
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> servicio.actualizar(POSTULANTE, datos)).getStatusCode().value());
    }

    @Test
    void unReclutadorPuedeEditarSuContactoSinCambiarLaEmpresa() {
        Cuenta cuenta = cuentaEmpresa("reclutador");
        when(repositorio.consultar(cuenta.usuario(), true)).thenReturn(Optional.of(cuenta));
        when(repositorio.consultar(cuenta.usuario(), false)).thenReturn(Optional.of(cuenta));
        SolicitudCuenta datos = empresa("Empresa", "https://empresa.example");
        servicio.actualizar(cuenta.usuario(), datos);
        verify(repositorio).guardarMiembro(8, 9, datos);
        verify(repositorio, never()).guardarEmpresa(anyLong(), any());
    }

    @Test
    void unReclutadorNoPuedeModificarLaEmpresaAunqueCambieElFormulario() {
        Cuenta cuenta = cuentaEmpresa("reclutador");
        when(repositorio.consultar(cuenta.usuario(), true)).thenReturn(Optional.of(cuenta));
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> servicio.actualizar(cuenta.usuario(), empresa("Otra empresa", "https://empresa.example")))
                .getStatusCode().value());
        verify(repositorio, never()).guardarMiembro(anyLong(), anyLong(), any());
        verify(repositorio, never()).guardarEmpresa(anyLong(), any());
    }

    @Test
    void unAdministradorActualizaSuEmpresaYRechazaDireccionesInseguras() {
        Cuenta cuenta = cuentaEmpresa("administrador");
        when(repositorio.consultar(cuenta.usuario(), true)).thenReturn(Optional.of(cuenta));
        when(repositorio.consultar(cuenta.usuario(), false)).thenReturn(Optional.of(cuenta));
        SolicitudCuenta datos = empresa("Nombre actualizado", "https://empresa.example");
        servicio.actualizar(cuenta.usuario(), datos);
        verify(repositorio).guardarEmpresa(9, datos);
        for (String direccion : List.of("javascript:alert(1)", "https://", "https://usuario:clave@empresa.example")) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> servicio.actualizar(cuenta.usuario(), empresa("Empresa", direccion))).getStatusCode().value());
        }
    }

    @Test
    void revalidaLaMembresiaAntesDeModificarDatosAunqueLaSesionEsteAbierta() {
        when(repositorio.consultar(POSTULANTE, true)).thenReturn(Optional.empty());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> servicio.actualizar(POSTULANTE, postulante("v1", "estudiante", 6, null)))
                .getStatusCode().value());
        verify(repositorio, never()).guardarPostulante(anyLong(), any());
    }

    private SolicitudCuenta postulante(String version, String condicion, Integer ciclo, Integer anio) {
        return new SolicitudCuenta(" Ana ", " Pérez ", " ANA@example.test ", "+51 999 888 777", "Lima",
                "Universidad", 3L, condicion, ciclo, anio, null, null, null, version);
    }

    private SolicitudCuenta empresa(String nombre, String sitioWeb) {
        return new SolicitudCuenta("Ana", "Pérez", "ana@example.test", "+51 999 888 777", "Lima", null,
                null, null, null, null, nombre, "Tecnología", sitioWeb, "v1");
    }

    private Cuenta cuentaPostulante() {
        return new Cuenta(POSTULANTE, new DatosPersonales("Ana", "Pérez", "ana@example.test", null),
                new Formacion("Lima", "Universidad", 3, "Sistemas", "estudiante", 6, null), null, "v1", List.of());
    }

    private Cuenta cuentaEmpresa(String rol) {
        var usuario = new UsuarioSesion(8, "Ana", "Pérez", "ana@example.test", "empresa", 9L, "Empresa", rol);
        return new Cuenta(usuario, new DatosPersonales("Ana", "Pérez", "ana@example.test", null), null,
                new Empresa("Empresa", "Tecnología", "Lima", "https://empresa.example", "PE", "20123456789"),
                "v1", List.of());
    }
}
