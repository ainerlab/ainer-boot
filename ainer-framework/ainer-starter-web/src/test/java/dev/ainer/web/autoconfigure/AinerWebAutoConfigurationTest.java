package dev.ainer.web.autoconfigure;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.ErrorCode;
import dev.ainer.core.error.ErrorCodeContributor;
import dev.ainer.core.error.ErrorCodeRegistry;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.web.request.RequestIdFilter;
import dev.ainer.web.request.RequestIds;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AinerWebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AinerWebAutoConfiguration.class))
            .withUserConfiguration(TestWebConfiguration.class);

    @Test
    void mapsBusinessErrorsToRealHttpStatusAndStableBody() {
        contextRunner.run(context -> {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilter(context.getBean(RequestIdFilter.class))
                    .build();

            mockMvc.perform(get("/probe/business").header(RequestIds.HEADER, "client-request-42"))
                    .andExpect(status().is(422))
                    .andExpect(header().string(RequestIds.HEADER, "client-request-42"))
                    .andExpect(jsonPath("$.code").value(StandardErrorCode.BUSINESS_RULE_VIOLATION.code()))
                    .andExpect(jsonPath("$.message").value("订单状态不允许取消"))
                    .andExpect(jsonPath("$.requestId").value("client-request-42"));
        });
    }

    @Test
    void registersModuleErrorCodeContributors() {
        contextRunner
                .withBean(ErrorCodeContributor.class, () -> () -> List.of(TestErrorCode.PROBE))
                .run(context -> assertThat(context.getBean(ErrorCodeRegistry.class).snapshot())
                        .containsKey(TestErrorCode.PROBE.code()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class TestWebConfiguration {

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/business")
        void businessFailure() {
            throw new BusinessException(StandardErrorCode.BUSINESS_RULE_VIOLATION, "订单状态不允许取消");
        }
    }

    enum TestErrorCode implements ErrorCode {
        PROBE;

        @Override
        public String code() {
            return "AINER.TEST.PROBE";
        }

        @Override
        public String defaultMessage() {
            return "probe";
        }

        @Override
        public int httpStatus() {
            return 422;
        }
    }
}
