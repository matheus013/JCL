package implementations.dm_kernel.user;

import java.util.Map;

public class DeviceProperty {

    private String host;
    private String port;
    private String mac;
    private String portS;
    private int size = 0;

    public DeviceProperty() {

    }

    public DeviceProperty(Map<String, String> hostPort) {
        host = hostPort.get("IP");
        port = hostPort.get("PORT");
        mac = hostPort.get("MAC");
        portS = hostPort.get("PORT_SUPER_PEER");
        size = hostPort.size();
    }

    public DeviceProperty(Map.Entry<String, Map<String, String>> hostPort) {
        host = hostPort.getValue().get("IP");
        port = hostPort.getValue().get("PORT");
        mac = hostPort.getValue().get("MAC");
        portS = hostPort.getValue().get("PORT_SUPER_PEER");
//        size = hostPort.size();

    }

    public String host() {
        return this.host;
    }

    public String port() {
        return this.port;
    }

    public String mac() {
        return this.mac;
    }

    public String portS() {
        return this.portS;
    }

    public int getSize() {
        return size;
    }

    public String sumAll() {
        return host + port + mac + portS;
    }
}
