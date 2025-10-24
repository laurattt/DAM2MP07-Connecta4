package com.clientFX;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    public static UtilsWS wsClient;

    public static int port = 3000;
    public static String protocol = "http";
    public static String host = "localhost";
    public static String protocolWS = "ws";

    public static CtrlConfig ctrlConfig;
    public static CtrlSockets ctrlSockets;
    public static CtrlGame ctrlGame;


    public static void main(String[] args) {

        // Iniciar app JavaFX   
        launch(args);
    }
    
    @Override
    public void start(Stage stage) throws Exception {

        final int windowWidth = 400;
        final int windowHeight = 300;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml"); 
        UtilsViews.addView(getClass(), "ViewSockets", "/assets/viewSockets.fxml");
        UtilsViews.addView(getClass(), "ViewGame", "/assets/viewGame.fxml");
        // Añadir la vista de la cuenta atrás

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlSockets = (CtrlSockets) UtilsViews.getController("ViewSockets");
        ctrlGame = (CtrlGame) UtilsViews.getController("ViewGame");

        Scene scene = new Scene(UtilsViews.parentContainer);
        
        UtilsViews.setStage(stage);
        stage.setScene(scene);
        stage.onCloseRequestProperty(); // Call close method when closing window
        stage.setTitle("Connecta4-MonicaD-LauraT");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        // Add icon only if not Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            Image icon = new Image("file:/icons/icon.png");
            stage.getIcons().add(icon);
        }
    
    }


    @Override
    public void stop() { 
        if (wsClient != null) {
            wsClient.forceExit();
        }
        System.exit(1); // Kill all executor services
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    public static <T> List<T> jsonArrayToList(JSONArray array, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            T value = clazz.cast(array.get(i));
            list.add(value);
        }
        return list;
    }

    public static void connectToServer() {
        // Validar que el nombre no esté vacío
        String userName = ctrlConfig.txtName.getText();
        if (userName == null || userName.trim().isEmpty()) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText("Ingresa un nombre válido");
            return;
        }
        
        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connecting ...");

        pauseDuring(1500, () -> {
            String protocol = ctrlConfig.txtProtocol.getText();
            String host = ctrlConfig.txtHost.getText();
            String port = ctrlConfig.txtPort.getText();
            wsClient = UtilsWS.getSharedInstance(protocol + "://" + host + ":" + port);

            wsClient.onMessage((response) -> { 
                Platform.runLater(() -> { 
                    wsMessage(response); 
                }); 
            });
            
            wsClient.onError((response) -> { 
                Platform.runLater(() -> { 
                    wsError(response); 
                }); 
            });
            
            // Enviar información del usuario después de conectar
            pauseDuring(2000, () -> {
                if (wsClient != null && wsClient.isOpen()) {
                    JSONObject userInfo = new JSONObject();
                    userInfo.put("type", "userInfo");
                    userInfo.put("userName", userName.trim()); // Usar el nombre ingresado
                    wsClient.safeSend(userInfo.toString());
                    System.out.println("Enviando nombre de usuario: " + userName.trim());
                } else {
                    ctrlConfig.txtMessage.setTextFill(Color.RED);
                    ctrlConfig.txtMessage.setText("Error de conexión");
                }
            });
        });
    }
        
    private static void wsMessage(String response) {
        
        Platform.runLater(()->{ 
            JSONObject msgObj = new JSONObject(response);
            String type = msgObj.optString("type","");

            switch (type) {
                case "serverData" -> {
                    ctrlGame.receiveServerData(msgObj);




// --------------------------------------------------------------------------------------------------------
                    // REVISAR

                    // Por ahora cuando acaba el juego cambiamos otra vez al chat, CAMBIAR A VISTA GANADORES

                    JSONObject game = msgObj.optJSONObject("game");
                    if (game != null) {
                        String status = game.optString("status", "");
                        if ("win".equals(status) || "draw".equals(status)) {
                            // Después de un tiempo, volver al chat
                            pauseDuring(5000, () -> {
                                if (!"ViewSockets".equals(UtilsViews.getActiveView())) {
                                    UtilsViews.setViewAnimating("ViewSockets");
                                }
                            });
                        }
                    }





// --------------------------------------------------------------------------------------------------------

                    break;
                }
                case "countdown" -> {
                    int countdownValue = msgObj.optInt("value",0);
                    handleCountdown(countdownValue);
                    break;
                }
                default -> {



// --------------------------------------------------------------------------------------------------------
                    // REVISAR


                    // Només estarem a la vista del xat per rebre missatges si no estem a ViewGame
                    // Afegir que no canviï si està a les vistes d'espera o countdown
                    String activeView = UtilsViews.getActiveView();
                    if (!"ViewGame".equals(activeView) && !"ViewSockets".equals(activeView)) {
                        UtilsViews.setViewAnimating("ViewSockets");
                    }
                    ctrlSockets.receiveMessage(msgObj);
                    break;



// --------------------------------------------------------------------------------------------------------



                }


            }
            
        });
    }

    // Funció que imprimeix els números del countdown i canvia la vista a la del joc quan d'arriba a 0
    private static void handleCountdown(int value) {
        if (value == 0) {
            // Cambiar a vista de juego cuando termina la cuenta atrás
            UtilsViews.setViewAnimating("ViewGame");
        }
        // Mostrem el countdown per consola -> CANVIAR: vista countdown
        System.out.println("Countdown: " + value);
    }

    private static void wsError(String response) {

        String connectionRefused = "Connection refused";
        if (response.indexOf(connectionRefused) != -1) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText(connectionRefused);
            pauseDuring(1500, () -> {
                ctrlConfig.txtMessage.setText("");
            });
        }
    }
}
