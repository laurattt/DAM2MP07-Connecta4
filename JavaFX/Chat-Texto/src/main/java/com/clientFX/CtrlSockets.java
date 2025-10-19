package com.clientFX;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

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
        choiceType.getItems().addAll("broadcast", "bounce", "private");
        choiceType.setValue(choiceType.getItems().get(0));
        choiceType.setOnAction((event) -> {
            if (choiceType.getValue().equals("private")) {
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

        if (type.equals("private")) {
            String destination = choiceUser.getValue();
            if (destination == null || destination.isEmpty()) {
                txtArea.appendText("\nError: No destination selected for private message");
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
}