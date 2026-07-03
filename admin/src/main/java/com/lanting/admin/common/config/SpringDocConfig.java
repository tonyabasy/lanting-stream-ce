package com.lanting.admin.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI lantingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lanting Stream CE API")
                        .version("1.0.0"));
    }
}
