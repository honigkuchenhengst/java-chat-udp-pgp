package routing;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Neighbor {
    private final InetAddress ip;
    private final int port;
    private long lastUpdateTime;
    private boolean timerInfOrDelete; //true == inf, false == delete

    public Neighbor(InetAddress ip, int port) {
        this.ip = ip;
        this.port = port;
        this.lastUpdateTime = System.currentTimeMillis();
        this.timerInfOrDelete = true;
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

    public boolean getTimerInfOrDelete() {
        return timerInfOrDelete;
    }

    public void setTimerInfOrDelete(boolean timerInfOrDelete) {
        this.timerInfOrDelete = timerInfOrDelete;
    }
}
