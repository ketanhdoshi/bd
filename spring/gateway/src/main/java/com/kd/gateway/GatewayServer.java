package com.kd.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

// We use Eureka Discovery Service as a client to locate all our microservices.
// However being a client also means automatically registering ourself with Eureka as a service 
// (although that is not necessary since we offers no services of our own)
@EnableDiscoveryClient
@SpringBootApplication
public class GatewayServer {

/*     @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("acct_service_route",
                        r -> r.path("/accounts/**")
                                .uri("lb://account-service"))
                .build();
    } */

	public static void main(String[] args) {
        SpringApplication.run(GatewayServer.class, args);
        // Tell server to look for gateway-server.yml
        System.setProperty("spring.config.name", "gateway-server");
	}

}