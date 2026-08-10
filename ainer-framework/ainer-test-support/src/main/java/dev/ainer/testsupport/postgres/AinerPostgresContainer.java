package dev.ainer.testsupport.postgres;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Municipal PostgreSQL container factory pinned to the Ainer database baseline image
 * ({@code postgres:18.3-alpine}), usable with Spring Boot's {@code @ServiceConnection} so the
 * datasource is wired automatically without {@code @DynamicPropertySource} boilerplate.
 *
 * <pre>{@code
 * @Testcontainers
 * @SpringBootTest
 * class RepositoryTest {
 *
 *     @Container
 *     @ServiceConnection
 *     static final PostgreSQLContainer<?> POSTGRES = AinerPostgresContainer.create();
 *
 *     @Autowired private DataSource dataSource;
 * }
 * }</pre>
 */
public final class AinerPostgresContainer {

    public static final String IMAGE = "postgres:18.3-alpine";

    public static PostgreSQLContainer<?> create() {
        return new PostgreSQLContainer<>(IMAGE);
    }

    private AinerPostgresContainer() {
    }
}