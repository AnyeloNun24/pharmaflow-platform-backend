package com.pharmaflow.auth_service;

import com.pharmaflow.auth_service.config.properties.JwtProperties;
import com.pharmaflow.auth_service.config.properties.PasswordTokenProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, PasswordTokenProperties.class})
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
