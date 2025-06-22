package com.team03.ticketmon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@ConfigurationPropertiesScan
@SpringBootApplication
public class TicketmonGoApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketmonGoApplication.class, args);
	}

}
