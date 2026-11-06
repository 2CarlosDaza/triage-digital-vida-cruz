package co.vidacruz.digital.triaje.dominio;

import co.vidacruz.digital.triaje.dominio.enumeracion.Nivel;

/** Estrategia pura que determina si aplican las condiciones de un nivel. */
public interface ReglaClasificacion {
    Nivel nivel();

    boolean aplica(SignosVitales signosVitales);
}
