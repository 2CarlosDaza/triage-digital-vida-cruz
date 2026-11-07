package co.vidacruz.digital.triaje.dominio;

import co.vidacruz.digital.triaje.dominio.enumeracion.Nivel;
import java.math.BigDecimal;

public final class ReglaNivel2 implements ReglaClasificacion {
    private static final BigDecimal TEMPERATURA_LIMITE = new BigDecimal("38.5");

    @Override
    public Nivel nivel() {
        return Nivel.URGENTE;
    }

    @Override
    public boolean aplica(SignosVitales signos) {
        return (signos.spo2() >= 90 && signos.spo2() <= 93)
                || (signos.presionSistolica() >= 90 && signos.presionSistolica() <= 100)
                || signos.temperatura().compareTo(TEMPERATURA_LIMITE) >= 0
                || (signos.gcs() >= 9 && signos.gcs() <= 13);
    }
}
