package org.acm.rg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class RegistrySvcApplication {

	public static void main(String[] args) {
		SpringApplication.run(RegistrySvcApplication.class, args);
	}
}
