package co.vidacruz.digital.triaje.dominio;

public final class TransicionInvalidaExcepcion extends DominioExcepcion {
    public TransicionInvalidaExcepcion(EstadoClinico origen, EstadoClinico destino) {
        super("Transición clínica inválida: " + origen + " → " + destino);
    }
}
