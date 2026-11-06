package co.vidacruz.digital.triaje;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.vidacruz.digital.triaje.dominio.excepcion.DominioExcepcion;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArquitecturaPrueba {
    private JavaClasses clasesProduccion;

    @BeforeEach
    void importarClasesDeProduccion() {
        clasesProduccion =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("co.vidacruz.digital.triaje");
    }

    @Test
    void dominioNoDependeDeFrameworksNiInfraestructura() {
        ArchRule regla =
                noClasses()
                        .that()
                        .resideInAPackage("..dominio..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework..",
                                "reactor..",
                                "com.fasterxml.jackson..",
                                "..infraestructura..");
        regla.check(clasesProduccion);
    }

    @Test
    void aplicacionNoDependeDeInfraestructura() {
        ArchRule regla =
                noClasses()
                        .that()
                        .resideInAPackage("..aplicacion..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..infraestructura..")
                        .allowEmptyShould(true);
        regla.check(clasesProduccion);
    }

    @Test
    void clasesTecnicasRespetanSufijosDelGlosario() {
        exigirSufijos(
                "infraestructura.web",
                "Controlador",
                "Solicitud",
                "Respuesta",
                "Validador",
                "Manejador",
                "Filtro",
                "Mapeador");
        exigirSufijos("infraestructura.persistencia", "Repositorio", "Adaptador");
        exigirSufijos(
                "infraestructura.mensajeria",
                "Publicador",
                "Consumidor",
                "Adaptador",
                "Propiedades");
        exigirSufijos(
                "infraestructura.ia", "Adaptador", "Servicio", "Propiedades", "Mapeador");
        exigirSufijos(
                "infraestructura.config", "Configuracion", "Propiedades", "Filtro", "Manejador");
        exigirSufijos("aplicacion.puerto", "Puerto");
        exigirSufijos("aplicacion.servicio", "Servicio", "CasoDeUso");
        clasesProduccion.stream()
                .filter(clase -> clase.isAssignableTo(DominioExcepcion.class))
                .forEach(
                        clase ->
                                assertTrue(
                                        clase.getSimpleName().endsWith("Excepcion"),
                                        clase.getName()));
    }

    @Test
    void mainNoDependeDeSqlJniNiMetodosBloqueantes() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("java.sql", "java.sql..")
                .check(clasesProduccion);
        noClasses()
                .should()
                .callMethodWhere(target(nameMatching("block.*")))
                .check(clasesProduccion);
        noClasses().should().callMethod(Thread.class, "sleep").check(clasesProduccion);
    }

    @Test
    void elDominioNoConsultaElRelojDelSistema() {
        noClasses()
                .that()
                .resideOutsideOfPackage("..infraestructura.config.reloj..")
                .should()
                .callMethod(Instant.class, "now")
                .check(clasesProduccion);
        noClasses()
                .that()
                .resideOutsideOfPackage("..infraestructura.config.reloj..")
                .should()
                .callMethod(LocalDateTime.class, "now")
                .check(clasesProduccion);
    }

    @Test
    void clasesDePruebaTerminanEnElSufijoCorrespondiente() {
        JavaClasses clasesPrueba = new ClassFileImporter().importPath("build/classes/java/test");
        clasesPrueba.stream()
                .filter(clase -> !clase.isAnonymousClass() && !clase.isNestedClass())
                .forEach(
                        clase ->
                                assertTrue(
                                        clase.getSimpleName().endsWith("Prueba")
                                                || clase.getSimpleName()
                                                        .endsWith("PruebaIntegracion"),
                                        "Sufijo de prueba inválido para " + clase.getName()));
    }

    private void exigirSufijos(String fragmentoPaquete, String... sufijosPermitidos) {
        clasesProduccion.stream()
                .filter(clase -> clase.getPackageName().contains(fragmentoPaquete))
                .forEach(
                        clase ->
                                assertTrue(
                                        Stream.of(sufijosPermitidos)
                                                .anyMatch(clase.getSimpleName()::endsWith),
                                        "Sufijo inválido para " + clase.getName()));
    }
}
