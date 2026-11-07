package co.vidacruz.digital.triaje.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.vidacruz.digital.triaje.dominio.enumeracion.Nivel;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClasificadorPrueba {
    private final Clasificador clasificador =
            new Clasificador(
                    List.of(new ReglaNivel3(), new ReglaNivel2(), new ReglaNivel1()),
                    List.of(
                            "documentos",
                            "recolección de resultados",
                            "renovación de recetario",
                            "vacunación",
                            "seguimiento"));

    @ParameterizedTest
    @CsvSource({"39,1", "40,4", "89,4", "90,3", "120,3", "121,1"})
    void debeClasificarLimitesDeFrecuenciaCardiaca(int frecuencia, int nivel) {
        assertNivel(nivel, signos(frecuencia, 150, 97, "36.8", 0, 15), "consulta");
    }

    @ParameterizedTest
    @CsvSource({"89,1", "90,2", "93,2", "94,4"})
    void debeClasificarLimitesDeSaturacion(int spo2, int nivel) {
        assertNivel(nivel, signos(70, 150, spo2, "36.8", 0, 15), "consulta");
    }

    @ParameterizedTest
    @CsvSource({"89,1", "90,2", "100,2", "101,4"})
    void debeClasificarLimitesDePresionSistolica(int presion, int nivel) {
        assertNivel(nivel, signos(70, presion, 97, "36.8", 0, 15), "consulta");
    }

    @ParameterizedTest
    @CsvSource({"37.9,4", "38.0,3", "38.4,3", "38.5,2"})
    void debeClasificarLimitesDeTemperatura(String temperatura, int nivel) {
        assertNivel(nivel, signos(70, 150, 97, temperatura, 0, 15), "consulta");
    }

    @ParameterizedTest
    @CsvSource({"8,1", "9,2", "13,2", "14,4", "3,1"})
    void debeClasificarLimitesDeGcs(int gcs, int nivel) {
        assertNivel(nivel, signos(70, 150, 97, "36.8", 0, gcs), "consulta");
    }

    @ParameterizedTest
    @CsvSource({"6,4", "7,3"})
    void debeClasificarLimitesDeDolor(int dolor, int nivel) {
        assertNivel(nivel, signos(70, 150, 97, "36.8", dolor, 15), "consulta");
    }

    @Test
    void debeElegirNivelDosCuandoCoincidenNivelesDosYTres() {
        assertEquals(
                Nivel.URGENTE,
                clasificador.clasificar(signos(90, 90, 91, "38.0", 7, 10), "consulta"));
    }

    @Test
    void debeElegirNivelUnoCuandoCoincidenNivelesUnoYDos() {
        assertEquals(
                Nivel.CRITICO,
                clasificador.clasificar(signos(39, 90, 91, "38.5", 7, 8), "consulta"));
    }

    @Test
    void debeAplicarNivelCincoConFraseAdministrativaNormalizada() {
        assertEquals(
                Nivel.NO_URGENTE_2,
                clasificador.clasificar(signosBase(), "RENOVACIÓN de recetario"));
    }

    @Test
    void debeNormalizarEspaciosDoblesYSaltosDeLineaEnLaQueja() {
        assertEquals(
                Nivel.NO_URGENTE_2,
                clasificador.clasificar(signosBase(), " renovación   de\nrecetario "));
    }

    @Test
    void noDebeAplicarNivelCincoCuandoLaFraseEsParteDeOtraPalabra() {
        assertEquals(Nivel.NO_URGENTE, clasificador.clasificar(signosBase(), "predocumentos"));
    }

    @Test
    void noDebeCoincidirUnaPalabraEnPluralConLaQuejaAdministrativaSingular() {
        assertEquals(Nivel.NO_URGENTE, clasificador.clasificar(signosBase(), "seguimientos"));
    }

    @Test
    void rechazaQuejasAdministrativasNulasEnElConstructor() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new Clasificador(
                                List.of(new ReglaNivel1(), new ReglaNivel2(), new ReglaNivel3()),
                                Arrays.asList("documentos", null)));
    }

    @Test
    void rechazaEstrategiasFueraDeLosNivelesUnoATres() {
        ReglaClasificacion reglaNivelCuatro =
                new ReglaClasificacion() {
                    @Override
                    public Nivel nivel() {
                        return Nivel.NO_URGENTE;
                    }

                    @Override
                    public boolean aplica(SignosVitales signosVitales) {
                        return true;
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Clasificador(
                                List.of(
                                        new ReglaNivel1(),
                                        new ReglaNivel2(),
                                        new ReglaNivel3(),
                                        reglaNivelCuatro),
                                List.of("documentos")));
    }

    @Test
    void noDebeAplicarNivelCincoAUnNivelTres() {
        assertEquals(
                Nivel.URGENTE_2,
                clasificador.clasificar(signos(90, 150, 97, "36.8", 0, 15), "vacunación"));
    }

    @Test
    void noDebeAplicarNivelCincoALosNivelesUnoDosNiTres() {
        assertEquals(
                Nivel.CRITICO,
                clasificador.clasificar(signos(39, 150, 97, "36.8", 0, 15), "documentos"));
        assertEquals(
                Nivel.URGENTE,
                clasificador.clasificar(signos(70, 150, 91, "36.8", 0, 15), "documentos"));
        assertEquals(
                Nivel.URGENTE_2,
                clasificador.clasificar(signos(90, 150, 97, "36.8", 0, 15), "documentos"));
    }

    @Test
    void debeClasificarLosCasosDeEjemploE1AE6() {
        assertEquals(
                Nivel.CRITICO,
                clasificador.clasificar(signos(130, 180, 97, "36.8", 8, 15), "Dolor torácico"));
        assertEquals(
                Nivel.URGENTE,
                clasificador.clasificar(
                        signos(95, 150, 91, "37.0", 4, 15), "Dificultad para respirar"));
        assertEquals(
                Nivel.URGENTE_2,
                clasificador.clasificar(signos(88, 130, 98, "38.2", 6, 15), "Fiebre"));
        assertEquals(
                Nivel.NO_URGENTE,
                clasificador.clasificar(signos(72, 120, 99, "36.5", 2, 15), "Herida por cortar"));
        assertEquals(
                Nivel.NO_URGENTE_2,
                clasificador.clasificar(
                        signos(70, 115, 99, "36.4", 0, 15), "Renovación de recetario"));
        assertEquals(
                Nivel.CRITICO,
                clasificador.clasificar(signos(35, 80, 85, "35.9", 0, 6), "Inconsciente"));
    }

    @Test
    void debeExponerSlaSegunRn02() {
        assertEquals(
                List.of(5, 10, 30, 60, 120),
                List.of(
                        Nivel.CRITICO.slaMinutos(),
                        Nivel.URGENTE.slaMinutos(),
                        Nivel.URGENTE_2.slaMinutos(),
                        Nivel.NO_URGENTE.slaMinutos(),
                        Nivel.NO_URGENTE_2.slaMinutos()));
    }

    private void assertNivel(int esperado, SignosVitales signos, String queja) {
        assertEquals(esperado, clasificador.clasificar(signos, queja).numero());
    }

    private SignosVitales signosBase() {
        return signos(70, 150, 97, "36.8", 0, 15);
    }

    private SignosVitales signos(
            int frecuencia, int presion, int spo2, String temperatura, int dolor, int gcs) {
        return new SignosVitales(
                frecuencia,
                presion,
                Math.min(80, presion - 1),
                spo2,
                new BigDecimal(temperatura),
                dolor,
                gcs);
    }
}
