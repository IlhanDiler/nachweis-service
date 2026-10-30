package de.diler.nachweis;

import org.springframework.boot.SpringApplication;

public class TestNachweisServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(NachweisServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
