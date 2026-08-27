package {{package.name}}.{{entity.package}}.application;

import java.util.List;

/** 应用层返回的框架无关、受控分页。 */
public record {{entity.className}}Page(
        List<{{entity.className}}Record> items,
        int page,
        int size,
        long total) {

    public {{entity.className}}Page {
        items = List.copyOf(items);
    }
}
