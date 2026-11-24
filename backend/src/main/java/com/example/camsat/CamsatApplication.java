package com.example.camsat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CamsatApplication {
    public static void main(String[] args) {
        SpringApplication.run(CamsatApplication.class, args);
    }
}
