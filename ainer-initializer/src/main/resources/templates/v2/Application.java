package {{package.name}};

import dev.ainer.module.workspace.WorkspaceModuleConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(WorkspaceModuleConfiguration.class)
@MapperScan(basePackages = "{{package.name}}")
public class {{application.className}}Application {

    public static void main(String[] args) {
        SpringApplication.run({{application.className}}Application.class, args);
    }
}
