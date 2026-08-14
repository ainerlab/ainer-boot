package dev.ainer.module.file;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.file.file.application.FileAuthorities;
import dev.ainer.module.file.file.application.FileErrorCode;
import dev.ainer.module.file.file.application.FilePage;
import dev.ainer.module.file.file.application.FileStorageApplicationService;
import dev.ainer.module.file.file.domain.FileObject;
import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.TokenProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the file module (ADR-0040). Real PostgreSQL 18.3 Testcontainers plus the
 * framework's local storage adapter on a {@link TempDir}; exercises migration → MyBatis → domain →
 * service including limits, checksum, compensation, audit and pagination.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = FileStorageModuleIntegrationTest.TestApplication.class,
        properties = {
                "ainer.file.enabled=true",
                "ainer.file.max-size-bytes=256",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class FileStorageModuleIntegrationTest {

    private static final IdentityAuthorityRef AUTHORITY =
            new IdentityAuthorityRef("https://auth.ainer.test");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_file_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ainer.storage.local.base-directory", () -> storageDir.toString());
    }

    @Autowired
    FileStorageApplicationService service;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() throws java.io.IOException {
        jdbcTemplate.execute("DELETE FROM ainer_file_audit");
        jdbcTemplate.execute("DELETE FROM ainer_file_object");
        // @TempDir is shared across tests; reset it so per-test file-count assertions are reliable
        try (var entries = Files.list(storageDir)) {
            for (Path entry : entries.toList()) {
                deleteRecursively(entry);
            }
        }
    }

    private static void deleteRecursively(Path path) throws java.io.IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    @Test
    void uploadPersistsMetadataAndBytes() throws Exception {
        byte[] bytes = "ainer file module".getBytes();

        FileObject object = service.upload(
                principal(FileAuthorities.READ, FileAuthorities.WRITE), "req-1",
                "docs", "notes.txt", "text/plain", new ByteArrayInputStream(bytes));

        assertThat(object.id().version()).isEqualTo(7);
        assertThat(object.namespace()).isEqualTo("docs");
        assertThat(object.filename()).isEqualTo("notes.txt");
        assertThat(object.contentType()).isEqualTo("text/plain");
        assertThat(object.contentLength()).isEqualTo(bytes.length);
        assertThat(object.uploadedByType()).isEqualTo("USER");
        assertThat(object.uploadedById()).isEqualTo("account:1");

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_file_object WHERE id = ?", Integer.class, object.id());
        assertThat(rows).isEqualTo(1);
        // bytes actually landed under the namespace directory
        try (var files = Files.list(storageDir.resolve("docs"))) {
            assertThat(files.count()).isEqualTo(1);
        }
        String operation = jdbcTemplate.queryForObject(
                "SELECT operation FROM ainer_file_audit WHERE file_id = ?", String.class, object.id());
        assertThat(operation).isEqualTo("UPLOADED");
    }

    @Test
    void uploadComputesSha256ChecksumOfStoredBytes() throws Exception {
        byte[] bytes = "checksum me".getBytes();
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));

        FileObject object = service.upload(
                principal(FileAuthorities.READ, FileAuthorities.WRITE), null,
                "docs", "a.txt", "text/plain", new ByteArrayInputStream(bytes));

        assertThat(object.checksumSha256()).isEqualTo(expected);
    }

    @Test
    void oversizeUploadIsRejectedAndBytesRemoved() throws Exception {
        byte[] oversize = new byte[300]; // limit is 256 in this test context

        assertThatThrownBy(() -> service.upload(
                principal(FileAuthorities.READ, FileAuthorities.WRITE), null,
                "docs", "big.bin", "application/json", new ByteArrayInputStream(oversize)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(FileErrorCode.FILE_TOO_LARGE));

        // compensation removed the already-stored bytes and no metadata row exists
        try (var files = Files.list(storageDir.resolve("docs"))) {
            assertThat(files.count()).isZero();
        }
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_file_object", Integer.class);
        assertThat(rows).isZero();
    }

    @Test
    void disallowedContentTypeIsRejected() {
        assertThatThrownBy(() -> service.upload(
                principal(FileAuthorities.READ, FileAuthorities.WRITE), null,
                "docs", "x.exe", "application/x-msdownload",
                new ByteArrayInputStream("MZ".getBytes())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(FileErrorCode.CONTENT_TYPE_NOT_ALLOWED));
    }

    @Test
    void blankFilenameIsRejected() {
        assertThatThrownBy(() -> service.upload(
                principal(FileAuthorities.READ, FileAuthorities.WRITE), null,
                "docs", "  ", "text/plain", new ByteArrayInputStream("x".getBytes())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(FileErrorCode.EMPTY_FILENAME));
    }

    @Test
    void uploadWithoutWriteScopeIsForbidden() {
        assertThatThrownBy(() -> service.upload(
                principal(FileAuthorities.READ), null,
                "docs", "a.txt", "text/plain", new ByteArrayInputStream("x".getBytes())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(StandardErrorCode.FORBIDDEN));
    }

    @Test
    void downloadRoundTripsBytes() throws Exception {
        byte[] bytes = "round trip".getBytes();
        FileObject object = service.upload(
                principal(FileAuthorities.READ, FileAuthorities.WRITE), null,
                "docs", "r.txt", "text/plain", new ByteArrayInputStream(bytes));

        FileStorageApplicationService.DownloadedFile downloaded =
                service.download(principal(FileAuthorities.READ, FileAuthorities.WRITE), object.id());

        assertThat(downloaded.object().id()).isEqualTo(object.id());
        try (InputStream content = downloaded.content()) {
            assertThat(content.readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void deleteRemovesMetadataAndBytesButKeepsAudit() throws Exception {
        byte[] bytes = "to delete".getBytes();
        AuthenticatedPrincipal principal = principal(FileAuthorities.READ, FileAuthorities.WRITE);
        FileObject object = service.upload(
                principal, null, "docs", "d.txt", "text/plain", new ByteArrayInputStream(bytes));

        service.delete(principal, "req-2", object.id());

        Integer metadata = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_file_object WHERE id = ?", Integer.class, object.id());
        assertThat(metadata).isZero();
        try (var files = Files.list(storageDir.resolve("docs"))) {
            assertThat(files.count()).isZero();
        }
        // audit rows survive; the DELETED row's file_id was nulled by the FK
        Integer deletedAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_file_audit WHERE operation = 'DELETED' AND file_id IS NULL",
                Integer.class);
        assertThat(deletedAudits).isEqualTo(1);
        Integer uploadedAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_file_audit WHERE operation = 'UPLOADED'", Integer.class);
        assertThat(uploadedAudits).isEqualTo(1);
    }

    @Test
    void deleteMissingFileThrowsNotFound() {
        AuthenticatedPrincipal principal = principal(FileAuthorities.READ, FileAuthorities.WRITE);
        assertThatThrownBy(() -> service.delete(principal, null, dev.ainer.core.uuid.Uuidv7.generate()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(FileErrorCode.NOT_FOUND));
    }

    @Test
    void pageFiltersByNamespace() {
        AuthenticatedPrincipal principal = principal(FileAuthorities.READ, FileAuthorities.WRITE);
        upload(principal, "ns-a", "a1.txt");
        upload(principal, "ns-a", "a2.txt");
        upload(principal, "ns-b", "b1.txt");

        FilePage nsA = service.page(principal, "ns-a", 1, 20);
        assertThat(nsA.total()).isEqualTo(2);
        assertThat(nsA.items()).allSatisfy(object -> assertThat(object.namespace()).isEqualTo("ns-a"));

        FilePage all = service.page(principal, null, 1, 20);
        assertThat(all.total()).isEqualTo(3);
    }

    @Test
    void invalidPageSizeIsRejected() {
        AuthenticatedPrincipal principal = principal(FileAuthorities.READ);
        assertThatThrownBy(() -> service.page(principal, null, 1, 101))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(FileErrorCode.INVALID_PAGE));
    }

    private void upload(AuthenticatedPrincipal principal, String namespace, String filename) {
        service.upload(principal, null, namespace, filename, "text/plain",
                new ByteArrayInputStream("content".getBytes()));
    }

    private static AuthenticatedPrincipal principal(String... scopes) {
        return new AuthenticatedPrincipal(
                new HumanSubjectRef(AUTHORITY, "account:1"),
                AUTHORITY,
                TokenProfile.USER_NEUTRAL_V1,
                "1",
                Set.of("ainer-api"),
                Set.of(scopes),
                "pwd",
                null,
                0L);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(FileModuleConfiguration.class)
    static class TestApplication {
    }

    /**
     * The controller requires an {@code AuthenticatedPrincipalResolver}; this service-level test
     * exercises the service directly, so a fixed principal satisfies the wiring without enabling
     * the whole resource-server chain (real JWT is covered by {@code FileStorageHttpTest}).
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class PrincipalFixture {

        @Bean
        dev.ainer.security.token.AuthenticatedPrincipalResolver testPrincipalResolver() {
            return () -> new AuthenticatedPrincipal(
                    new HumanSubjectRef(AUTHORITY, "account:1"),
                    AUTHORITY,
                    TokenProfile.USER_NEUTRAL_V1,
                    "1",
                    Set.of("ainer-api"),
                    Set.of(FileAuthorities.READ, FileAuthorities.WRITE),
                    "pwd",
                    null,
                    0L);
        }
    }
}
