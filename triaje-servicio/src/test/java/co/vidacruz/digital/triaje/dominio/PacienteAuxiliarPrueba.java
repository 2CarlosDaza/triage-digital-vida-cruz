package co.vidacruz.digital.triaje.dominio;

import co.vidacruz.digital.triaje.dominio.enumeracion.EstadoClinico;
import co.vidacruz.digital.triaje.dominio.enumeracion.Nivel;
import java.math.BigDecimal;
import java.time.Instant;

final class PacienteAuxiliarPrueba {
    static final Instant REGISTRO = Instant.parse("2026-01-01T00:00:00Z");

    private PacienteAuxiliarPrueba() {}

    static Paciente paciente(EstadoClinico estado, boolean escalado) {
        Paciente paciente =
                Paciente.registrar(
                        "id-1",
                        "Nombre Apellido",
                        30,
                        70,
                        120,
                        80,
                        99,
                        new BigDecimal("36.5"),
                        2,
                        15,
                        "consulta",
                        Nivel.NO_URGENTE,
                        REGISTRO);
        if (estado != EstadoClinico.REGISTRADO) {
            paciente.cambiarEstado(estado, REGISTRO.plusSeconds(1));
        }
        if (escalado) paciente.escalar(REGISTRO.plusSeconds(2));
        return paciente;
    }
}
