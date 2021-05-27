package com.kd.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

// ------------------------------------------
// API Gateway Server
//
// The backend microservices are not visible to external REST clients. So their
// only entry to make REST API calls is through the API Gateway. They pass in
// security credentials in the form of JWT tokens in Authorization Request Headers.
// The Gateway forwards those on to the microservices.
// ------------------------------------------

// We use Eureka Discovery Service as a client to locate all our microservices.
// However being a client also means automatically registering ourself with Eureka as a service 
// (although that is not necessary since we offers no services of our own)
@EnableDiscoveryClient
@SpringBootApplication
public class GatewayServer {

	public static void main(String[] args) {
        // Look for configuration properties in gateway-server.yml
        // All the routing rules are defined in the config file
        System.setProperty("spring.config.name", "gateway-server");
        SpringApplication.run(GatewayServer.class, args);
	}
}