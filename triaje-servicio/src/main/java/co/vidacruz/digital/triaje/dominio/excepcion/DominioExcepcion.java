package co.vidacruz.digital.triaje.dominio.excepcion;

public abstract class DominioExcepcion extends RuntimeException {
    protected DominioExcepcion(String mensaje) {
        super(mensaje);
    }
}
