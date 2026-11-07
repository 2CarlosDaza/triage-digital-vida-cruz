package co.vidacruz.digital.triaje.dominio;

import co.vidacruz.digital.triaje.dominio.enumeracion.EstadoClinico;
import co.vidacruz.digital.triaje.dominio.enumeracion.Nivel;
import co.vidacruz.digital.triaje.dominio.excepcion.DatosInvalidosExcepcion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Agregado raíz del servicio de triaje. */
public final class Paciente {
    private final String id;
    private final String nombreCompleto;
    private final int edad;
    private final SignosVitales signosVitales;
    private final String quejaPrincipal;
    private final Nivel nivel;
    private EstadoClinico estado;
    private Instant estadoDesde;
    private boolean escalado;
    private final Instant fechaRegistro;

    private Paciente(
            String id,
            String nombreCompleto,
            int edad,
            SignosVitales signosVitales,
            String quejaPrincipal,
            Nivel nivel,
            EstadoClinico estado,
            Instant estadoDesde,
            boolean escalado,
            Instant fechaRegistro) {
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.nombreCompleto =
                Objects.requireNonNull(nombreCompleto, "nombreCompleto es obligatorio");
        this.edad = edad;
        this.signosVitales = Objects.requireNonNull(signosVitales, "signosVitales es obligatorio");
        this.quejaPrincipal =
                Objects.requireNonNull(quejaPrincipal, "quejaPrincipal es obligatoria");
        this.nivel = Objects.requireNonNull(nivel, "nivel es obligatorio");
        this.estado = Objects.requireNonNull(estado, "estado es obligatorio");
        this.estadoDesde = Objects.requireNonNull(estadoDesde, "estadoDesde es obligatorio");
        this.escalado = escalado;
        this.fechaRegistro = Objects.requireNonNull(fechaRegistro, "fechaRegistro es obligatoria");
    }

    public static Paciente registrar(
            String id,
            String nombreCompleto,
            int edad,
            int frecuenciaCardiaca,
            int presionSistolica,
            int presionDiastolica,
            int spo2,
            BigDecimal temperatura,
            int dolor,
            int gcs,
            String quejaPrincipal,
            Nivel nivel,
            Instant ahora) {
        List<String> camposInvalidos = new ArrayList<>();
        validarNombreYEdad(camposInvalidos, nombreCompleto, edad);
        SignosVitales.agregarViolaciones(
                camposInvalidos,
                frecuenciaCardiaca,
                presionSistolica,
                presionDiastolica,
                spo2,
                temperatura,
                dolor,
                gcs);
        if (id == null || id.isBlank()) camposInvalidos.add("id");
        if (!camposInvalidos.isEmpty()) throw new DatosInvalidosExcepcion(camposInvalidos);
        Objects.requireNonNull(quejaPrincipal, "quejaPrincipal es obligatoria");
        Objects.requireNonNull(nivel, "nivel es obligatorio");
        Objects.requireNonNull(ahora, "ahora es obligatorio");
        SignosVitales signosVitales =
                new SignosVitales(
                        frecuenciaCardiaca,
                        presionSistolica,
                        presionDiastolica,
                        spo2,
                        temperatura,
                        dolor,
                        gcs);
        return new Paciente(
                id,
                nombreCompleto,
                edad,
                signosVitales,
                quejaPrincipal,
                nivel,
                EstadoClinico.REGISTRADO,
                ahora,
                false,
                ahora);
    }

    public static Paciente reconstruir(
            String id,
            String nombreCompleto,
            int edad,
            SignosVitales signosVitales,
            String quejaPrincipal,
            Nivel nivel,
            EstadoClinico estado,
            Instant estadoDesde,
            boolean escalado,
            Instant fechaRegistro) {
        List<String> camposInvalidos = new ArrayList<>();
        validarNombreYEdad(camposInvalidos, nombreCompleto, edad);
        if (!camposInvalidos.isEmpty()) throw new DatosInvalidosExcepcion(camposInvalidos);
        return new Paciente(
                id,
                nombreCompleto,
                edad,
                signosVitales,
                quejaPrincipal,
                nivel,
                estado,
                estadoDesde,
                escalado,
                fechaRegistro);
    }

    private static void validarNombreYEdad(
            List<String> camposInvalidos, String nombreCompleto, int edad) {
        if (nombreCompleto == null
                || nombreCompleto.trim().length() < 3
                || nombreCompleto.trim().length() > 100) {
            camposInvalidos.add("nombreCompleto");
        }
        if (edad < 0 || edad > 120) camposInvalidos.add("edad");
    }

    public EstadoClinico cambiarEstado(EstadoClinico nuevoEstado, Instant ahora) {
        Objects.requireNonNull(ahora, "ahora es obligatorio");
        EstadoClinico estadoValidado = estado.cambiarEstado(nuevoEstado);
        this.estadoDesde = ahora;
        this.estado = estadoValidado;
        return estadoValidado;
    }

    public boolean escalar(Instant ahora) {
        Objects.requireNonNull(ahora, "ahora es obligatorio");
        if (estado != EstadoClinico.REGISTRADO || escalado) return false;
        escalado = true;
        return true;
    }

    public String id() {
        return id;
    }

    public String nombreCompleto() {
        return nombreCompleto;
    }

    public int edad() {
        return edad;
    }

    public SignosVitales signosVitales() {
        return signosVitales;
    }

    public String quejaPrincipal() {
        return quejaPrincipal;
    }

    public Nivel nivel() {
        return nivel;
    }

    public EstadoClinico estado() {
        return estado;
    }

    public Instant estadoDesde() {
        return estadoDesde;
    }

    public boolean escalado() {
        return escalado;
    }

    public Instant fechaRegistro() {
        return fechaRegistro;
    }
}
