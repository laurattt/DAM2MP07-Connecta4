package com.server;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * Servidor WebSocket amb routing simple de missatges, sense REPL.
 */
public class Main extends WebSocketServer {

    /** Port per defecte on escolta el servidor. */
    public static final int DEFAULT_PORT = 3000;

    // Claus JSON
    private static final String K_TYPE = "type";
    private static final String K_MESSAGE = "message";
    private static final String K_ORIGIN = "origin";
    private static final String K_DESTINATION = "destination";
    private static final String K_ID = "id";
    private static final String K_LIST = "list";
    private static final String K_USERNAME = "userName"; // nueva clave para el nombre de usuario

    // Tipus de missatge
    private static final String T_BOUNCE = "bounce";
    private static final String T_BROADCAST = "broadcast";
    private static final String T_PRIVATE = "private";
    private static final String T_CLIENTS = "clients";
    private static final String T_ERROR = "error";
    private static final String T_CONFIRMATION = "confirmation";
    private static final String T_USER_INFO = "userInfo"; // user info

    /** Registre de clients i assignació de noms (pool integrat). */
    private final ClientRegistry clients;

    /**
     * Crea un servidor WebSocket que escolta a l'adreça indicada.
     */
    public Main(InetSocketAddress address) {
        super(address);
        this.clients = new ClientRegistry(); // Sin parámetros
    }

    // ----------------- Helpers JSON -----------------

    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    private static void put(JSONObject o, String k, Object v) {
        if (v != null) o.put(k, v);
    }

    private void sendSafe(WebSocket to, String payload) {
        if (to == null) return;
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            String name = clients.cleanupDisconnected(to);
            System.out.println("Client desconnectat durant send: " + name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcastExcept(WebSocket sender, String payload) {
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            if (!Objects.equals(conn, sender)) sendSafe(conn, payload);
        }
    }

    /**
     * Envia la llista actualitzada de clients a tots els clients connectats.
     */
    private void sendClientsListToAll() {
        JSONArray list = clients.currentNames();
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            JSONObject rst = msg(T_CLIENTS);
            put(rst, K_ID, e.getValue()); // ID único del cliente
            put(rst, K_LIST, list);
            sendSafe(e.getKey(), rst.toString());
        }
    }

    // ----------------- WebSocketServer overrides -----------------

   @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // SOLO recibir el nombre del usuario
        System.out.println("Nueva conexión establecida, esperando nombre de usuario...");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String userName = clients.remove(conn);
        System.out.println("Client desconnectat: " + userName);
        
        // notif de desconexion
        if (userName != null) {
            JSONObject leaveMsg = msg("userLeft");
            leaveMsg.put("userName", userName);
            broadcastExcept(null, leaveMsg.toString());
            sendClientsListToAll();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String originName = clients.nameBySocket(conn);
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "JSON invàlid").toString());
            return;
        }

        String type = obj.optString(K_TYPE, "");
        switch (type) {
            case T_USER_INFO -> {
                
                String userName = obj.optString(K_USERNAME, "").trim();
                if (!userName.isEmpty()) {
                    // nombre en uso (esto generaba conflicto)
                    if (clients.isNameTaken(userName)) {
                        sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Nombre de usuario ya en uso").toString());
                        conn.close();
                        return;
                    }
                    
                    // resgistro user
                    String assignedName = clients.add(conn, userName);
                    System.out.println("Usuario registrado: " + assignedName);
                    
                    // nuevo user en el chat
                    JSONObject joinMsg = msg("userJoined");
                    joinMsg.put("userName", assignedName);
                    broadcastExcept(conn, joinMsg.toString());
                    
                    // lista actualizada clientes
                    sendClientsListToAll();
                }
            }
            case T_BOUNCE -> {
                if (originName == null) {
                    sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Usuario no registrado").toString());
                    return;
                }
                String txt = obj.optString(K_MESSAGE, "");
                JSONObject rst = msg(T_BOUNCE)
                    .put(K_MESSAGE, txt)
                    .put(K_ORIGIN, originName);
                sendSafe(conn, rst.toString());
            }
            case T_BROADCAST -> {
                if (originName == null) {
                    sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Usuario no registrado").toString());
                    return;
                }
                String txt = obj.optString(K_MESSAGE, "");
                JSONObject rst = msg(T_BROADCAST)
                    .put(K_ORIGIN, originName)
                    .put(K_MESSAGE, txt);
                broadcastExcept(conn, rst.toString());
            }
            case T_PRIVATE -> {
                if (originName == null) {
                    sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Usuario no registrado").toString());
                    return;
                }
                String destName = obj.optString(K_DESTINATION, "");
                if (destName.isBlank()) {
                    sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Falta 'destination'").toString());
                    return;
                }
                WebSocket dest = clients.socketByName(destName);
                if (dest == null) {
                    sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Usuario " + destName + " no disponible.").toString());
                    return;
                }
                String txt = obj.optString(K_MESSAGE, "");
                
                // enviar al destinatario
                sendSafe(dest, msg(T_PRIVATE)
                    .put(K_ORIGIN, originName)
                    .put(K_DESTINATION, destName)
                    .put(K_MESSAGE, txt)
                    .toString());
                    
                // confirmacion al remitente
                sendSafe(conn, msg(T_CONFIRMATION)
                    .put(K_MESSAGE, "Mensaje enviado a " + destName)
                    .toString());
            }
            default -> {
                sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Tipo desconocido: " + type).toString());
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Servidor WebSocket engegat al port: " + getPort());
        setConnectionLostTimeout(100);
    }

    // ----------------- Lifecycle util -----------------

    private static void registerShutdownHook(Main server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Aturant servidor (shutdown hook)...");
            try {
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servidor aturat.");
        }));
    }

    private static void awaitForever() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);

        System.out.println("Servidor WebSocket en execució al port " + DEFAULT_PORT + ". Prem Ctrl+C per aturar-lo.");
        awaitForever();
    }
}
