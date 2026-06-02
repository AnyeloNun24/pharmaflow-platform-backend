package com.pharmaflow.auth_service;

import com.pharmaflow.auth_service.config.properties.JwtProperties;
import com.pharmaflow.auth_service.config.properties.PasswordTokenProperties;
import com.pharmaflow.auth_service.config.properties.SecurityPolicyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // habilita el sondeo del relay del Outbox (OutboxRelay)
@EnableConfigurationProperties({
        JwtProperties.class,
        PasswordTokenProperties.class,
        SecurityPolicyProperties.class
})
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
