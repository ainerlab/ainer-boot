package dev.ainer.spring.storage;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.storage.FileStoragePort;
import dev.ainer.core.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link LocalFileStorageAdapter}. Uses a JUnit {@code @TempDir} for isolation — no
 * external storage service or Docker required.
 */
class LocalFileStorageAdapterTest {

    @TempDir
    Path tempDir;

    private FileStoragePort storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorageAdapter(tempDir.toString());
    }

    @Test
    void storeAndResolveRoundTrip() throws java.io.IOException {
        byte[] content = "hello-storage".getBytes();
        StoredFile stored = storage.store("workspace-1", "report.pdf", "application/pdf",
                new ByteArrayInputStream(content));

        assertThat(stored.namespace()).isEqualTo("workspace-1");
        assertThat(stored.filename()).isEqualTo("report.pdf");
        assertThat(stored.contentType()).isEqualTo("application/pdf");
        assertThat(stored.contentLength()).isEqualTo(content.length);
        assertThat(stored.storageKey()).startsWith("workspace-1/");

        Optional<InputStream> resolved = storage.resolve(stored.storageKey());
        assertThat(resolved).isPresent();
        assertThat(resolved.get().readAllBytes()).isEqualTo(content);
    }

    @Test
    void resolveMissingKeyReturnsEmpty() {
        assertThat(storage.resolve("nonexistent/abc123")).isEmpty();
    }

    @Test
    void deleteRemovesFile() {
        StoredFile stored = storage.store("ns", "f.txt", null,
                new ByteArrayInputStream("data".getBytes()));
        assertThat(storage.delete(stored.storageKey())).isTrue();
        assertThat(storage.resolve(stored.storageKey())).isEmpty();
    }

    @Test
    void deleteMissingKeyReturnsFalse() {
        assertThat(storage.delete("missing/key")).isFalse();
    }

    @Test
    void storeCreatesNamespaceDirectory() {
        StoredFile stored = storage.store("workspace-1", "f.txt", null,
                new ByteArrayInputStream("x".getBytes()));
        assertThat(stored.storageKey()).startsWith("workspace-1/");
        assertThat(tempDir.resolve("workspace-1")).isDirectoryContaining(
                path -> path.getFileName().toString()
                        .equals(stored.storageKey().substring("workspace-1/".length())));
    }

    @Test
    void pathTraversalInNamespaceIsRejected() {
        assertThatThrownBy(() -> storage.store("..", "evil.txt", null,
                new ByteArrayInputStream("x".getBytes())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void pathTraversalInStorageKeyIsRejected() {
        assertThatThrownBy(() -> storage.resolve("../etc/passwd"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.delete("../etc/passwd"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void dotPrefixedNamespaceIsRejected() {
        assertThatThrownBy(() -> storage.store(".hidden", "f.txt", null,
                new ByteArrayInputStream("x".getBytes())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void slashInNamespaceIsRejected() {
        // namespace 是单层逻辑分组，不允许含路径分隔符
        assertThatThrownBy(() -> storage.store("ws/attachments", "f.txt", null,
                new ByteArrayInputStream("x".getBytes())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void backslashInNamespaceIsRejected() {
        assertThatThrownBy(() -> storage.store("ws\\attachments", "f.txt", null,
                new ByteArrayInputStream("x".getBytes())))
                .isInstanceOf(BusinessException.class);
    }
}
