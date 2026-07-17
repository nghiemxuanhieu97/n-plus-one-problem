package com.example.batchsize;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "BatchSize Learning Demo",
        version = "1.0",
        description = "Run each Hibernate BatchSize scenario separately and compare IN queries and metrics."
))
public class BatchSizeApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchSizeApplication.class, args);
    }
}
