package dev.ainer.core.error;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ErrorCodeRegistryTest {

    @Test
    void registersUniqueCodes() {
        ErrorCodeRegistry registry = new ErrorCodeRegistry()
                .register(List.of(StandardErrorCode.values()));

        assertEquals(StandardErrorCode.values().length, registry.snapshot().size());
    }

    @Test
    void rejectsDuplicateCodes() {
        ErrorCode duplicate = new ErrorCode() {
            @Override
            public String code() {
                return StandardErrorCode.NOT_FOUND.code();
            }

            @Override
            public String defaultMessage() {
                return "duplicate";
            }

            @Override
            public int httpStatus() {
                return 404;
            }
        };

        ErrorCodeRegistry registry = new ErrorCodeRegistry().register(StandardErrorCode.NOT_FOUND);

        assertThrows(IllegalStateException.class, () -> registry.register(duplicate));
    }
}
