package com.footballmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.footballmanager.domain",
    "com.footballmanager.application",
    "com.footballmanager.adapters",
    "com.footballmanager.infrastructure"
})
public class FootballManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FootballManagerApplication.class, args);
    }
}
