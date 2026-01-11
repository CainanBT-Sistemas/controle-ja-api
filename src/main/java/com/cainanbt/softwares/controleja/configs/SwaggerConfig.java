package com.cainanbt.softwares.controleja.configs;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI controleJaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Controle Já API")
                        .description("Documentação da API do sistema Controle Já")
                        .version("v1.0")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))
                .externalDocs(new ExternalDocumentation()
                        .description("Repositório GitHub")
                        .url("https://github.com/CainanBT-Sistemas/controle-ja-api"));
    }
}
