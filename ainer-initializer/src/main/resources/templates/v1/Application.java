package {{package.name}};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class {{application.className}}Application {

    public static void main(String[] args) {
        SpringApplication.run({{application.className}}Application.class, args);
    }
}