package com.carmarketplace;

import org.springframework.boot.SpringApplication;

public class TestCarMarketplaceApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(CarMarketplaceApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
