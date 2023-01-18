package cs.dev.log.security.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public GroupedOpenApi groupedOpenApi() {
        return GroupedOpenApi.builder()
                .group("security")
                .pathsToMatch("/auth/**")
                .addOpenApiCustomiser(openApi -> openApi
                        .getComponents()
                        .addSecuritySchemes("Basic-Auth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                        )
                )
                .build();
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components())
                .info(new Info()
                        .title("CS-DEV-LOG")
                        .version("1.0.0")
                        .description("Security Modules Specifications")
                        .termsOfService("http://localhost:8080")
                        .contact(new Contact().name("Web Developer"))
                        .license(new License().name("CSCHOI"))
                );
    }
}
