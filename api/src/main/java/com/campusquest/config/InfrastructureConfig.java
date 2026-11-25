package com.campusquest.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InfrastructureConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI campusQuestOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CampusQuest API")
                        .version("v1")
                        .description("Gamified campus quests: assignments, challenges, XP and levels."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
