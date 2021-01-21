package com.kd.kdspring.registration;

// import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
// import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
// @SpringBootApplication(exclude = { HibernateJpaAutoConfiguration.class, DataSourceAutoConfiguration.class })

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class RegistrationServer {
    public static void main(String[] args) {
        // Tell Boot to look for configuration settings in registration-server.yml
        System.setProperty("spring.config.name", "registration-server");
        SpringApplication.run(RegistrationServer.class, args);
      } 
}
