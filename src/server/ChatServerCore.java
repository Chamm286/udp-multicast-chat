package server;

import java.net.*;
import java.util.*;

public class ChatServerCore {

    public static final int    PORT             = 2024;
    public static final int    DISCOVERY_PORT   = 2025;
    public static final String MULTICAST_ADDR   = "230.0.0.1";
    public static final int    MULTICAST_PORT   = 7777;

    public interface Logger {
        void log(String msg);
    }

    private final Logger logger;
    private volatile boolean running = true;
    private DatagramSocket mainSocket;
    private DatagramSocket discoverySocket;
    private int actualPort = PORT;

    public static class ClientInfo {
        public String username;
        public InetAddress ip;
        public int port;            // port gửi (send socket)
        public int recvPort;        // ⭐ port nhận riêng
        public long connectedAt;
        public ClientInfo(String u, InetAddress i, int p, int rp) {
            username = u; ip = i; port = p; recvPort = rp;
            connectedAt = System.currentTimeMillis();
        }
    }

    private final Map<String, ClientInfo> onlineClients = new LinkedHashMap<>();
    private final Map<String, Set<String>> groups = new LinkedHashMap<>();

    public ChatServerCore(Logger logger) { this.logger = logger; }
    public Map<String, ClientInfo> getOnlineClients() { return onlineClients; }
    public Map<String, Set<String>> getGroups()       { return groups; }
    public int getActualPort() { return actualPort; }

    public void start(int port) {
        this.actualPort = port;
        startDiscoveryServer();

        new Thread(() -> {
            try {
                mainSocket = new DatagramSocket(port, InetAddress.getByName("0.0.0.0"));
                logger.log("[OK] Server started on port " + port);
                logger.log("[MCAST] " + MULTICAST_ADDR + ":" + MULTICAST_PORT);
                logger.log("[DISCOVERY] UDP " + DISCOVERY_PORT);

                byte[] recvBuf = new byte[65535];
                while (running) {
                    DatagramPacket recvPkt = new DatagramPacket(recvBuf, recvBuf.length);
                    mainSocket.receive(recvPkt);

                    InetAddress ip = recvPkt.getAddress();
                    int portCli = recvPkt.getPort();
                    String req = new String(recvPkt.getData(), 0, recvPkt.getLength(), "UTF-8").trim();

                    handle(req, ip, portCli, mainSocket);
                }
            } catch (Exception e) {
                logger.log("[ERR] " + e.getMessage());
            }
        }, "UDP-Server").start();
    }

    private void startDiscoveryServer() {
        new Thread(() -> {
            try {
                discoverySocket = new DatagramSocket(DISCOVERY_PORT);
                discoverySocket.setBroadcast(true);

                byte[] buf = new byte[512];
                while (running) {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    discoverySocket.receive(pkt);

                    String req = new String(pkt.getData(), 0, pkt.getLength(), "UTF-8").trim();
                    if (req.equals("DISCOVER_SERVER")) {
                        String resp = "SERVER_HERE|" + actualPort + "|" + getLocalIP();
                        byte[] respBuf = resp.getBytes("UTF-8");
                        discoverySocket.send(new DatagramPacket(respBuf, respBuf.length,
                                pkt.getAddress(), pkt.getPort()));
                        logger.log("[DISCOVERY] Trả lời " + pkt.getAddress().getHostAddress());
                    }
                }
            } catch (Exception e) {
                logger.log("[ERR] Discovery: " + e.getMessage());
            }
        }, "Discovery-Server").start();
    }

    public void stop() {
        running = false;
        if (mainSocket != null) mainSocket.close();
        if (discoverySocket != null) discoverySocket.close();
    }

