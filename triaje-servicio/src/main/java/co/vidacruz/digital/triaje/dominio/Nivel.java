package co.vidacruz.digital.triaje.dominio;

public enum Nivel {
    CRITICO(1, "CRITICO", 5),
    URGENTE(2, "URGENTE", 10),
    URGENTE_2(3, "URGENTE_2", 30),
    NO_URGENTE(4, "NO_URGENTE", 60),
    NO_URGENTE_2(5, "NO_URGENTE_2", 120);

    private final int numero;
    private final String nombre;
    private final int slaMinutos;

    Nivel(int numero, String nombre, int slaMinutos) {
        this.numero = numero;
        this.nombre = nombre;
        this.slaMinutos = slaMinutos;
    }

    public int numero() {
        return numero;
    }

    public String nombre() {
        return nombre;
    }

    public int slaMinutos() {
        return slaMinutos;
    }
}
