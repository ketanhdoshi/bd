package com.kd.kdspring;

import com.kd.kdspring.registration.RegistrationServer;
import com.kd.kdspring.account.AccountServer;
import com.kd.kdspring.web.WebServer;
import com.kd.kdspring.legacy.LegacyApplication;

// This is the Main class for the entire application. It acts as a
// central Launcher to start off all the microservices as separate processes.
// It doesn't do much else, all of the real logic resides within each microservice.
//
// You run the jar file multiple times, with this as the main class. Each
// time you pass a different parameter for which microservice you want to
// start.
public class Main {
    public static void main(String[] args) {
 
        // Take a server name argument which tells us which service to start
        String serverName = "";
 
        switch (args.length) {
        case 2:
            System.setProperty("server.port", args[1]);
        case 1:
            serverName = args[0].toLowerCase();
            break;
 
        default:
            return;
        }
 
        if (serverName.equals("registration")) {
            // Start Eureka Service Discovery
            RegistrationServer.main(args);
        } else if (serverName.equals("account")) {
            // Start the Accounts Server service
            AccountServer.main(args);
        } else if (serverName.equals("web")) {
            WebServer.main(args);
        } else if (serverName.equals("legacy")) {
            // Start the Legacy Application service (which internally is a monolithic application)
            LegacyApplication.main(args);
        } else {
            System.out.println("Unknown server type: " + serverName);
        }
    }
}