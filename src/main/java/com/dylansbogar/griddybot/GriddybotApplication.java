package com.dylansbogar.griddybot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class GriddybotApplication {

	public static void main(String[] args) {
		SpringApplication.run(GriddybotApplication.class, args);
	}
}
