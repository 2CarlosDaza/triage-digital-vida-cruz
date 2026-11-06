package co.vidacruz.digital.triaje;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import reactor.blockhound.BlockHound;

class BlockHoundPrueba {
    private static boolean instalado;

    @Test
    @EnabledOnOs(OS.LINUX)
    void debeInstalarDetectorDeBloqueosParaLasPruebasDelServicio() {
        synchronized (BlockHoundPrueba.class) {
            if (!instalado) {
                BlockHound.install();
                instalado = true;
            }
        }
    }
}
