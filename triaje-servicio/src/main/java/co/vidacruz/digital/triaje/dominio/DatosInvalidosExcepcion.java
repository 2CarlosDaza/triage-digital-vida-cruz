package co.vidacruz.digital.triaje.dominio;

import java.util.List;
import java.util.stream.Collectors;

public final class DatosInvalidosExcepcion extends DominioExcepcion {
    private final List<String> camposInvalidos;

    public DatosInvalidosExcepcion(List<String> camposInvalidos) {
        super("Datos inválidos en los campos: " + String.join(", ", camposInvalidos));
        if (camposInvalidos.isEmpty()) {
            throw new IllegalArgumentException("Debe existir al menos un campo inválido");
        }
        this.camposInvalidos =
                camposInvalidos.stream().distinct().collect(Collectors.toUnmodifiableList());
    }

    public List<String> camposInvalidos() {
        return camposInvalidos;
    }
}
