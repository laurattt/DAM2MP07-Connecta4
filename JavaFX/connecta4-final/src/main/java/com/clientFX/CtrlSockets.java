package com.clientFX;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import java.util.Optional;

public class CtrlSockets implements Initializable {

    @FXML
    private Label txtId;

    @FXML
    private TextField txtField;

    @FXML
    private TextArea txtArea;

    @FXML
    private ChoiceBox<String> choiceType, choiceUser;

    private List<String> connectedUsers = new ArrayList<>();
    private String currentUserName;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Start choiceBox
        choiceType.getItems().clear();
        choiceType.getItems().addAll("broadcast", "bounce", "private", "invite to play"); // Añadimos la invitación a la partida (funcionará como un private)
        choiceType.setValue(choiceType.getItems().get(0));
        choiceType.setOnAction((event) -> {
            if (choiceType.getValue().equals("private") || choiceType.getValue().equals("invite to play")) {
                choiceUser.setDisable(false);
                updateUserChoiceBox();
            } else {
                choiceUser.setDisable(true);
            }
        });
        choiceUser.setDisable(true);

        // Send message when pressing enter
        txtField.setOnAction((event) -> {
            sendMessage();
        });
    }

    @FXML
    private void sendMessage() {
        String txt = txtField.getText();
        if (txt == null || txt.trim().isEmpty()) {
            return;
        }

        JSONObject obj = new JSONObject("{}");
        String type = choiceType.getValue().toLowerCase();
        obj.put("type", type);
        obj.put("message", txt);

        if (type.equals("private") || type.equals("invite to play")) { // El invite funciona como un private
            String destination = choiceUser.getValue();
            if (destination == null || destination.isEmpty()) {
                txtArea.appendText("\nError: No destination selected for "+ type +" message");
                return;
            }
            obj.put("destination", destination);
        }

        Main.wsClient.safeSend(obj.toString());
        System.out.println("Send WebSocket: " + obj.toString());
        
        // Limpiar el campo de texto después de enviar
        txtField.clear();
    }

    // Actualizar la ChoiceBox de usuarios
    private void updateUserChoiceBox() {
        choiceUser.getItems().clear();
        for (String userName : connectedUsers) {
            if (!userName.equals(currentUserName)) {
                choiceUser.getItems().add(userName);
            }
        }
        if (!choiceUser.getItems().isEmpty()) {
            choiceUser.setValue(choiceUser.getItems().get(0));
        }
    }

    // Main wsClient calls this method when receiving a message
    public void receiveMessage(JSONObject messageObj) {
        System.out.println("Receive WebSocket: " + messageObj.toString());
        String type = messageObj.getString("type");

        if (type.equals("clients")) {
            JSONArray JSONlist = messageObj.getJSONArray("list");
            currentUserName = messageObj.getString("id"); // Esto ahora es solo el nombre
            
            // Actualizar lista de usuarios conectados
            connectedUsers.clear();
            for (int i = 0; i < JSONlist.length(); i++) {
                connectedUsers.add(JSONlist.getString(i));
            }
            
            // Mostrar  el nombre
            txtId.setText(currentUserName);
            updateUserChoiceBox();
            
        } else if (type.equals("bounce")) {
            String message = messageObj.getString("message");
            String origin = messageObj.getString("origin");
            txtArea.appendText("\n[" + getCurrentTime() + "] " + origin + " (bounce): " + message);

        } else if (type.equals("broadcast")) {
            String message = messageObj.getString("message");
            String origin = messageObj.getString("origin");
            txtArea.appendText("\n[" + getCurrentTime() + "] " + origin + " (broadcast): " + message);

        } else if (type.equals("private")) {
            String message = messageObj.getString("message");
            String origin = messageObj.getString("origin");
            String destination = messageObj.getString("destination");
            
            if (origin.equals(currentUserName)) {
                // Soy el remitente
                txtArea.appendText("\n[" + getCurrentTime() + "] Tú → " + destination + " (private): " + message);
            } else {
                // Soy el destinatario
                txtArea.appendText("\n[" + getCurrentTime() + "] " + origin + " → Tú (private): " + message);
            }
            
        } else if (type.equals("invite to play")) {
            String message = messageObj.getString("message");
            String origin = messageObj.getString("origin");
            String destination = messageObj.getString("destination");
            
            if (origin.equals(currentUserName)) {
                // Soy el remitente
                txtArea.appendText("\n[" + getCurrentTime() + "] Tú → " + destination + " (invite to play): " + message);
            } else {
                // Soy el destinatario
                txtArea.appendText("\n[" + getCurrentTime() + "] " + origin + " → Tú (invite to play): " + message);
                
                // Aparece una ventana emergente para aceptar o rechazar la invitación
                Platform.runLater(() -> {
                    Boolean accepted = false;

                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Invitación a jugar");
                    alert.setHeaderText("Has recibido una invitación para jugar de " + origin);
                    alert.setContentText("Mensaje: " + message + "\n¿Aceptas la invitación?");

                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        // El usuario aceptó la invitación
                        txtArea.appendText("\nHas aceptado la invitación de " + origin + " para jugar.");
                        accepted = true;
                    } else {
                        // El usuario rechazó la invitación
                        txtArea.appendText("\nHas rechazado la invitación de " + origin + " para jugar.");
                    }
                    
                    // Enviar respuesta al servidor
                    JSONObject responseObj = new JSONObject("{}");
                    responseObj.put("type", "invite response");
                    responseObj.put("origin", currentUserName); // Soy yo quien responde
                    responseObj.put("destination", origin); // Respuesta al remitente original
                    responseObj.put("accepted", accepted);
                    
                    Main.wsClient.safeSend(responseObj.toString());

                    if (accepted) {
                        // Cambiamos a la vista del juego
                        Platform.runLater(() -> {
                            UtilsViews.setViewAnimating("ViewGame");
                            Stage stage = UtilsViews.getStage();
                            stage.setWidth(1400);
                            stage.setHeight(900);
                            stage.centerOnScreen();
                        });
                    }
                });
            }
            
        } else if (type.equals("invite response")) {
            String origin = messageObj.getString("origin"); // Quien responde
            String destination = messageObj.getString("destination"); // El remitente original de la invitación
            boolean accepted = messageObj.getBoolean("accepted");
            
            if (accepted) {
                txtArea.appendText("\n--- " + origin + " ha aceptado tu invitación para jugar ---");
                
                // Cambiamos a la vista del juego
                Platform.runLater(() -> {
                    UtilsViews.setViewAnimating("ViewGame");
                    Stage stage = UtilsViews.getStage();
                    stage.setWidth(1400);
                    stage.setHeight(900);
                    stage.centerOnScreen();
                });
            } else {
                txtArea.appendText("\n--- " + origin + " ha rechazado tu invitación para jugar ---");
            }
            
        } else if (type.equals("userJoined")) {
            String userName = messageObj.getString("userName");
            txtArea.appendText("\n--- " + userName + " se unió al chat ---");
            
        } else if (type.equals("userLeft")) {
            String userName = messageObj.getString("userName");
            txtArea.appendText("\n--- " + userName + " dejó el chat ---");
            
        } else if (type.equals("confirmation")) {
            String confirmationMsg = messageObj.getString("message");
            txtArea.appendText("\n[Sistema] " + confirmationMsg);
            
        } else if (type.equals("error")) {
            String errorMsg = messageObj.getString("message");
            txtArea.appendText("\n[Error] " + errorMsg);
        }
    }

    // Método auxiliar para obtener la hora actual
    private String getCurrentTime() {
        return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    // Vista para tablero
    @FXML
    public void vistaTablero (ActionEvent event){
        UtilsViews.setViewAnimating("ViewGame");

        Stage stage = UtilsViews.getStage();
        stage.setWidth(1400);
        stage.setHeight(900); //despues se ajusta para ver mejor el tablero zzz

        stage.centerOnScreen();
    }

}