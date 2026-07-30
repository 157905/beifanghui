package com.beifanghui.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BeifanghuiBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BeifanghuiBackendApplication.class, args);
    }

}
