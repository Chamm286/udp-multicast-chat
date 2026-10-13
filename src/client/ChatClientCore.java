package client;

import java.net.*;
import java.util.Enumeration;

public class ChatClientCore {

    private String serverIP;
    private int SERVER_PORT;
    private static final String MULTICAST_ADDR = "230.0.0.1";
    private static final int    MULTICAST_PORT = 7777;
    private static final int    DISCOVERY_PORT = 2025;

    // ⭐ 2 SOCKET RIÊNG: 1 cho GỬI, 1 cho NHẬN
    private final DatagramSocket sendSocket;
    private final DatagramSocket recvSocket;
    private MulticastSocket mcastSocket;
    private boolean joined = false;

    public ChatClientCore(String serverIP, int port) throws Exception {
        this.serverIP = serverIP;
        this.SERVER_PORT = port;

        // Socket gửi
        sendSocket = new DatagramSocket();
        sendSocket.setSendBufferSize(4 * 1024 * 1024);

        // ⭐ Socket NHẬN riêng — bind port ngẫu nhiên, không đụng socket gửi
        recvSocket = new DatagramSocket();
        recvSocket.setSoTimeout(0);
        recvSocket.setReceiveBufferSize(4 * 1024 * 1024);

        // Multicast cho group chat
        mcastSocket = new MulticastSocket(MULTICAST_PORT);
        mcastSocket.setReuseAddress(true);
        mcastSocket.setLoopbackMode(false);
        mcastSocket.setReceiveBufferSize(4 * 1024 * 1024);
    }

    public static String[] discoverServer(int timeoutMs) {
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket();
            ds.setBroadcast(true);
            ds.setSoTimeout(timeoutMs);
            byte[] buf = "DISCOVER_SERVER".getBytes("UTF-8");
            try {
                ds.send(new DatagramPacket(buf, buf.length,
                        InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT));
            } catch (Exception ignored) {}
            try {
                ds.send(new DatagramPacket(buf, buf.length,
                        InetAddress.getByName("127.0.0.1"), DISCOVERY_PORT));
            } catch (Exception ignored) {}

            byte[] recv = new byte[512];
            DatagramPacket respPkt = new DatagramPacket(recv, recv.length);
            ds.receive(respPkt);
            String resp = new String(respPkt.getData(), 0, respPkt.getLength(), "UTF-8").trim();
            String[] p = resp.split("\\|");
            if (p.length >= 3 && p[0].equals("SERVER_HERE")) {
                return new String[]{ p[2], p[1] };
            }
        } catch (Exception ignored) {
        } finally {
            if (ds != null) ds.close();
        }
        return null;
    }

    public String getServerIP()      { return serverIP; }
    public int    getServerPort()    { return SERVER_PORT; }
    public String getMulticastAddr() { return MULTICAST_ADDR; }
    public int    getMulticastPort() { return MULTICAST_PORT; }
    public MulticastSocket getMcSocket() { return mcastSocket; }

    /** ⭐ Socket dùng để NHẬN tin */
    public DatagramSocket getSocket() { return recvSocket; }

    /** ⭐ Port dùng để NHẬN — gửi kèm LOGIN để server biết trả lời về đâu */
    public int getRecvPort() { return recvSocket.getLocalPort(); }

    public void joinMulticast() throws Exception {
        if (joined) return;
        NetworkInterface ni = getNetworkInterface();
        if (ni != null) {
            try { mcastSocket.setNetworkInterface(ni); } catch (Exception ignored) {}
        }
        mcastSocket.joinGroup(InetAddress.getByName(MULTICAST_ADDR));
        joined = true;
    }

    public void leaveMulticast() {
        if (!joined) return;
        try {
            mcastSocket.leaveGroup(InetAddress.getByName(MULTICAST_ADDR));
            joined = false;
        } catch (Exception ignored) {}
    }

    /** ⭐ Gửi từ socket gửi */
    public void sendOnly(String req) {
        try {
            InetAddress addr = InetAddress.getByName(serverIP);
            byte[] buf = req.getBytes("UTF-8");
            sendSocket.send(new DatagramPacket(buf, buf.length, addr, SERVER_PORT));
        } catch (Exception ignored) {}
    }

    public void close() {
        leaveMulticast();
        try { if (mcastSocket != null) mcastSocket.close(); } catch (Exception ignored) {}
        try { sendSocket.close(); } catch (Exception ignored) {}
        try { recvSocket.close(); } catch (Exception ignored) {}
    }

    private NetworkInterface getNetworkInterface() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                String name = ni.getName().toLowerCase();
                if (name.contains("vbox") || name.contains("vmnet")
                    || name.contains("bluetooth") || name.contains("virtual")) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()
                        && !a.isLinkLocalAddress()) {
                        return ni;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}