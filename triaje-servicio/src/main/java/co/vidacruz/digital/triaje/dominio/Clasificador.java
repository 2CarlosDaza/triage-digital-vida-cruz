package co.vidacruz.digital.triaje.dominio;

import co.vidacruz.digital.triaje.dominio.enumeracion.Nivel;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Evalúa las estrategias del nivel más grave al menos grave (RN-01). */
public final class Clasificador {
    private static final Pattern MARCAS_COMBINADAS = Pattern.compile("\\p{M}+");
    private final List<ReglaClasificacion> reglas;
    private final List<Pattern> patronesQuejasAdministrativas;

    public Clasificador(List<ReglaClasificacion> reglas, List<String> quejasAdministrativas) {
        Objects.requireNonNull(reglas, "reglas son obligatorias");
        this.reglas =
                List.copyOf(reglas).stream()
                        .sorted(Comparator.comparingInt(regla -> regla.nivel().numero()))
                        .toList();
        this.patronesQuejasAdministrativas =
                List.copyOf(
                                Objects.requireNonNull(
                                        quejasAdministrativas,
                                        "quejasAdministrativas es obligatoria"))
                        .stream()
                        .map(Clasificador::normalizar)
                        .filter(frase -> !frase.isBlank())
                        .map(Clasificador::crearPatronQueja)
                        .toList();
        Set<Nivel> niveles =
                this.reglas.stream().map(ReglaClasificacion::nivel).collect(Collectors.toSet());
        if (this.reglas.size() != 3
                || !niveles.equals(Set.of(Nivel.CRITICO, Nivel.URGENTE, Nivel.URGENTE_2))) {
            throw new IllegalArgumentException(
                    "Se requiere exactamente una estrategia para cada nivel clínico del 1 al 3");
        }
    }

    public Nivel clasificar(SignosVitales signosVitales, String quejaPrincipal) {
        Objects.requireNonNull(signosVitales, "signosVitales es obligatorio");
        Objects.requireNonNull(quejaPrincipal, "quejaPrincipal es obligatoria");
        Nivel nivel =
                reglas.stream()
                        .filter(regla -> regla.aplica(signosVitales))
                        .map(ReglaClasificacion::nivel)
                        .findFirst()
                        .orElse(Nivel.NO_URGENTE);
        if (nivel == Nivel.NO_URGENTE && esQuejaAdministrativa(quejaPrincipal)) {
            return Nivel.NO_URGENTE_2;
        }
        return nivel;
    }

    private boolean esQuejaAdministrativa(String quejaPrincipal) {
        String normalizada = normalizar(quejaPrincipal);
        return patronesQuejasAdministrativas.stream()
                .anyMatch(patron -> patron.matcher(normalizada).find());
    }

    private static String normalizar(String texto) {
        String minusculas = texto.toLowerCase(Locale.ROOT);
        return MARCAS_COMBINADAS
                .matcher(Normalizer.normalize(minusculas, Normalizer.Form.NFD))
                .replaceAll("")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Pattern crearPatronQueja(String frase) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(frase) + "(?![\\p{L}\\p{N}])");
    }
}