    private String getLocalIP() {
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
                    if (a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
                    String ip = a.getHostAddress();
                    if (ip.contains(".") && !ip.startsWith("169.254") && !ip.startsWith("192.168.56.")) return ip;
                }
            }
        } catch (Exception ignored) {}
        return "localhost";
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

    private void handle(String req, InetAddress ip, int port, DatagramSocket socket) {
        String[] p = req.split("\\|", -1);
        if (p.length == 0 || p[0].isEmpty()) return;

        switch (p[0].toUpperCase()) {
            case "LOGIN":
                // ⭐ LOGIN|username|recvPort
                if (p.length < 2) return;
                int recvPort = p.length >= 3 ? parseIntSafe(p[2]) : port;
                handleLogin(p[1], ip, port, recvPort, socket);
                break;
            case "LOGOUT":
                if (p.length < 2) return;
                handleLogout(p[1], socket);
                break;
            case "LIST_USERS":
                broadcastUserList(socket);
                broadcastGroupList(socket);
                break;
            case "UNICAST":
                if (p.length < 4) return;
                handleUnicast(p[1], p[2], p[3], socket);
                break;
            case "BROADCAST":
                if (p.length < 3) return;
                handleBroadcast(p[1], p[2], socket);
                break;
            case "JOIN_GROUP":
                if (p.length < 3) return;
                handleJoin(p[1], p[2], socket);
                break;
            case "LEAVE_GROUP":
                if (p.length < 3) return;
                handleLeave(p[1], p[2], socket);
                break;
            case "GROUP_MSG":
                if (p.length < 4) return;
                handleGroupMsg(p[1], p[2], p[3], socket);
                break;
            case "TYPING":
                if (p.length < 3) return;
                handleTyping(p[1], p[2], socket);
                break;
        }
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }

    private void handleLogin(String username, InetAddress ip, int port, int recvPort, DatagramSocket socket) {
        boolean isNew = !onlineClients.containsKey(username);
        onlineClients.put(username, new ClientInfo(username, ip, port, recvPort));
        logger.log("[ONLINE] " + username + " (" + ip.getHostAddress() + ":" + recvPort + ")");

        // OK cho client vừa login
        sendTo(ip, recvPort, "OK|Welcome " + username, socket);
        broadcastUserList(socket);
        broadcastGroupList(socket);

        if (isNew) {
            for (ClientInfo ci : onlineClients.values()) {
                if (!ci.username.equals(username)) {
                    sendTo(ci.ip, ci.recvPort, "SYS|" + username + " đã tham gia chat", socket);
                }
            }
        }

        // Gửi lại lần 2
        new Thread(() -> {
            try { Thread.sleep(500); } catch (Exception ignored) {}
            broadcastUserList(socket);
            broadcastGroupList(socket);
        }).start();
    }

    private void handleLogout(String username, DatagramSocket socket) {
        ClientInfo ci = onlineClients.remove(username);
        if (ci != null) {
            logger.log("[OFFLINE] " + username);
            broadcastUserList(socket);
            for (ClientInfo other : onlineClients.values()) {
                sendTo(other.ip, other.recvPort, "SYS|" + username + " đã rời chat", socket);
            }
        }
    }

    private void handleUnicast(String from, String to, String msg, DatagramSocket socket) {
        ClientInfo target = onlineClients.get(to);
        if (target == null) {
            ClientInfo sender = onlineClients.get(from);
            if (sender != null) sendTo(sender.ip, sender.recvPort, "ERROR|User " + to + " không online", socket);
            return;
        }
        sendTo(target.ip, target.recvPort, "MSG|UNICAST|" + from + "|" + senderIP(from) + "|" + msg, socket);
        ClientInfo sender = onlineClients.get(from);
        if (sender != null) sendTo(sender.ip, sender.recvPort, "MSG|UNICAST_ECHO|" + to + "|" + msg, socket);
        logger.log("[UNICAST] " + from + " -> " + to);
    }

    private void handleBroadcast(String from, String msg, DatagramSocket socket) {
        for (Map.Entry<String, ClientInfo> e : onlineClients.entrySet()) {
            ClientInfo ci = e.getValue();
            if (e.getKey().equals(from)) {
                sendTo(ci.ip, ci.recvPort, "MSG|BROADCAST_ECHO|" + msg, socket);
            } else {
                sendTo(ci.ip, ci.recvPort, "MSG|BROADCAST|" + from + "|" + senderIP(from) + "|" + msg, socket);
            }
        }
        logger.log("[BROADCAST] " + from);
    }

    private void handleJoin(String username, String groupId, DatagramSocket socket) {
        groups.computeIfAbsent(groupId, k -> new LinkedHashSet<>()).add(username);
        logger.log("[JOIN] " + username + " -> " + groupId);

        Set<String> members = groups.get(groupId);
        for (String m : members) {
            if (m.equals(username)) continue;
            ClientInfo ci = onlineClients.get(m);
            if (ci != null) sendTo(ci.ip, ci.recvPort, "SYS|" + username + " đã tham gia nhóm " + groupId, socket);
        }

        ClientInfo sender = onlineClients.get(username);
        if (sender != null) sendTo(sender.ip, sender.recvPort, "OK|Bạn đã tham gia nhóm " + groupId, socket);
        broadcastGroupList(socket);
    }

    private void handleLeave(String username, String groupId, DatagramSocket socket) {
        Set<String> members = groups.get(groupId);
        if (members != null) {
            members.remove(username);
            logger.log("[LEAVE] " + username + " <- " + groupId);
            for (String m : members) {
                ClientInfo ci = onlineClients.get(m);
                if (ci != null) sendTo(ci.ip, ci.recvPort, "SYS|" + username + " đã rời nhóm " + groupId, socket);
            }
        }
        ClientInfo sender = onlineClients.get(username);
        if (sender != null) sendTo(sender.ip, sender.recvPort, "OK|Bạn đã rời nhóm " + groupId, socket);
        broadcastGroupList(socket);
    }

    private void handleGroupMsg(String from, String groupId, String msg, DatagramSocket socket) {
        Set<String> members = groups.get(groupId);
        if (members == null || !members.contains(from)) {
            ClientInfo sender = onlineClients.get(from);
            if (sender != null) sendTo(sender.ip, sender.recvPort, "ERROR|Bạn chưa ở trong nhóm " + groupId, socket);
            return;
        }

        String packet = "MCAST|" + groupId + "|" + from + "|" + senderIP(from) + "|" + msg;
        try {
            MulticastSocket mcastSocket = new MulticastSocket();
            mcastSocket.setTimeToLive(1);
            mcastSocket.setLoopbackMode(false);
            NetworkInterface ni = getNetworkInterface();
            if (ni != null) mcastSocket.setNetworkInterface(ni);

            byte[] buf = packet.getBytes("UTF-8");
            InetAddress mcastAddr = InetAddress.getByName(MULTICAST_ADDR);
            mcastSocket.send(new DatagramPacket(buf, buf.length, mcastAddr, MULTICAST_PORT));
            mcastSocket.close();
            logger.log("[MCAST -> " + groupId + "] " + from + ": " + msg);
        } catch (Exception e) {
            logger.log("[ERR] Multicast: " + e.getMessage());
        }

        ClientInfo sender = onlineClients.get(from);
        if (sender != null)
            sendTo(sender.ip, sender.recvPort, "MSG|GROUP_ECHO|" + groupId + "|" + msg, socket);
    }

    private void handleTyping(String from, String to, DatagramSocket socket) {
        if (to.startsWith("GROUP:")) {
            String groupId = to.substring(6);
            Set<String> members = groups.get(groupId);
            if (members != null) {
                for (String m : members) {
                    if (m.equals(from)) continue;
                    ClientInfo ci = onlineClients.get(m);
                    if (ci != null) sendTo(ci.ip, ci.recvPort, "TYPING|" + from + "|" + to, socket);
                }
            }
        } else {
            ClientInfo target = onlineClients.get(to);
            if (target != null) {
                sendTo(target.ip, target.recvPort, "TYPING|" + from + "|" + to, socket);
            }
        }
    }

    private void broadcastUserList(DatagramSocket socket) {
        StringBuilder sb = new StringBuilder("USERLIST|");
        for (String u : onlineClients.keySet()) sb.append(u).append(",");
        String listMsg = sb.toString();
        for (ClientInfo ci : onlineClients.values()) sendTo(ci.ip, ci.recvPort, listMsg, socket);
    }

    private void broadcastGroupList(DatagramSocket socket) {
        StringBuilder sb = new StringBuilder("GROUPLIST|");
        for (Map.Entry<String, Set<String>> e : groups.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            sb.append(e.getKey()).append(":");
            sb.append(String.join(",", e.getValue())).append(";");
        }
        String msg = sb.toString();
        for (ClientInfo ci : onlineClients.values()) sendTo(ci.ip, ci.recvPort, msg, socket);
    }

    private String senderIP(String username) {
        ClientInfo ci = onlineClients.get(username);
        return ci != null ? ci.ip.getHostAddress() : "unknown";
    }

    private void sendTo(InetAddress ip, int port, String msg, DatagramSocket socket) {
        try {
            byte[] buf = msg.getBytes("UTF-8");
            if (buf.length > 65000) return;
            socket.send(new DatagramPacket(buf, buf.length, ip, port));
        } catch (Exception e) {
            logger.log("[ERR] sendTo: " + e.getMessage());
        }
    }
}