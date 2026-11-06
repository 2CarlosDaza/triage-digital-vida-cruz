package co.vidacruz.digital.triaje.dominio;

import java.math.BigDecimal;

public final class ReglaNivel3 implements ReglaClasificacion {
    private static final BigDecimal TEMPERATURA_MINIMA = new BigDecimal("38.0");
    private static final BigDecimal TEMPERATURA_MAXIMA = new BigDecimal("38.5");

    @Override
    public Nivel nivel() {
        return Nivel.URGENTE_2;
    }

    @Override
    public boolean aplica(SignosVitales signos) {
        return (signos.frecuenciaCardiaca() >= 90 && signos.frecuenciaCardiaca() <= 120)
                || (signos.temperatura().compareTo(TEMPERATURA_MINIMA) >= 0
                        && signos.temperatura().compareTo(TEMPERATURA_MAXIMA) < 0)
                || signos.dolor() >= 7;
    }
}
