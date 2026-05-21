package com.pystelectronic.rfid.transfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.pystelectronic.rfid.transfer",
    "com.pystelectronic.rfid.common"
})

@EnableAsync
@EnableScheduling
public class TransferApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransferApiApplication.class, args);
    }
}
