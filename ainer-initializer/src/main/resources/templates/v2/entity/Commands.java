package {{package.name}}.{{entity.package}}.application;

/** 应用输入合同；HTTP 或持久化类型不穿过本边界。 */
public final class {{entity.className}}Commands {

    private {{entity.className}}Commands() {
    }

    public record Create(
{{entity.commandComponents}}) {
    }

    public record Update(
{{entity.commandComponents}},
            long version) {
    }
}
