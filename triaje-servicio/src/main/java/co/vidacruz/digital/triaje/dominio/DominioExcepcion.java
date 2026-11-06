package co.vidacruz.digital.triaje.dominio;

public abstract class DominioExcepcion extends RuntimeException {
    protected DominioExcepcion(String mensaje) {
        super(mensaje);
    }
}
