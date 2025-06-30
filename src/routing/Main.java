package routing;

import java.net.*;

public class Main {
    public static void main(String[] args) {
        try {
            //TODO manpage oder -help o.Ä.
            if (args.length < 1) {
                System.out.println("Usage: java routing.Main <ownPort> [neighborIP:neighborPort] ...");
                return;
            }

            InetAddress ownIP = InetAddress.getByName("192.168.178.20");
            int ownPort = Integer.parseInt(args[0]);

            RoutingManager manager = new RoutingManager(ownIP, ownPort);

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

            manager.start();

            System.out.println("Routing Node läuft auf " + ownIP.getHostAddress() + ":" + ownPort);
            System.out.println("STRG+C zum Beenden.");

            // Einfach dauerhaft laufen lassen
            while (true) {
                Thread.sleep(10000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
