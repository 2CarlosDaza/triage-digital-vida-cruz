package co.vidacruz.digital.triaje.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.vidacruz.digital.triaje.dominio.enumeracion.EstadoClinico;
import co.vidacruz.digital.triaje.dominio.excepcion.TransicionInvalidaExcepcion;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EstadoClinicoPrueba {
    @Test
    void debePermitirTransicionesClinicasValidas() {
        assertEquals(
                EstadoClinico.EN_CONSULTA,
                EstadoClinico.REGISTRADO.cambiarEstado(EstadoClinico.EN_CONSULTA));
        assertEquals(
                EstadoClinico.ALTA, EstadoClinico.REGISTRADO.cambiarEstado(EstadoClinico.ALTA));
        assertEquals(
                EstadoClinico.ALTA, EstadoClinico.EN_CONSULTA.cambiarEstado(EstadoClinico.ALTA));
    }

    @Test
    void debeRechazarTransicionAlMismoEstado() {
        for (EstadoClinico estado : EstadoClinico.values()) {
            assertThrows(TransicionInvalidaExcepcion.class, () -> estado.cambiarEstado(estado));
        }
    }

    @Test
    void debeRechazarTransicionesInvalidasYDestinoNulo() {
        assertThrows(
                TransicionInvalidaExcepcion.class,
                () -> EstadoClinico.EN_CONSULTA.cambiarEstado(EstadoClinico.REGISTRADO));
        assertThrows(
                TransicionInvalidaExcepcion.class,
                () -> EstadoClinico.ALTA.cambiarEstado(EstadoClinico.EN_CONSULTA));
        assertThrows(
                TransicionInvalidaExcepcion.class,
                () -> EstadoClinico.ALTA.cambiarEstado(EstadoClinico.REGISTRADO));
        assertThrows(
                TransicionInvalidaExcepcion.class,
                () -> EstadoClinico.REGISTRADO.cambiarEstado(null));
    }

    @Test
    void cambiarEstadoActualizaEstadoDesdeSoloEnTransicionValida() {
        Instant ahora = Instant.parse("2026-01-01T00:00:00Z");
        Paciente paciente = PacienteAuxiliarPrueba.paciente(EstadoClinico.REGISTRADO, false);
        paciente.cambiarEstado(EstadoClinico.EN_CONSULTA, ahora);
        assertEquals(EstadoClinico.EN_CONSULTA, paciente.estado());
        assertEquals(ahora, paciente.estadoDesde());
        assertThrows(
                TransicionInvalidaExcepcion.class,
                () -> paciente.cambiarEstado(EstadoClinico.EN_CONSULTA, ahora.plusSeconds(1)));
        assertEquals(ahora, paciente.estadoDesde());
    }
}
