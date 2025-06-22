package routing;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class RoutingManager {

    private InetAddress ownIP;
    private int ownPort;
    private RoutingTable routingTable;
    private Set<Neighbor> neighbors;
    private DatagramSocket socket;
    private ScheduledExecutorService scheduler;

    public RoutingManager(InetAddress ownIP, int ownPort) throws SocketException {
        this.ownIP = ownIP;
        this.ownPort = ownPort;
        this.routingTable = new RoutingTable();
        this.neighbors = new HashSet<>();
        this.socket = new DatagramSocket(ownPort);
        this.scheduler = Executors.newScheduledThreadPool(2);
        initOwnEntry();
    }

    private void initOwnEntry() {
        routingTable.addEntry(new RoutingEntry(
                ownIP, ownPort,
                ownIP, ownPort,
                0
        ));
    }

    public void addNeighbor(InetAddress neighborIP, int neighborPort) {
        neighbors.add(new Neighbor(neighborIP, neighborPort));
        // Trage Nachbar initial mit HopCount 1 ein
        routingTable.addEntry(new RoutingEntry(
                neighborIP, neighborPort,
                neighborIP, neighborPort,
                1
        ));
    }

    public void start() {
        // Start Empfänger
        scheduler.execute(this::receiveLoop);
        // Start periodisches Senden alle 10 Sekunden
        scheduler.scheduleAtFixedRate(this::sendUpdatesToNeighbors, 0, 10, TimeUnit.SECONDS);
    }

    private void receiveLoop() {
        byte[] buf = new byte[2048];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                processReceivedTable(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processReceivedTable(byte[] data) {
        try {
            RoutingTable.DeserializedRoutingTable deserialized = RoutingTable.deserializeWithHeader(data);
            InetAddress sourceIP = deserialized.sourceIP;
            int sourcePort = deserialized.sourcePort;

            // Checke ob Sender überhaupt ein Nachbar ist
            boolean isNeighbor = neighbors.stream()
                    .anyMatch(n -> n.getIp().equals(sourceIP) && n.getPort() == sourcePort);

            if (!isNeighbor) {
                System.out.println("Empfangen von Nicht-Nachbar. Ignoriere.");
                return;
            }

            System.out.println("Empfange RoutingTable von " + sourceIP + ":" + sourcePort);
            updateTable(deserialized.table, sourceIP, sourcePort);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateTable(RoutingTable receivedTable, InetAddress sourceIP, int sourcePort) {
        for (RoutingEntry receivedEntry : receivedTable.getEntries()) {
            try {
                // Split Horizon prüfen
                if (receivedEntry.getNextHopIP().equals(ownIP) && receivedEntry.getNextHopPort() == ownPort) {
                    continue;
                }

                // Überspringe mich selbst
                if (receivedEntry.getDestinationIP().equals(ownIP) && receivedEntry.getDestinationPort() == ownPort) {
                    continue;
                }

                Optional<RoutingEntry> existingOpt = routingTable.getEntries().stream()
                        .filter(e -> e.getDestinationIP().equals(receivedEntry.getDestinationIP()) &&
                                e.getDestinationPort() == receivedEntry.getDestinationPort())
                        .findFirst();

                int newHopCount = receivedEntry.getHopCount() + 1;
                if (newHopCount >= 16) newHopCount = 16; // Infinity-Value

                if (existingOpt.isEmpty() && newHopCount < 16) {
                    // Neuer Eintrag
                    routingTable.addEntry(new RoutingEntry(
                            receivedEntry.getDestinationIP(),
                            receivedEntry.getDestinationPort(),
                            sourceIP,
                            sourcePort,
                            newHopCount
                    ));
                    System.out.println("Neuer Eintrag gelernt: " + receivedEntry.getDestinationIP());
                } else if (existingOpt.isPresent()) {
                    RoutingEntry existing = existingOpt.get();

                    if (existing.getNextHopIP().equals(sourceIP) && existing.getNextHopPort() == sourcePort) {
                        // Update direkt vom bekannten NextHop
                        routingTable.getEntries().remove(existing);
                        routingTable.addEntry(new RoutingEntry(
                                existing.getDestinationIP(),
                                existing.getDestinationPort(),
                                sourceIP,
                                sourcePort,
                                newHopCount
                        ));
                    } else if (newHopCount < existing.getHopCount()) {
                        // Besserer Pfad gefunden
                        routingTable.getEntries().remove(existing);
                        routingTable.addEntry(new RoutingEntry(
                                existing.getDestinationIP(),
                                existing.getDestinationPort(),
                                sourceIP,
                                sourcePort,
                                newHopCount
                        ));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendUpdatesToNeighbors() {
        for (Neighbor neighbor : neighbors) {
            try {
                byte[] data = routingTable.serializeWithHeader(ownIP, ownPort, neighbor.getIp(), neighbor.getPort());
                DatagramPacket packet = new DatagramPacket(data, data.length, neighbor.getIp(), neighbor.getPort());
                socket.send(packet);
                System.out.println("Sende RoutingTable an " + neighbor.getIp() + ":" + neighbor.getPort());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void printRoutingTable() {
        System.out.println(routingTable.toString());
    }

    public void stop() {
        scheduler.close();
    }
}
