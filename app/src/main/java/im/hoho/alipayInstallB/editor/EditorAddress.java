 package im.hoho.alipayInstallB.editor;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Enumeration;

public final class EditorAddress {

    private EditorAddress() {
    }

    public static String localUrl() {
        return "http://127.0.0.1:" + EditorHttpServer.PORT + "/";
    }

    public static String displayUrl() {
        String lanIp = findLanIp();
        return lanIp == null ? localUrl() : "http://" + lanIp + ":" + EditorHttpServer.PORT + "/";
    }

    public static String findLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
