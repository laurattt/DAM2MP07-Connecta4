package com.server;

import com.shared.ClientData;

import org.java_websocket.WebSocket;
import org.json.JSONArray;

import java.util.Set; 
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre de clients connectats usando nombres reales de usuarios.
 */
final class ClientRegistry {

    /** Mapa de sockets a nombres de usuario. */
    private final Map<WebSocket, String> bySocket = new ConcurrentHashMap<>();

    /** Mapa de nombres de usuario a sockets. */
    private final Map<String, WebSocket> byName = new ConcurrentHashMap<>();

    // Mapa per les dades dels clients en el joc
    private final Map<String, ClientData> clientsData = new ConcurrentHashMap<>();

    /**
     * Crea un nuevo registro
     */
    ClientRegistry() {
        // Constructor simple
    }

    /**
     * Añade un nuevo cliente con su nombre
     */
    String add(WebSocket socket, String userName) {
        bySocket.put(socket, userName);
        byName.put(userName, socket);

        // Creem dades del client
        clientsData.put(userName, new ClientData(userName, "none"));

        return userName; // Devuelve el mismo nombre que recibió
    }

    // Devolver userNames
    public Set<String> getAllUserNames() {
        return byName.keySet();
    }

    // Accedir a les dades dels clients
    ClientData getClientData(String userName) {
        return clientsData.get(userName);
    }

    void updateClientData(String userName, ClientData data) {
        clientsData.put(userName, data);
    }

    /**
     * Verifica si un nombre ya está en uso
     */
    boolean isNameTaken(String userName) {
        return byName.containsKey(userName);
    }

    /**
     * Elimina un cliente del registro
     */
    String remove(WebSocket socket) {
        String userName = bySocket.remove(socket);
        if (userName != null) {
            byName.remove(userName);
        }
        return userName;
    }

    /**
     * Obtiene el socket asociado a un nombre de usuario
     */
    WebSocket socketByName(String userName) {
        return byName.get(userName);
    }

    /**
     * Obtiene el nombre asociado a un socket
     */
    String nameBySocket(WebSocket socket) {
        return bySocket.get(socket);
    }

    /**
     * Retorna la lista actual de nombres de usuarios conectados
     */
    JSONArray currentNames() {
        JSONArray arr = new JSONArray();
        for (String userName : byName.keySet()) {
            arr.put(userName);
        }
        return arr;
    }

    /**
     * Limpia un socket desconectado
     */
    String cleanupDisconnected(WebSocket socket) {
        return remove(socket);
    }

    /**
     * Retorna una copia del mapa actual
     */
    Map<WebSocket, String> snapshot() {
        return Map.copyOf(bySocket);
    }
}