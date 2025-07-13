package routing;

import packet.*;

import java.net.*;

public class Main {
    public static void main(String[] args) {
        String ownAddress = "10.8.0.3";

        try {
            if (args.length < 1) {
                System.out.println("Usage: java routing.Main <routingPort> [neighborIP:neighborRoutingPort] ...");
                return;
            }

            // Lokale IP setzen – optional anpassen bei echtem Netzwerk
            InetAddress ownIP = InetAddress.getByName(ownAddress);

            int routingPort = Integer.parseInt(args[0]);
            int chatPort = routingPort + 1;

            // RoutingManager und ChatApp initialisieren
            RoutingManager manager = new RoutingManager(ownIP, routingPort);
            ChatApp app = new ChatApp(manager, chatPort,ownAddress);

            // Nachbarn hinzufügen, falls vorhanden
            for (int i = 1; i < args.length; i++) {
                String[] parts = args[i].split(":");
                if (parts.length != 2) {
                    System.out.println("Ungültiger Nachbar: " + args[i]);
                    continue;
                }
                InetAddress neighborIP = InetAddress.getByName(parts[0]);
                int neighborPort = Integer.parseInt(parts[1]);
                manager.addNeighbor(neighborIP, neighborPort);
            }

            // Routing starten (Broadcasts etc.)
            manager.start();

            // ChatApp starten (z.B. Konsoleingabe)
            app.start();

//            System.out.println("Node läuft:");
//            System.out.println("  RoutingPort: " + routingPort);
//            System.out.println("  ChatPort:    " + chatPort);
//            System.out.println("STRG+C zum Beenden.");

            // Keep alive
            while (true) {
                Thread.sleep(10000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
