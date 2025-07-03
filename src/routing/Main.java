package routing;
import packet.*;

import java.net.*;

public class Main {
    public static void main(String[] args) {

        try {
            if (args.length < 2) {
                System.out.println("Usage: java routing.Main <routingPort> <chatPort> [neighborIP:neighborRoutingPort] ...");
                return;
            }

            InetAddress ownIP = InetAddress.getByName("127.0.0.1");
            int routingPort = Integer.parseInt(args[0]);
            int chatPort = Integer.parseInt(args[1]);

            RoutingManager manager = new RoutingManager(ownIP, routingPort);
            ChatApp app = new ChatApp(manager, chatPort);

            // Nachbarn hinzufügen, falls vorhanden
            for (int i = 2; i < args.length; i++) {
                String[] parts = args[i].split(":");
                if (parts.length != 2) {
                    System.out.println("Ungültiger Nachbar: " + args[i]);
                    continue;
                }
                InetAddress neighborIP = InetAddress.getByName(parts[0]);
                int neighborPort = Integer.parseInt(parts[1]);
                manager.addNeighbor(neighborIP, neighborPort);
            }

            manager.start();
            app.start();

            System.out.println("Node läuft mit RoutingPort " + routingPort + " und ChatPort " + chatPort);
            System.err.println("STRG+C zum Beenden.");

            // Einfach dauerhaft laufen lassen
            while (true) {
                Thread.sleep(10000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
