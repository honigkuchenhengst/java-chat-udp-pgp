package routing;

import packet.ChatApp;
import packet.Packet;

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
    private boolean logging = false;

    public RoutingManager(InetAddress ownIP, int ownPort) throws SocketException {
        this.ownIP = ownIP;
        this.ownPort = ownPort;
        this.routingTable = new RoutingTable();
        this.neighbors = new ArrayList<>();
        this.socket = new DatagramSocket(new InetSocketAddress(ownIP, ownPort));
        this.scheduler = Executors.newScheduledThreadPool(3);
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
        // Zusätzlich: alle 15 Sekunden RoutingTable ausgeben, vermeidet synchrone ausgaben (man sieht auch mal enträge zwischen zwei Sendungen)
        scheduler.scheduleAtFixedRate(this::printRoutingTable, 5, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkForUnreachability, 7, 10, TimeUnit.SECONDS);
    }

    public void receiveLoop() {
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

    private void disconnect(InetAddress address,int port){
        Neighbor neighborToForget = new Neighbor(address, port);
        if(!this.neighbors.contains(neighborToForget)){
            return;
        }
        this.neighbors.remove(neighborToForget);
        Optional<RoutingEntry> existingOpt = routingTable.getEntries().stream()
                .filter(e -> e.getDestinationIP().equals(address) &&
                        e.getDestinationPort() == port)
                .findFirst();
        this.routingTable.getEntries().remove(existingOpt.get());
        this.routingTable.addEntry(new RoutingEntry(address,port,address,port,inf));
        this.sendUpdatesToNeighbors();
    }

    private void connect(InetAddress address, int port){
        this.neighbors.add(new Neighbor(address, port));
        try {
            byte[] data = routingTable.serializeWithHeaderOnlyThisNode(ownIP, ownPort, address, port);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
            if(logging) {
                System.err.println("Sende RoutingTable an " + address + ":" + port);
            }
        } catch (IOException e) {
            e.printStackTrace();
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
                System.err.println(deserialized.table);
                if(logging) {
                    System.err.println("Empfangen von Nicht-Nachbar. Ignoriere.");
                }
                return;
            }
            if(logging){

                System.err.println("Empfange RoutingTable von " + sourceIP + ":" + sourcePort);
            }
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
                        if(!this.neighbors.contains(new Neighbor(sourceIP, sourcePort))){
                            this.neighbors.add(new Neighbor(sourceIP, sourcePort));
                        }
                    }
                    updated = true;
                    if(logging) {
                        System.err.println("Neuer Eintrag gelernt: " + receivedEntry.getDestinationIP() + ":" + receivedEntry.getDestinationPort());
                    }
                } else if (existingOpt.isPresent()) {
                    RoutingEntry existingEntry = existingOpt.get();
                    //if (sourceIP.equals(existingEntry.getDestinationIP()) && sourcePort == existingEntry.getDestinationPort()) {
                        Neighbor tmpNeighbor = new Neighbor(sourceIP, sourcePort);
                        if (this.neighbors.contains(tmpNeighbor)) {
                            //aktualisiere Timer
                            neighbors.get(neighbors.indexOf(tmpNeighbor)).updateTimestamp();
                            neighbors.get(neighbors.indexOf(tmpNeighbor)).setTimerInfOrDelete(true);
                        }
                    //}

                    if(newHopCount == 1 && newHopCount != existingEntry.getHopCount() && sourceIP.equals(existingEntry.getDestinationIP()) && sourcePort == existingEntry.getDestinationPort()){
                        if(!this.neighbors.contains(new Neighbor(sourceIP, sourcePort))){
                            this.neighbors.add(new Neighbor(sourceIP, sourcePort));
                        }
                        routingTable.getEntries().remove(existingEntry);
                        routingTable.addEntry(new RoutingEntry(
                                existingEntry.getDestinationIP(),
                                existingEntry.getDestinationPort(),
                                sourceIP,
                                sourcePort,
                                newHopCount));
                        updated = true;
                    }

                    else if (existingEntry.getNextHopIP().equals(sourceIP) && existingEntry.getNextHopPort() == sourcePort && newHopCount != existingEntry.getHopCount()) {
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


                    } else if (newHopCount < existingEntry.getHopCount() && sourceIP.equals(existingEntry.getDestinationIP()) && sourcePort == existingEntry.getDestinationPort()) {
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
                if(logging) {
                    System.err.println("Sende RoutingTable an " + neighbor.getIp() + ":" + neighbor.getPort());
                }
                } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void checkForUnreachability() {
        for(Neighbor neighbor : neighbors) {
            RoutingEntry entry = routingTable.getEntries().stream()
                    .filter(e -> e.getDestinationIP().equals(neighbor.getIp()) &&
                            e.getDestinationPort() == neighbor.getPort())
                    .findFirst().orElse(null);
            if (entry == null) {
                continue;
            }
            if(neighbor.getTimerInfOrDelete() && System.currentTimeMillis() -neighbor.getLastUpdateTime() > 30_000){
                neighbor.setTimerInfOrDelete(false);
                neighbor.updateTimestamp();
                int neighborId = this.routingTable.getEntries().indexOf(entry);
                this.routingTable.getEntries().get(neighborId).setHopCount(inf);
                //Alle durch diesen Nachbarn verbundene Knoten auf nicht erreichbar setzen
                List<RoutingEntry> entries = this.routingTable.getEntries();
                for(RoutingEntry e : entries) {
                    if(e.getNextHopIP().equals(neighbor.getIp()) && e.getNextHopPort() == neighbor.getPort()){
                        this.routingTable.getEntries().get(this.routingTable.getEntries().indexOf(e)).setHopCount(inf);
                    }
                }
            } else if(!neighbor.getTimerInfOrDelete() && System.currentTimeMillis() -neighbor.getLastUpdateTime() > 90_000){
                this.routingTable.getEntries().remove(entry);
                this.neighbors.remove(neighbor);
                List<RoutingEntry> entries = this.routingTable.getEntries();
                //Alle durch diesen Nachbarn verbundene Knoten loeschen
                for(RoutingEntry e : entries) {
                    if(e.getNextHopIP().equals(neighbor.getIp()) && e.getNextHopPort() == neighbor.getPort()){
                        this.routingTable.getEntries().remove(e);
                    }
                }
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
                if(logging) {
                    System.err.println("Sende RoutingTable an " + neighbor.getIp() + ":" + neighbor.getPort());
                }
                } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void printRoutingTable() {
        if(logging) {
            System.err.println(routingTable.toString());
        }
    }

    public void stop() {
        scheduler.close();
    }

    public void sendMessageTo(DatagramSocket chatSocket, InetAddress destIP, int destPort, Packet packet) {
        Optional<RoutingEntry> routeOpt = routingTable.getEntries().stream()
                .filter(e -> e.getDestinationIP().equals(destIP) && e.getDestinationPort() == destPort)
                .findFirst();

        if (routeOpt.isEmpty()) {
            System.err.println("Ziel " + destIP + ":" + destPort + " nicht in Routing-Tabelle.");
            return;
        }

        RoutingEntry route = routeOpt.get();

        if (route.getHopCount() >= inf) {
            System.err.println("Ziel " + destIP + ":" + destPort + " ist als 'unreachable' markiert.");
            return;
        }

        InetAddress nextHopIP = route.getNextHopIP();
        int nextHopPort = route.getNextHopPort();

        try {
            byte[] data = packet.serialize();  // Nimm die Methode aus deiner Packet-Klasse
            DatagramPacket udpPacket = new DatagramPacket(data, data.length, nextHopIP, nextHopPort);
            chatSocket.send(udpPacket);
            if (logging) {
                System.err.println("Sende Nachricht an " + destIP + ":" + destPort +
                        " über " + nextHopIP + ":" + nextHopPort);
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Senden der Nachricht: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
