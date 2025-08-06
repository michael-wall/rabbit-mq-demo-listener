package com.mw.sample.rabbitmq.listener;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRabbit
public class SampleSpringBootApplication {

	public static void main(String[] args) {
		SpringApplication.run(SampleSpringBootApplication.class, args);
	}
}