package com.example.stripedemo;

import com.stripe.Stripe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class StripeDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(StripeDemoApplication.class, args);
    }

    /** Set the SECRET key once at startup. It stays on the server, never in the frontend. */
    @Bean
    CommandLineRunner initStripe(@Value("${stripe.secret-key}") String secretKey) {
        return args -> Stripe.apiKey = secretKey;
    }
}
