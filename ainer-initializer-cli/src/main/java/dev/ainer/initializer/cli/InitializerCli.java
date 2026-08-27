package dev.ainer.initializer.cli;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.generate.ProjectGenerator;
import dev.ainer.initializer.generate.ProjectTree;
import dev.ainer.initializer.generate.ProjectWriter;
import dev.ainer.initializer.generate.SecureProjectGenerator;
import dev.ainer.initializer.manifest.ManifestV2;
import dev.ainer.initializer.manifest.ManifestReader;
import dev.ainer.initializer.manifest.ManifestV1;
import dev.ainer.initializer.manifest.ProjectManifest;
import dev.ainer.initializer.preview.ProjectDiffer;
import dev.ainer.initializer.preview.ProjectPreview;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * Offline project initializer CLI. Subcommands:
 *
 * <ul>
 *   <li>{@code preview <manifest.yaml>} — validate and preview generation (read-only);</li>
 *   <li>{@code init <manifest.yaml> <target-dir>} — generate into an empty target
 *       (refuses non-empty targets unless {@code --force});</li>
 *   <li>{@code diff <manifest.yaml> <target-dir>} — read-only comparison.</li>
 * </ul>
 *
 * <p>Exit codes: 0 success, 2 usage/manifest error, 3 write refused.
 */
public final class InitializerCli {

    private final PrintStream out;
    private final PrintStream err;

    public InitializerCli(PrintStream out, PrintStream err) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    public static void main(String[] args) {
        int exitCode = new InitializerCli(System.out, System.err).run(args);
        System.exit(exitCode);
    }

    public int run(String[] args) {
        Objects.requireNonNull(args, "args");
        if (args.length == 0) {
            usage(err);
            return 2;
        }
        try {
            return switch (args[0]) {
                case "preview" -> preview(args);
                case "init" -> init(args);
                case "diff" -> diff(args);
                case "help", "--help", "-h" -> {
                    usage(out);
                    yield 0;
                }
                default -> {
                    err.println("未知子命令: " + args[0]);
                    usage();
                    yield 2;
                }
            };
        } catch (BusinessException e) {
            err.println("错误: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            err.println("I/O 错误: " + messageOf(e));
            return 2;
        }
    }

    private int preview(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return 2;
        }
        ProjectManifest manifest = readManifest(args[1]);
        ProjectTree tree = generate(manifest);
        out.print(ProjectPreview.of(tree).render());
        out.println("(preview 未写入任何文件)");
        return 0;
    }

    private int init(String[] args) throws IOException {
        boolean force = Arrays.asList(args).contains("--force");
        String[] rest = Arrays.stream(args)
                .filter(arg -> !"--force".equals(arg))
                .toArray(String[]::new);
        if (rest.length != 3) {
            usage();
            return 2;
        }
        ProjectManifest manifest = readManifest(rest[1]);
        Path target = Path.of(rest[2]).toAbsolutePath();
        ProjectTree tree = generate(manifest);
        try {
            new ProjectWriter().write(tree, target, force);
        } catch (BusinessException e) {
            err.println("错误: " + e.getMessage());
            return 3;
        }
        out.print(ProjectPreview.of(tree, target).render());
        out.println("已生成 " + tree.size() + " 个文件到 " + target);
        return 0;
    }

    private int diff(String[] args) throws IOException {
        if (args.length != 3) {
            usage();
            return 2;
        }
        ProjectManifest manifest = readManifest(args[1]);
        Path target = Path.of(args[2]).toAbsolutePath();
        ProjectTree tree = generate(manifest);
        ProjectDiffer.DiffResult result = new ProjectDiffer().diff(tree, target);
        out.print(diffText(result));
        return result.hasChanges() ? 1 : 0;
    }

    private ProjectManifest readManifest(String manifestPath) throws IOException {
        try (var reader = new InputStreamReader(
                Files.newInputStream(Path.of(manifestPath)), StandardCharsets.UTF_8)) {
            return new ManifestReader().readProject(reader);
        }
    }

    private ProjectTree generate(ProjectManifest manifest) {
        if (manifest instanceof ManifestV1 v1) {
            return new ProjectGenerator(v1).generate();
        }
        if (manifest instanceof ManifestV2 v2) {
            return new SecureProjectGenerator(v2).generate();
        }
        throw new IllegalArgumentException("不支持的 manifest 实现: " + manifest.getClass().getName());
    }

    private static String diffText(ProjectDiffer.DiffResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("新增 ").append(result.newFiles().size());
        if (!result.newFiles().isEmpty()) {
            builder.append(": ").append(String.join(", ", result.newFiles()));
        }
        builder.append('\n');
        builder.append("修改 ").append(result.modifiedFiles().size());
        if (!result.modifiedFiles().isEmpty()) {
            builder.append(": ").append(String.join(", ", result.modifiedFiles()));
        }
        builder.append('\n');
        builder.append("不变 ").append(result.unchangedFiles().size()).append('\n');
        builder.append("建议删除（未执行）").append(result.suggestedDeletes().size());
        if (!result.suggestedDeletes().isEmpty()) {
            builder.append(": ").append(String.join(", ", result.suggestedDeletes()));
        }
        return builder.toString();
    }

    private static String messageOf(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void usage() {
        usage(err);
    }

    private void usage(PrintStream stream) {
        stream.println("""
                用法: ainer-initializer <命令> <参数>

                命令:
                  preview <manifest.yaml>                校验并预览（只读，不落盘）
                  init <manifest.yaml> <target-dir>      在空目录生成项目（非空需 --force）
                  diff <manifest.yaml> <target-dir>      只读对比现有目录
                  help                                    显示本帮助
                """);
    }
}
