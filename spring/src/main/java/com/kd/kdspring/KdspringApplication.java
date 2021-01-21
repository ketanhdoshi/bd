package com.kd.kdspring;

import java.util.Arrays;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import com.kd.kdspring.book.BookController;
import com.kd.kdspring.member.MemberController;
import com.kd.kdspring.member.MemberService;

@EnableJpaRepositories({"com.kd.kdspring.book", "com.kd.kdspring.member"})
//@EnableJpaRepositories
@EntityScan("com.kd.kdspring.model")
@SpringBootApplication
@ComponentScan(useDefaultFilters=false)
public class KdspringApplication {

	public static void main(String[] args) {
		SpringApplication.run(KdspringApplication.class, args);
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

	@Bean
    public HelloController helloController() {
        return new HelloController();
    }

	@Bean
    public SimpleController simpleController() {
        return new SimpleController();
    }

	@Bean
    public BookController bookController() {
        return new BookController();
    }

	@Bean
    public MemberController memberController() {
        return new MemberController();
    }

	@Bean
    public MemberService memberService() {
        return new MemberService();
    }

}

