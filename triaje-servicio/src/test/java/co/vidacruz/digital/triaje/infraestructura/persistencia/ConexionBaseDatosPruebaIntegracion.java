package co.vidacruz.digital.triaje.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import co.vidacruz.digital.triaje.TriajeServicioAplicacion;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.test.StepVerifier;

@SpringBootTest(classes = TriajeServicioAplicacion.class)
@Testcontainers(disabledWithoutDocker = true)
class ConexionBaseDatosPruebaIntegracion {
    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("vida_cruz")
                    .withUsername("triaje_migracion")
                    .withPassword("clave_migracion")
                    .withInitScript("db/crear_usuario_runtime.sql");

    @Autowired private ConnectionFactory connectionFactory;
    @Autowired private DatabaseClient databaseClient;

    @DynamicPropertySource
    static void configurarConexion(DynamicPropertyRegistry propiedades) {
        propiedades.add(
                "spring.r2dbc.url",
                () ->
                        "r2dbc:postgresql://"
                                + POSTGRES.getHost()
                                + ":"
                                + POSTGRES.getFirstMappedPort()
                                + "/"
                                + POSTGRES.getDatabaseName());
        propiedades.add("spring.r2dbc.username", () -> "triaje_runtime");
        propiedades.add("spring.r2dbc.password", () -> "clave_runtime");
        propiedades.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        propiedades.add("spring.flyway.user", POSTGRES::getUsername);
        propiedades.add("spring.flyway.password", POSTGRES::getPassword);
        propiedades.add("spring.flyway.placeholders.usuario-runtime", () -> "triaje_runtime");
    }

    @Test
    void debeConectarConPostgresUsandoR2dbc() {
        assertNotNull(connectionFactory);

        databaseClient
                .sql("SELECT current_database() AS nombre")
                .map((fila, metadatos) -> fila.get("nombre", String.class))
                .one()
                .as(StepVerifier::create)
                .expectNext("vida_cruz")
                .verifyComplete();
    }

    @Test
    void debeAplicarLaMigracionFlywayAlIniciar() {
        databaseClient
                .sql("SELECT to_regclass('public.paciente')::text AS nombre")
                .map((fila, metadatos) -> fila.get("nombre", String.class))
                .one()
                .as(StepVerifier::create)
                .assertNext(nombre -> assertEquals("paciente", nombre))
                .verifyComplete();

        databaseClient
                .sql("SELECT COUNT(*) AS cantidad FROM flyway_schema_history WHERE success")
                .map((fila, metadatos) -> fila.get("cantidad", Long.class))
                .one()
                .as(StepVerifier::create)
                .expectNext(1L)
                .verifyComplete();
    }
}
