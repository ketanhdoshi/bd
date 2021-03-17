package com.kd.kdspring.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Bean;

// Disable Security and Actuator Security, so that other services don't need to authenticate
// Also disable Reactive Webflux load balancer auto configuration
@SpringBootApplication (exclude = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
    org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class,
    org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerBeanPostProcessorAutoConfiguration.class,
})
// Enable service registration and discovery, so it registers itself with the discovery-server service
@EnableDiscoveryClient
public class UserServer {

    @Autowired
    UserRepository userRepository;

    public static void main(String[] args) {
        // Will configure using user-server.yml
        System.setProperty("spring.config.name", "user-server");

        SpringApplication.run(UserServer.class, args);
    }

    // ------------------------------------------
	// Bcrypt Password Encoder Bean for hashing passwords passed by the client
	// ------------------------------------------
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}