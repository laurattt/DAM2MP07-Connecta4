package com.server;

import com.shared.ClientData;
import com.shared.GameObject;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final String K_ACCEPTED = "accepted"; // clave para respuesta de invitación

    // Tipus de missatge
    private static final String T_BOUNCE = "bounce";
    private static final String T_BROADCAST = "broadcast";
    private static final String T_PRIVATE = "private";
    private static final String T_INVITATION = "invite to play";
    private static final String T_INVITE_RESPONSE = "invite response";
    private static final String T_CLIENTS = "clients";
    private static final String T_ERROR = "error";
    private static final String T_CONFIRMATION = "confirmation";
    private static final String T_USER_INFO = "userInfo"; // user info
    // Countdown
    private static final String T_COUNTDOWN = "countdown";
    // Per passar informació durant la partida
    private static final String T_CLIENT_MOUSE_MOVING = "clientMouseMoving";
    private static final String T_CLIENT_PIECE_MOVING = "clientPieceMoving";
    private static final String T_CLIENT_PLAY = "clientPlay";
    private static final String T_SERVER_DATA = "serverData";

    // Estats del joc
    private enum GameStatus { WAITING, COUNTDOWN, PLAYING, WIN, DRAW }
    private GameStatus gameStatus = GameStatus.WAITING;

    // Variables per les dades que s'han d'intercanviar
    private String[][] gameBoard = new String[6][7];
    private String currentTurnPlayer = null;
    private String lastMovePlayer = null;
    private int lastMoveCol = -1;
    private int lastMoveRow = -1;
    private String winner = "";

    // Rols dels jugadors
    private String playerRed = null;
    private String playerYellow = null;
    

    /** Registre de clients i assignació de noms (pool integrat). */
    private final ClientRegistry clients;

    // Mapes per guardar les dades dels clients i les posicions de les fitxes
    private final Map<String, ClientData> clientsData = new ConcurrentHashMap<>();
    private final Map<String, JSONObject> draggingPieces = new ConcurrentHashMap<>();
    

    /**
     * Crea un servidor WebSocket que escolta a l'adreça indicada.
     */
    public Main(InetSocketAddress address) {
        super(address);
        this.clients = new ClientRegistry(); // Sin parámetros
    }

    private void initializeBoard() {
        for (int row = 0; row < gameBoard.length; row++) {
            for (int col = 0; col < gameBoard[0].length; col++) {
                gameBoard[row][col] = null; // Cap fitxa en aquesta posició
            }
        }
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
            // ---------------------------------------------------
            // Neteja de dades relacionades amb el client desconnectat

            draggingPieces.remove(name);
            //clientsData.remove(name);

            // ----------------------------------------------------
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
        //String userName = clients.remove(conn);
        String userName = clients.cleanupDisconnected(conn);
        System.out.println("Client desconnectat: " + userName);
        
        // notif de desconexion
        if (userName != null) {
            draggingPieces.remove(userName);

            // Si estava en una partida, gestionar la seva sortida
            if (userName.equals(playerRed) || userName.equals(playerYellow)) {
                handlePlayerDisconnected(userName);
            }

            JSONObject leaveMsg = msg("userLeft");
            leaveMsg.put("userName", userName);
            broadcastExcept(null, leaveMsg.toString());
            sendClientsListToAll();
        }
    }

    private void handlePlayerDisconnected(String userName) {
        // Finalitzar la partida
        gameStatus = GameStatus.WAITING;
        winner = userName.equals(playerRed) ? playerYellow : playerRed;
        broadcastServerData(); // Quan el jugador rebi aquest estat, el jugador canviarà a la vista de guanyadors
        // Resetejar el joc
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                resetGame();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void resetGame() {
        gameStatus = GameStatus.WAITING;
        playerRed = null;
        playerYellow = null;
        currentTurnPlayer = null;
        winner = "";
        initializeBoard();
        draggingPieces.clear();
        
        // A lo mejor se puede retocar para que solo se resetee lo de los jugadores de la partida
        // Resetejar el color dels clients
        for (String userName : clients.getAllUserNames()) {
            ClientData clientData = clients.getClientData(userName);
            if (clientData != null) {
                clientData.color = "none";
            }
        }
        
        broadcastServerData();
    }

    // El que ha de fer el servidor quan rep cadascun dels diferents tipus de missatge
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
            case T_INVITATION -> {
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
                
                // enviar invitacion al destinatario
                sendSafe(dest, msg(T_INVITATION)
                    .put(K_ORIGIN, originName)
                    .put(K_DESTINATION, destName)
                    .put(K_MESSAGE, txt)
                    .toString());
                    
                // confirmacion al remitente
                sendSafe(conn, msg(T_CONFIRMATION)
                    .put(K_MESSAGE, "Invitación enviada a " + destName)
                    .toString());
            }
            case T_INVITE_RESPONSE -> {
                if (originName == null) {
                    sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Usuario no registrado").toString());
                    return;
                }

                String destName = obj.optString(K_DESTINATION, "");
                boolean accepted = obj.optBoolean("accepted", false); // intentamos obtener si se aceptó o no y si no existe ese campo, por defecto es false

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
                
                // enviar respuesta al remitente original de la invitacion
                sendSafe(dest, msg(T_INVITE_RESPONSE)
                    .put(K_ORIGIN, originName)
                    .put(K_DESTINATION, destName)
                    .put(K_ACCEPTED, accepted)
                    .toString());
                    
                // confirmacion al remitente
                sendSafe(conn, msg(T_CONFIRMATION)
                    .put(K_MESSAGE, "Respuesta enviada a " + destName)
                    .toString());

                if (accepted) {
                    startGame(originName, destName);
                }
            }
            case T_CLIENT_MOUSE_MOVING -> {
                // Actualitzem la posició del ratolí del jugador i la compartim amb l'altre jugador
                updateClientMouse(originName, obj.getJSONObject("value"));
                broadcastServerData();
            }
            case T_CLIENT_PIECE_MOVING -> {
                // Només si és el torn del jugador
                if (originName.equals(currentTurnPlayer) && gameStatus == GameStatus.PLAYING) {
                    updatePiecePosition(originName, obj.getJSONObject("value"));
                    broadcastServerData();
                }
            }
            case T_CLIENT_PLAY -> {
                // Només si és el torn del jugador
                if (originName.equals(currentTurnPlayer) && gameStatus == GameStatus.PLAYING) {
                    processPlayerMove(originName, obj.getJSONObject("value"));
                    broadcastServerData();
                }
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


    // Funcions per gestionar la lògica del joc (cridades des de onMessage)
    // Actualitzar posició ratolí
    private void updateClientMouse(String playerName, JSONObject mouseData) {
        ClientData clientData = clients.getClientData(playerName);

        if (clientData != null) {
            clientData.mouseX = mouseData.optInt("mouseX", -1);
            clientData.mouseY = mouseData.optInt("mouseY", -1);
        }
    }

    // Actualitzar posició fitxa
    private void updatePiecePosition(String playerName, JSONObject pieceData) {

        String pieceId = pieceData.optString("id", "");
        
        if (!pieceId.isEmpty()){
            draggingPieces.put(playerName, pieceData);
        }

    }

    // Mètode per processar la jugada d'un jugador
    private void processPlayerMove(String playerName, JSONObject playData) {
        int column = playData.optInt("column",-1);

        if (column < 0 || column >= 7) {
            return; // columna invàlida
        }

        // Trobar la primera fila buida a la columna
        int row = findAvailableRow(column);
        if (row == -1) {
            return; // columna plena
        }

        // Guardem si la fitxa és vermella o groga
        String piece = playerName.equals(playerRed) ? "R" : "Y";

        // Actualitzar taulell
        gameBoard[row][column] = piece;
        lastMoveCol = column;
        lastMoveRow = row;
        lastMovePlayer = playerName;

        // Netejar fitxar arrastrada
        draggingPieces.remove(playerName);

        // Comprovar si hi ha guanyador o empat
        if (checkWin(row, column, piece)) {
            gameStatus = GameStatus.WIN;
            winner = playerName;
        } else if (isBoardFull()) {
            gameStatus = GameStatus.DRAW;
        } else {
            // Si no ha guanyat ningú i no està el taulell ple, canviar torn
            currentTurnPlayer = currentTurnPlayer.equals(playerRed) ? playerYellow : playerRed;
        }
    }

    // Trobar la primera fila buida a la columna
    private int findAvailableRow(int column) {
        for (int row = gameBoard.length - 1; row >= 0; row--) {
            if (gameBoard[row][column] == null) {
                return row;
            }
        }
        return -1; // columna plena
    }

    // Verificar si algú ha guanyat
    private boolean checkWin(int row, int col, String piece) {
        // Comprovar en totes les direccions i sentits (partnt de la fitxa acabada de posar, mirem cap a totes bandes)
        // Sumem la fitxa actual (1) més les fitxes connectades en ambdós sentits en cada direcció
        return (1 + countConnectedPieces(row, col, 1, 0, piece) + countConnectedPieces(row, col, -1, 0, piece) > 3) || // horitzontal
               (1 + countConnectedPieces(row, col, 0, 1, piece) + countConnectedPieces(row, col, 0, -1, piece) > 3) || // vertical
               (1 + countConnectedPieces(row, col, 1, 1, piece) + countConnectedPieces(row, col, -1, -1, piece) > 3) || // diagonal \
               (1 + countConnectedPieces(row, col, 1, -1, piece) + countConnectedPieces(row, col, -1, 1, piece) > 3);   // diagonal /
    }
    // Comptar fitxes connectades en una direcció i sentit
    // row i col: posicó de la fitxa acabada de posar
    // sRow i sCol: sentit a comprovar (canvis en fila i columna), exemple: (1,0) per horitzontal dreta, (0,1) per vertical cap avall, (1,1) per diagonal \
    // piece: tipus de fitxa a comprovar ("R" o "Y")
    private int countConnectedPieces(int row, int col, int sRow, int sCol, String piece) {
        int count = 0;
        int r = row + sRow;
        int c = col + sCol;
        // Comptar fitxes del mateix tipus en la direcció donada fins que o trobem una diferent o sortim del taulell
        while (r >= 0 && r < gameBoard.length && c >= 0 && c < gameBoard[0].length && piece.equals(gameBoard[r][c])) {
            count++;
            r += sRow;
            c += sCol;
        }
        return count;
    }

    // Comprovar si el taulell està ple
    private boolean isBoardFull() {
        for (int col = 0; col < gameBoard[0].length; col++) {
            if (gameBoard[0][col] == null) {
                return false; // Hi ha almenys una columna amb espai
            }
        }
        return true; // Totes les columnes estan plenes
    }

    // Enviar dades del servidor als clients
    private void broadcastServerData() {
        for (Map.Entry<WebSocket, String> entry : clients.snapshot().entrySet()) {
            WebSocket conn = entry.getKey();
            String clientName = entry.getValue();

            JSONObject serverData = createServerDataForClient(clientName);
            sendSafe(conn, serverData.toString());
        }
    }

    // Crear l'objecte JSON amb les dades del servidor
    private JSONObject createServerDataForClient(String clientName) {
        JSONObject serverData = new JSONObject();
        serverData.put(K_TYPE, T_SERVER_DATA);
        serverData.put("clientName", clientName);

        // Llista de clients
        JSONArray clientsList = new JSONArray();
        for (String userName : clients.getAllUserNames()) {
            ClientData clientData = clients.getClientData(userName);
            JSONObject clientObj = new JSONObject();
            clientObj.put("name", clientData.name);
            clientObj.put("color", clientData.name.equals(playerRed) ? "red" : "yellow"); // clientObj.put("color", client.name.equals(playerRed) ? "red" : client.name.equals(playerYellow) ? "yellow" : "null"); si al final tenim espectadors els hi podem posar null com a color
            clientObj.put("mouseX", clientData.mouseX);
            clientObj.put("mouseY", clientData.mouseY);
            clientObj.put("role", clientData.name.equals(playerRed) ? "R" : "Y"); // clientObj.put("role", client.name.equals(playerRed) ? "R" : client.name.equals(playerYellow) ? "Y" : "S"); si al final tenim espectadors els hi podem posar S com a rol (de spectator)
            clientsList.put(clientObj);
        }
        serverData.put("clientsList", clientsList);

        // Llista d'objectes (fitxes)
        JSONArray objectsList = new JSONArray();
        for (Map.Entry<String, JSONObject> entry : draggingPieces.entrySet()) {
            JSONObject draggingPiece = new JSONObject();
            draggingPiece.put("id", entry.getKey() + "_dragging");
            draggingPiece.put("x", entry.getValue().optInt("x", 0));
            draggingPiece.put("y", entry.getValue().optInt("y", 0));
            draggingPiece.put("role", entry.getKey().equals(playerRed) ? "R" : "Y");
            objectsList.put(draggingPiece);
        }
        serverData.put("objectsList", objectsList);

        // Estat del joc
        JSONObject gameState = new JSONObject();
        gameState.put("status", gameStatus.name().toString().toLowerCase());
        gameState.put("turn", currentTurnPlayer != null ? currentTurnPlayer: "");

        // Taulell
        JSONArray boardArray = new JSONArray();
        for (int row = 0; row < gameBoard.length ; row++) {
            JSONArray rowArray = new JSONArray();
            for (int col = 0; col < gameBoard[0].length; col++) {
                rowArray.put(gameBoard[row][col] != null ? gameBoard[row][col] : " ");
            }
            boardArray.put(rowArray);
        }
        gameState.put("board", boardArray);

        // Última jugada
        if (lastMoveCol != -1) {
            JSONObject lastMove = new JSONObject();
            lastMove.put("col",lastMoveCol);
            lastMove.put("row",lastMoveRow);
            gameState.put("lastMove", lastMove);
        }

        gameState.put("winner", winner);
        
        serverData.put("game", gameState);

        return serverData;
    } 

    private void startGame(String player1, String player2) {
        playerRed = player1;
        playerYellow = player2;
        currentTurnPlayer = playerRed;
        gameStatus = GameStatus.COUNTDOWN;

        // Inicialitzar taulell
        initializeBoard();

        // Actualitzar colors jugadors
        ClientData redPlayer = clients.getClientData(playerRed);
        if (redPlayer != null) redPlayer.color = "RED";

        ClientData yellowPlayer = clients.getClientData(playerYellow);
        if (yellowPlayer != null) yellowPlayer.color = "YELLOW";

        startCountdown();
    }

    private void startCountdown() {
        new Thread(() -> {
            try {
                for (int i = 5; i >= 0; i--) {
                    JSONObject countdownMsg = new JSONObject();
                    countdownMsg.put("type", "countdown");
                    countdownMsg.put("value", i);
                    broadcastExcept(null, countdownMsg.toString());
                    
                    if (i == 0) {
                        gameStatus = GameStatus.PLAYING;
                        broadcastServerData();
                    }
                    
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
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
        Main server = new Main(new InetSocketAddress("0.0.0.0",DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);

        System.out.println("Servidor WebSocket en execució al port " + DEFAULT_PORT + ". Prem Ctrl+C per aturar-lo.");
        awaitForever();
    }
}
