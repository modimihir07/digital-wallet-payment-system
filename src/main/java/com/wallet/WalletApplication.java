package com.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Digital Wallet and Payment System.
 */
@SpringBootApplication
public class WalletApplication {
    /** Starts the Spring Boot application. */
    public static void main(String[] args) {
        SpringApplication.run(WalletApplication.class, args);
    }
}
