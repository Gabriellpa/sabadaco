package com.gabriellpa.sabadaco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SabadacoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SabadacoApplication.class, args);
    }

}
