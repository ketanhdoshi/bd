package com.kd.kdspring.legacy;

import java.util.Arrays;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

// Although this runs as a microservice in the microservice architecture, 
// it internally consists of a small 'monolithic' application with some web controllers
// as well as a handful of REST APIs with their database backends.

// Tell Boot to search and auto-instantiate Repository beans in these packages
@EnableJpaRepositories({"com.kd.kdspring.book", "com.kd.kdspring.member"})
//@EnableJpaRepositories
// Tell Boot to search and auto-instantiate Entity beans in these packages
@EntityScan({"com.kd.kdspring.book", "com.kd.kdspring.member"})
@SpringBootApplication
@ComponentScan(basePackages = {
	"com.kd.kdspring.legacy",
	"com.kd.kdspring.book", 
	"com.kd.kdspring.member", 
	"com.kd.kdspring.exception"
})
public class LegacyApplication {

	public static void main(String[] args) {
		// Since all the microservices in our application are in the same project, they would 
		// automatically use the same configuration. To avoid that, each microservice specifies 
		// an alternative file by setting the spring.config.name property.
		//
		// By default, this line is not needed and Boot looks for configuration settings 
		// in application.yml or application.properties.
		// However, by including this line, we tell Boot that our configuration settings 
		// are in legacy-application.yml instead
        System.setProperty("spring.config.name", "legacy-application");
		// Launch the application
		SpringApplication.run(LegacyApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {

			System.out.println("Let's inspect the beans provided by Spring Boot:");

			String[] beanNames = ctx.getBeanDefinitionNames();
			Arrays.sort(beanNames);
			for (String beanName : beanNames) {
				System.out.println(beanName);
			}

		};
	}
}

