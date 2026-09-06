package com.group.resv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ResvApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResvApplication.class, args);
    }
}
