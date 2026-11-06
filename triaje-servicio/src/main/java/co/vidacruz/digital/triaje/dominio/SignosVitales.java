package co.vidacruz.digital.triaje.dominio;

import co.vidacruz.digital.triaje.dominio.excepcion.DatosInvalidosExcepcion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Datos fisiológicos usados por las reglas de clasificación de RN-01. */
public record SignosVitales(
        int frecuenciaCardiaca,
        int presionSistolica,
        int presionDiastolica,
        int spo2,
        BigDecimal temperatura,
        int dolor,
        int gcs) {
    private static final BigDecimal TEMPERATURA_MINIMA = new BigDecimal("34.0");
    private static final BigDecimal TEMPERATURA_MAXIMA = new BigDecimal("42.0");

    public SignosVitales {
        List<String> camposInvalidos = new ArrayList<>();
        agregarViolaciones(
                camposInvalidos,
                frecuenciaCardiaca,
                presionSistolica,
                presionDiastolica,
                spo2,
                temperatura,
                dolor,
                gcs);
        if (!camposInvalidos.isEmpty()) {
            throw new DatosInvalidosExcepcion(camposInvalidos);
        }
    }

    static void agregarViolaciones(
            List<String> camposInvalidos,
            int frecuenciaCardiaca,
            int presionSistolica,
            int presionDiastolica,
            int spo2,
            BigDecimal temperatura,
            int dolor,
            int gcs) {
        if (frecuenciaCardiaca < 20 || frecuenciaCardiaca > 250)
            camposInvalidos.add("frecuenciaCardiaca");
        if (presionSistolica < 60 || presionSistolica > 260)
            camposInvalidos.add("presionSistolica");
        if (presionDiastolica < 30
                || presionDiastolica > 160
                || presionDiastolica >= presionSistolica) {
            camposInvalidos.add("presionDiastolica");
        }
        if (spo2 < 70 || spo2 > 100) camposInvalidos.add("spo2");
        if (temperatura == null
                || temperatura.compareTo(TEMPERATURA_MINIMA) < 0
                || temperatura.compareTo(TEMPERATURA_MAXIMA) > 0) {
            camposInvalidos.add("temperatura");
        }
        if (dolor < 0 || dolor > 10) camposInvalidos.add("dolor");
        if (gcs < 3 || gcs > 15) camposInvalidos.add("gcs");
    }
}
