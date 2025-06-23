package routing;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class RoutingManager {

    private InetAddress ownIP;
    private int ownPort;
    private RoutingTable routingTable;
    private List<Neighbor> neighbors; //TODO Lieber ne andere Datenstruktur um Timer besser aktualisieren zu können?
    private DatagramSocket socket;
    private ScheduledExecutorService scheduler;
    private int inf;

    public RoutingManager(InetAddress ownIP, int ownPort) throws SocketException {
        this.ownIP = ownIP;
        this.ownPort = ownPort;
        this.routingTable = new RoutingTable();
        this.neighbors = new ArrayList<>();
        this.socket = new DatagramSocket(ownPort);
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.inf = 16;
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
        //TODO halte ich für keine gute Idee, wenn es den Nachbar nicht gibt hat man ihn trotzdem in der Tabelle
        /*
        routingTable.addEntry(new RoutingEntry(
                neighborIP, neighborPort,
                neighborIP, neighborPort,
                1
        ));
                */

    }

    public void start() {
        // Start Empfänger
        scheduler.execute(this::receiveLoop);
        // Start periodisches Senden alle 10 Sekunden
        scheduler.scheduleAtFixedRate(this::sendUpdatesToNeighbors, 0, 10, TimeUnit.SECONDS);
        // Zusätzlich: alle 15 Sekunden RoutingTable ausgeben
        scheduler.scheduleAtFixedRate(this::printRoutingTable, 5, 10, TimeUnit.SECONDS);
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

            //Checke ob Tabelle nicht von Nachbarn oder neuem Knoten kommt
            if (!isNeighbor && deserialized.table.getSize() > 1) {
                System.out.println("Empfangen von Nicht-Nachbar. Ignoriere.");
                return;
            }

            System.out.println("Empfange RoutingTable von " + sourceIP + ":" + sourcePort);
            updateTable(deserialized.table, sourceIP, sourcePort);

        } catch (Exception e) {
            //TODO ordentlicher Exception Umgang
            e.printStackTrace();
        }
    }

    private void updateTable(RoutingTable receivedTable, InetAddress sourceIP, int sourcePort) {
        boolean updated = false;
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
                if (newHopCount >= inf) newHopCount = inf;

                if (existingOpt.isEmpty() && newHopCount < inf) {
                    // Neuer Eintrag
                    routingTable.addEntry(new RoutingEntry(
                            receivedEntry.getDestinationIP(),
                            receivedEntry.getDestinationPort(),
                            sourceIP,
                            sourcePort,
                            newHopCount
                    ));
                    //Neuer Eintrag ist auch Nachbar
                    if(newHopCount == 1){
                        this.neighbors.add(new Neighbor(receivedEntry.getDestinationIP(), receivedEntry.getDestinationPort()));
                    }
                    updated = true;
                    System.out.println("Neuer Eintrag gelernt: " + receivedEntry.getDestinationIP() + ":" + receivedEntry.getDestinationPort());

                } else if (existingOpt.isPresent()) {
                    RoutingEntry existingEntry = existingOpt.get();
                    Neighbor tmpNeighbor = new Neighbor(existingEntry.getDestinationIP(), existingEntry.getDestinationPort());
                    if( this.neighbors.contains(tmpNeighbor)) {
                        //aktualisiere Timer
                        neighbors.get(neighbors.indexOf(tmpNeighbor)).updateTimestamp();
                    }

                    if (existingEntry.getNextHopIP().equals(sourceIP) && existingEntry.getNextHopPort() == sourcePort && newHopCount != existingEntry.getHopCount()) {
                        // Update direkt vom bekannten NextHop
                        routingTable.getEntries().remove(existingEntry);
                        routingTable.addEntry(new RoutingEntry(
                                existingEntry.getDestinationIP(),
                                existingEntry.getDestinationPort(),
                                sourceIP,
                                sourcePort,
                                newHopCount
                        ));
                        updated = true;


                    } else if (newHopCount < existingEntry.getHopCount()) {
                        // Besserer Pfad gefunden
                        routingTable.getEntries().remove(existingEntry);
                        routingTable.addEntry(new RoutingEntry(
                                existingEntry.getDestinationIP(),
                                existingEntry.getDestinationPort(),
                                sourceIP,
                                sourcePort,
                                newHopCount
                        ));
                        updated = true;
                    }
                }
            } catch (Exception e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
            }
        }
        if (updated) {
            //sende Routingtabelle an alle nachbarn außer Sender der Updates
            this.sendUpdatesToNeighbors(new Neighbor(sourceIP, sourcePort));
            //TODO
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

    private void sendUpdatesToNeighbors(Neighbor neighbor2Skip) {
        for (Neighbor neighbor : neighbors) {
            if(neighbor.equals(neighbor2Skip)) {
                continue;
            }
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
