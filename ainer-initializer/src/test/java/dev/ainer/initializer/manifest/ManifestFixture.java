package dev.ainer.initializer.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Loads the manifest fixtures under {@code /manifest/v1/} the same way consumers would.
 */
public final class ManifestFixture {

    public static ManifestV1 sample() throws IOException {
        return load("manifest/v1/sample.yaml");
    }

    public static ManifestV1 postgres() throws IOException {
        return load("manifest/v1/postgres.yaml");
    }

    public static ManifestV1 crud() throws IOException {
        return load("manifest/v1/crud.yaml");
    }

    private static ManifestV1 load(String resource) throws IOException {
        try (InputStream stream = ManifestFixture.class.getClassLoader().getResourceAsStream(resource)) {
            Objects.requireNonNull(stream, "missing fixture " + resource);
            Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            return new ManifestReader().read(reader);
        }
    }
}