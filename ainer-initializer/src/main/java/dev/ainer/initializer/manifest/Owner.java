package dev.ainer.initializer.manifest;

import org.jspecify.annotations.Nullable;

/**
 * Optional ownership metadata written only to README and copyright placeholders, never
 * into run-time configuration.
 *
 * @param displayName human readable owner name
 * @param email       contact email, must be empty or look like an email
 */
public record Owner(@Nullable String displayName, @Nullable String email) {

    public Owner {
        if (displayName != null && displayName.isBlank()) {
            displayName = null;
        }
        if (email != null && email.isBlank()) {
            email = null;
        }
    }

    public String displayNameOrFallback() {
        return displayName == null ? "Ainer Project" : displayName;
    }
}