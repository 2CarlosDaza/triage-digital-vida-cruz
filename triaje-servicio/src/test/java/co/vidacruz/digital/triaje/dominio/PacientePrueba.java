package co.vidacruz.digital.triaje.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.vidacruz.digital.triaje.dominio.enumeracion.EstadoClinico;
import co.vidacruz.digital.triaje.dominio.enumeracion.Nivel;
import co.vidacruz.digital.triaje.dominio.excepcion.DatosInvalidosExcepcion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PacientePrueba {
    private static final Instant AHORA = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void registrarCreaPacienteConEstadoYFechasIniciales() {
        Paciente paciente =
                Paciente.registrar(
                        "id-1",
                        "Ana Cruz",
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
                        AHORA);

        assertEquals(EstadoClinico.REGISTRADO, paciente.estado());
        assertEquals(AHORA, paciente.estadoDesde());
        assertEquals(AHORA, paciente.fechaRegistro());
    }

    @Test
    void validaLimitesInclusivosDeLosRangosRf01() {
        assertPacienteValido(3, 0, 20, 60, 30, 70, "34.0", 0, 3);
        assertPacienteValido(100, 120, 250, 260, 160, 100, "42.0", 10, 15);
    }

    @Test
    void acumulaTodasLasViolacionesDeRf01EnUnaExcepcion() {
        String nombreLargo = "N".repeat(101);
        DatosInvalidosExcepcion excepcion =
                assertThrows(
                        DatosInvalidosExcepcion.class,
                        () ->
                                Paciente.registrar(
                                        "id-1",
                                        nombreLargo,
                                        121,
                                        19,
                                        59,
                                        161,
                                        69,
                                        new BigDecimal("42.1"),
                                        11,
                                        2,
                                        "consulta",
                                        Nivel.NO_URGENTE,
                                        AHORA));

        assertEquals(
                List.of(
                        "nombreCompleto",
                        "edad",
                        "frecuenciaCardiaca",
                        "presionSistolica",
                        "presionDiastolica",
                        "spo2",
                        "temperatura",
                        "dolor",
                        "gcs"),
                excepcion.camposInvalidos());
    }

    @Test
    void acumulaTemperaturaNulaConElRestoDeViolaciones() {
        DatosInvalidosExcepcion excepcion =
                assertThrows(
                        DatosInvalidosExcepcion.class,
                        () ->
                                Paciente.registrar(
                                        "id-1",
                                        "Ana Cruz",
                                        30,
                                        19,
                                        59,
                                        29,
                                        69,
                                        null,
                                        -1,
                                        2,
                                        "consulta",
                                        Nivel.NO_URGENTE,
                                        AHORA));
        assertEquals(
                List.of(
                        "frecuenciaCardiaca",
                        "presionSistolica",
                        "presionDiastolica",
                        "spo2",
                        "temperatura",
                        "dolor",
                        "gcs"),
                excepcion.camposInvalidos());
    }

    @Test
    void signosVitalesEsAutovalidado() {
        DatosInvalidosExcepcion excepcion =
                assertThrows(
                        DatosInvalidosExcepcion.class,
                        () -> new SignosVitales(19, 59, 60, 69, new BigDecimal("33.9"), 11, 2));
        assertEquals(
                List.of(
                        "frecuenciaCardiaca",
                        "presionSistolica",
                        "presionDiastolica",
                        "spo2",
                        "temperatura",
                        "dolor",
                        "gcs"),
                excepcion.camposInvalidos());
    }

    @Test
    void rechazaLimitesInferioresYSuperioresDeSignosVitales() {
        assertThrows(
                DatosInvalidosExcepcion.class,
                () -> new SignosVitales(19, 59, 29, 69, new BigDecimal("33.9"), -1, 2));
        assertThrows(
                DatosInvalidosExcepcion.class,
                () -> new SignosVitales(251, 261, 161, 101, new BigDecimal("42.1"), 11, 16));
    }

    @Test
    void acumulaNombreEdadEIdentificadorInvalidos() {
        DatosInvalidosExcepcion excepcion =
                assertThrows(
                        DatosInvalidosExcepcion.class,
                        () ->
                                Paciente.registrar(
                                        " ",
                                        "AB",
                                        -1,
                                        70,
                                        120,
                                        80,
                                        99,
                                        new BigDecimal("36.5"),
                                        2,
                                        15,
                                        "consulta",
                                        Nivel.NO_URGENTE,
                                        AHORA));

        assertEquals(List.of("nombreCompleto", "edad", "id"), excepcion.camposInvalidos());
    }

    @Test
    void reconstruirRechazaNombreYEdadInvalidos() {
        SignosVitales signos = new SignosVitales(70, 120, 80, 99, new BigDecimal("36.5"), 2, 15);
        DatosInvalidosExcepcion excepcion =
                assertThrows(
                        DatosInvalidosExcepcion.class,
                        () ->
                                Paciente.reconstruir(
                                        "id-1",
                                        "AB",
                                        -1,
                                        signos,
                                        "consulta",
                                        Nivel.NO_URGENTE,
                                        EstadoClinico.REGISTRADO,
                                        AHORA,
                                        false,
                                        AHORA));
        assertEquals(List.of("nombreCompleto", "edad"), excepcion.camposInvalidos());
    }

    @Test
    void reconstruirEsUnaFabricaNombradaYConservaLosDatosPersistidos() {
        Paciente original =
                Paciente.registrar(
                        "id-1",
                        "Ana Cruz",
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
                        AHORA);
        original.cambiarEstado(EstadoClinico.EN_CONSULTA, AHORA.plusSeconds(5));
        Paciente reconstruido =
                Paciente.reconstruir(
                        original.id(),
                        original.nombreCompleto(),
                        original.edad(),
                        original.signosVitales(),
                        original.quejaPrincipal(),
                        original.nivel(),
                        original.estado(),
                        original.estadoDesde(),
                        original.escalado(),
                        original.fechaRegistro());

        assertEquals(original.estado(), reconstruido.estado());
        assertEquals(original.estadoDesde(), reconstruido.estadoDesde());
        assertEquals(original.fechaRegistro(), reconstruido.fechaRegistro());
    }

    private void assertPacienteValido(
            int longitudNombre,
            int edad,
            int frecuencia,
            int sistolica,
            int diastolica,
            int spo2,
            String temperatura,
            int dolor,
            int gcs) {
        Paciente.registrar(
                "id-1",
                "A".repeat(longitudNombre),
                edad,
                frecuencia,
                sistolica,
                diastolica,
                spo2,
                new BigDecimal(temperatura),
                dolor,
                gcs,
                "consulta",
                Nivel.NO_URGENTE,
                AHORA);
    }
}
