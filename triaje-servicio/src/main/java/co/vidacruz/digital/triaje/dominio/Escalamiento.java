package co.vidacruz.digital.triaje.dominio;

import co.vidacruz.digital.triaje.dominio.enumeracion.EstadoClinico;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Decisión determinista de escalamiento; no consulta reloj ni servicios externos. */
public final class Escalamiento {
    public boolean debeEscalar(Paciente paciente, Instant ahora, Duration sla) {
        Objects.requireNonNull(paciente, "paciente es obligatorio");
        Objects.requireNonNull(ahora, "ahora es obligatorio");
        Objects.requireNonNull(sla, "sla es obligatorio");
        if (sla.isNegative()) throw new IllegalArgumentException("sla no puede ser negativo");
        return paciente.estado() == EstadoClinico.REGISTRADO
                && !paciente.escalado()
                && ahora.isAfter(paciente.fechaRegistro().plus(sla));
    }
}
