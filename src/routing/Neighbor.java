package routing;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Neighbor {
    private final InetAddress ip;
    private final int port;
    private long lastUpdateTime;

    public Neighbor(InetAddress ip, int port) {
        this.ip = ip;
        this.port = port;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public InetAddress getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void updateTimestamp() {
        this.lastUpdateTime = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return ip.getHostAddress() + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Neighbor)) return false;
        Neighbor other = (Neighbor) o;
        return this.port == other.port && this.ip.equals(other.ip);
    }

    @Override
    public int hashCode() {
        return ip.hashCode() * 31 + port;
    }
}
