package com.kd.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// Disable Reactive Webflux load balancer auto configuration
// Also disable Security auto-configuration - so that it doesn't create a default user in the in-memory db
@SpringBootApplication (exclude = {
    org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerBeanPostProcessorAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration.class
})
// Enable service registration and discovery, so it registers itself with the discovery-server service
@EnableDiscoveryClient
public class AccountServer {

    public static void main(String[] args) {
        // Will configure using account-server.yml
        System.setProperty("spring.config.name", "account-server");

        SpringApplication.run(AccountServer.class, args);
    }
}