package routing;

import java.net.InetAddress;

public class RoutingTest {
    public static void main(String[] args) throws Exception {
        RoutingManager nodeA = new RoutingManager(InetAddress.getByName("127.0.0.1"), 5000);
        RoutingManager nodeB = new RoutingManager(InetAddress.getByName("127.0.0.1"), 5002);
        RoutingManager nodeC = new RoutingManager(InetAddress.getByName("127.0.0.1"), 5004);

        // Nachbarn hinzufügen
        nodeB.addNeighbor(InetAddress.getByName("127.0.0.1"), 5000);  // A <-> B && B <-> A
        nodeC.addNeighbor(InetAddress.getByName("127.0.0.1"), 5002);  // B <-> C && C <-> B
        nodeC.addNeighbor(InetAddress.getByName("127.0.0.1"), 5000);  // A <-> C && C <-> A

        // Starten der Manager (startet Threads)
        nodeA.start();
        nodeB.start();
        nodeC.start();

        // Einfach laufen lassen zum Beobachten
        Thread.sleep(60000);  // 60 Sekunden laufen lassen

        nodeA.stop();
        nodeB.stop();
        nodeC.stop();
    }
}
