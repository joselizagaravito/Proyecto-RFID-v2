package com.pystelectronic.rfid.readtag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReadTagServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadTagServiceApplication.class, args);
    }
}
