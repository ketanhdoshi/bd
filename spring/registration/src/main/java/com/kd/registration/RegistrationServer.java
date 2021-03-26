package com.kd.registration;

// This is to tell Boot to disable auto configuration of Data sources. The
// other way to do the same thing is to turn it off via a configuration setting. If 
// that is done, looks like it is no longer required to do it here. But leaving
// the code here as documentation.
// 
// import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
// import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
// @SpringBootApplication(exclude = { HibernateJpaAutoConfiguration.class, DataSourceAutoConfiguration.class })

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

// Start Netflix Eureka Server for Service Registration and Discovery.
// It runs as a standalone application and listens on a Tomcat port as per
// its configuration settings.

// Disable Security and Actuator Security, so that services don't need to authenticate to register
// Also disable Reactive Webflux load balancer auto configuration (I think)
@SpringBootApplication (exclude = {
  org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
  org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class,
  org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerBeanPostProcessorAutoConfiguration.class,
})
@EnableEurekaServer
public class RegistrationServer {
    public static void main(String[] args) {
        // Tell Boot to look for configuration settings in registration-server.yml
        // rather than the default application.yml
        System.setProperty("spring.config.name", "registration-server");
        SpringApplication.run(RegistrationServer.class, args);
      } 
}
