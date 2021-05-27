package com.kd.kdspring;

import com.kd.kdspring.web.WebServer;
import com.kd.kdspring.legacy.LegacyApplication;

// ------------------------------------------
// This is the Main class for the Web Server and the Legacy application. It acts as a
// Launcher to start these off as separate processes.
// It doesn't do much else, all of the real logic resides within each microservice.
//
// You run the jar file multiple times, with this as the main class. Each
// time you pass a different parameter for which server you want to start.
// ------------------------------------------
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
 
        if (serverName.equals("web")) {
            WebServer.main(args);
        } else if (serverName.equals("legacy")) {
            // Start the Legacy Application service (which internally is a monolithic application)
            LegacyApplication.main(args);
        } else {
            System.out.println("Unknown server type: " + serverName);
        }
    }
}