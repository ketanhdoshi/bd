package com.kd.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.beans.factory.annotation.Autowired;

// Disable Reactive Webflux load balancer auto configuration
@SpringBootApplication (exclude = {
    org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerBeanPostProcessorAutoConfiguration.class,
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