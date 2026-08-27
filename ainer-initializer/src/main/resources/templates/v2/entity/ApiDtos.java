package {{package.name}}.{{entity.package}}.api;

import {{package.name}}.{{entity.package}}.application.{{entity.className}}Commands;
import {{package.name}}.{{entity.package}}.application.{{entity.className}}Page;
import {{package.name}}.{{entity.package}}.application.{{entity.className}}Record;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 显式 HTTP DTO；不序列化持久化实体或 MyBatis 分页类型。 */
public final class {{entity.className}}ApiDtos {

    private {{entity.className}}ApiDtos() {
    }

    public record CreateRequest(
{{entity.createComponents}}) {

        {{entity.className}}Commands.Create toCommand() {
            return new {{entity.className}}Commands.Create(
{{entity.requestValues}});
        }
    }

    public record UpdateRequest(
{{entity.updateComponents}}) {

        {{entity.className}}Commands.Update toCommand() {
            return new {{entity.className}}Commands.Update(
{{entity.requestValues}},
                    version);
        }
    }

    public record Response(
            UUID id,
            UUID workspaceId,
{{entity.responseComponents}},
            long version,
            Instant createdAt,
            Instant updatedAt) {

        static Response from({{entity.className}}Record record) {
            return new Response(
                    record.id(),
                    record.workspaceId(),
{{entity.recordValues}},
                    record.version(),
                    record.createdAt(),
                    record.updatedAt());
        }
    }

    public record PageResponse(
            List<Response> items,
            int page,
            int size,
            long total) {

        static PageResponse from({{entity.className}}Page page) {
            return new PageResponse(
                    page.items().stream().map(Response::from).toList(),
                    page.page(), page.size(), page.total());
        }
    }
}
