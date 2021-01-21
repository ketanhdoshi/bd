package com.kd.kdspring;

import com.kd.kdspring.registration.RegistrationServer;

public class Main {
    public static void main(String[] args) {
 
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
            RegistrationServer.main(args);
        } else if (serverName.equals("addition")) {
            RegistrationServer.main(args);
        } else if (serverName.equals("subtraction")) {
            RegistrationServer.main(args);
        } else if (serverName.equals("web")) {
            KdspringApplication.main(args);
        } else {
            System.out.println("Unknown server type: " + serverName);
        }
    }
}