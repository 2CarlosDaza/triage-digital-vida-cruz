package co.vidacruz.digital.triaje.dominio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EscalamientoPrueba {
    private final Escalamiento escalamiento = new Escalamiento();
    private final Duration sla = Duration.ofMinutes(60);

    @Test
    void noDebeEscalarAntesNiAlInstanteExactoDelSla() {
        Instant vencimiento = PacienteAuxiliarPrueba.REGISTRO.plus(sla);
        assertFalse(
                escalamiento.debeEscalar(
                        PacienteAuxiliarPrueba.paciente(EstadoClinico.REGISTRADO, false),
                        vencimiento.minusNanos(1),
                        sla));
        assertFalse(
                escalamiento.debeEscalar(
                        PacienteAuxiliarPrueba.paciente(EstadoClinico.REGISTRADO, false),
                        vencimiento,
                        sla));
    }

    @Test
    void debeEscalarCuandoSuperaElSla() {
        assertTrue(
                escalamiento.debeEscalar(
                        PacienteAuxiliarPrueba.paciente(EstadoClinico.REGISTRADO, false),
                        PacienteAuxiliarPrueba.REGISTRO.plus(sla).plusNanos(1),
                        sla));
    }

    @Test
    void noDebeEscalarSiYaEstaEscaladoOEnOtroEstado() {
        Instant ahora = PacienteAuxiliarPrueba.REGISTRO.plus(sla).plusSeconds(1);
        assertFalse(
                escalamiento.debeEscalar(
                        PacienteAuxiliarPrueba.paciente(EstadoClinico.REGISTRADO, true),
                        ahora,
                        sla));
        assertFalse(
                escalamiento.debeEscalar(
                        PacienteAuxiliarPrueba.paciente(EstadoClinico.EN_CONSULTA, false),
                        ahora,
                        sla));
        assertFalse(
                escalamiento.debeEscalar(
                        PacienteAuxiliarPrueba.paciente(EstadoClinico.ALTA, false), ahora, sla));
    }

    @Test
    void escalarEsIdempotenteYNoEscalaEstadosInactivos() {
        Paciente paciente = PacienteAuxiliarPrueba.paciente(EstadoClinico.REGISTRADO, false);
        paciente.escalar(PacienteAuxiliarPrueba.REGISTRO.plusSeconds(1));
        paciente.escalar(PacienteAuxiliarPrueba.REGISTRO.plusSeconds(2));
        assertTrue(paciente.escalado());
        Paciente alta = PacienteAuxiliarPrueba.paciente(EstadoClinico.ALTA, false);
        alta.escalar(PacienteAuxiliarPrueba.REGISTRO.plusSeconds(1));
        assertFalse(alta.escalado());
    }

    @Test
    void rechazaSlaNegativo() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        escalamiento.debeEscalar(
                                PacienteAuxiliarPrueba.paciente(EstadoClinico.REGISTRADO, false),
                                PacienteAuxiliarPrueba.REGISTRO,
                                Duration.ofSeconds(-1)));
    }
}
