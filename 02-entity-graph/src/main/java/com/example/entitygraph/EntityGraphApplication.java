package com.example.entitygraph;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "EntityGraph Learning Demo",
        version = "1.0",
        description = "Run each JPA EntityGraph scenario separately and compare its SQL and Hibernate metrics."
))
public class EntityGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(EntityGraphApplication.class, args);
    }
}
