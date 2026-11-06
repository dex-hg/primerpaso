package com.dextre.primerpaso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrimerpasoApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrimerpasoApplication.class, args);
	}

}
