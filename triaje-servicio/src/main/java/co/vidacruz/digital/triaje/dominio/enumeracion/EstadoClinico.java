package co.vidacruz.digital.triaje.dominio.enumeracion;

import co.vidacruz.digital.triaje.dominio.excepcion.TransicionInvalidaExcepcion;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum EstadoClinico {
    REGISTRADO,
    EN_CONSULTA,
    ALTA;

    private static final Map<EstadoClinico, Set<EstadoClinico>> TRANSICIONES =
            Map.of(
                    REGISTRADO, EnumSet.of(EN_CONSULTA, ALTA),
                    EN_CONSULTA, EnumSet.of(ALTA),
                    ALTA, EnumSet.noneOf(EstadoClinico.class));

    public EstadoClinico cambiarEstado(EstadoClinico destino) {
        if (destino == null || !TRANSICIONES.get(this).contains(destino)) {
            throw new TransicionInvalidaExcepcion(this, destino);
        }
        return destino;
    }
}
