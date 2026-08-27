package {{package.name}}.{{entity.package}}.application;

import java.time.Instant;
import java.util.UUID;

/** 稳定的应用层投影；持久化 Row 保持在 infrastructure 内部。 */
public record {{entity.className}}Record(
        UUID id,
        UUID workspaceId,
{{entity.commandComponents}},
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
