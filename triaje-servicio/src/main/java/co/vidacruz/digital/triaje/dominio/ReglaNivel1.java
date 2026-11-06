package co.vidacruz.digital.triaje.dominio;

import co.vidacruz.digital.triaje.dominio.enumeracion.Nivel;

public final class ReglaNivel1 implements ReglaClasificacion {
    @Override
    public Nivel nivel() {
        return Nivel.CRITICO;
    }

    @Override
    public boolean aplica(SignosVitales signos) {
        return signos.frecuenciaCardiaca() < 40
                || signos.frecuenciaCardiaca() > 120
                || signos.spo2() < 90
                || signos.presionSistolica() < 90
                || signos.gcs() <= 8;
    }
}
