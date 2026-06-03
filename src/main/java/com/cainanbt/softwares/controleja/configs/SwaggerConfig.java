package com.cainanbt.softwares.controleja.configs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    private static final String BEARER_AUTH = "bearerAuth";

    /**
     * Configura metadados, autenticação JWT e servidor base publicados no Swagger UI.
     */
    @Bean
    public OpenAPI controleJaOpenAPI() {
        return new OpenAPI()
                .addServersItem(new Server()
                        .url("/controle_ja_api/v1")
                        .description("Base path da API v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Informe apenas o JWT; o Swagger envia como Authorization: Bearer <token>.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .info(new Info()
                        .title("Controle Já API")
                        .description("Contratos REST do Controle Já. Rotas públicas: auth, registro, health e Swagger. Demais rotas exigem Bearer JWT.")
                        .version("v1.0")
                        .license(new License().name("Controle Já").url("https://github.com/CainanBT-Sistemas/controle-ja-api")))
                .externalDocs(new ExternalDocumentation()
                        .description("Contratos complementares em /docs no repositório")
                        .url("https://github.com/CainanBT-Sistemas/controle-ja-api"));
    }
}
