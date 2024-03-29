package com.kd.kdspring.web;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

// ------------------------------------------
// Front-end web server for the UI.
//
// End Users cannot interact directly with any of the back-end microservices as they are
// not exposed externally. Their only entry is through the Web UI served by this front-end
// server. It then internally makes calls to the back-end microservices, passes them the
// security credentials as JWT Tokens in the Authorization Header.
//
// This web server is MVC Servlet based. But it makes Reactive WebClient calls to the
// backend microservices.
// ------------------------------------------

// Disable auto-configuration of Repository beans
// Also disable Reactive Webflux load balancer auto configuration (I think)
@SpringBootApplication(exclude = { 
    HibernateJpaAutoConfiguration.class, DataSourceAutoConfiguration.class,
})
// We use Eureka Discovery Service as a client to locate all our microservices.
// However being a client also means automatically registering ourself with Eureka as a service 
// (although that is not necessary since we offers no services of our own)
@EnableDiscoveryClient
// Disable Spring Boot from scanning folders for Bean classes and auto-instantiating them
// because we do it explicitly here instead. We only scan for the security Beans.
@ComponentScan(basePackages = "com.kd.kdspring.web.security")
public class WebServer {
    // URL uses the logical name of all microservices (case insensitive)
    public static final String ACCOUNT_SERVICE_URL = "http://ACCOUNT-SERVICE";
    public static final String USER_SERVICE_URL = "http://USER-SERVICE";
    public static final String LEGACY_APPLICATION_URL = "http://LEGACY-APPLICATION";

    public static void main(String[] args) {
        // Tell server to look for web-server.properties or web-server.yml
        System.setProperty("spring.config.name", "web-server");
        SpringApplication.run(WebServer.class, args);
    }

    // By default with Spring Boot, ComponentScan is turned on, and Spring Boot automatically 
    // scans all packages and finds and auto-instantatiates these Beans. However we have
    // turned off ComponentScan above. Because of that we have to define these methods
    // below to explicitly create and instantiate these Beans.  This means that any time we 
    // add any Beans such as Controllers/Services, we have to explicitly add a method for them here.

    // RestTemplate is used to make MVC Servlet client calls to backend services
    // The RestTemplate bean will be intercepted and auto-configured by Spring Cloud (due
    // to the @LoadBalanced annotation) to use a custom HttpRequestClient that uses Netflix
    // Ribbon to do the microservice lookup. Ribbon is also a load-balancer so if you have 
    // multiple instances of a service available, it picks one for you.
    //
    // @return
    @LoadBalanced
    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // WebClient is used to make Reactive Webflux client calls to backend services
    @Bean
    @LoadBalanced
    public WebClient.Builder getWebClient(){
        return WebClient.builder();
    }

    /**
     * The AccountService encapsulates the interaction with the micro-service.
     * 
     * @return A new service instance.
     */
    @Bean
    public WebAccountService accountService() {
        return new WebAccountService(ACCOUNT_SERVICE_URL);
    }

    /**
     * Create the controller, passing it the {@link WebAccountsService} to use.
     * 
     * @return
     */
    @Bean
    public WebAccountController accountsController() {
        return new WebAccountController(accountService());
    }

    /**
     * The UserService encapsulates the interaction with the micro-service.
     * 
     * @return A new service instance.
     */
    @Bean
    public WebUserService userService() {
        return new WebUserService(USER_SERVICE_URL);
    }

    /**
     * Create the controller, passing it the {@link WebUserService} to use.
     * 
     * @return
     */
    @Bean
    public WebUserController userController() {
        return new WebUserController(userService());
    }

    /**
     * The LegacyService encapsulates the interaction with the legacy application.
     * 
     * @return A new service instance.
     */
    @Bean
    public WebLegacyService legacyService() {
        return new WebLegacyService(LEGACY_APPLICATION_URL);
    }

    /**
     * Create the controller, passing it the {@link WebLegacyService} to use.
     * 
     * @return
     */
    @Bean
    public WebLegacyController legacyController() {
        return new WebLegacyController(legacyService());
    }

    /**
     * Create the controller.
     * 
     * @return
     */
    @Bean
    public WebClientController clientController() {
        return new WebClientController();
    }
}
