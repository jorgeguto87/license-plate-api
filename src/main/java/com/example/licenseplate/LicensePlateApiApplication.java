package com.example.licenseplate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LicensePlateApiApplication {

    public static void main(String[] args) {
        System.out.println("Iniciando API de Detecção de Placas...");
        SpringApplication.run(LicensePlateApiApplication.class, args);
        System.out.println("API iniciada com sucesso!");
    }
}