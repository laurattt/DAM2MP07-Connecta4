package com.clientFX;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class CtrlConfig implements Initializable {

    @FXML
    public TextField txtProtocol;

    @FXML
    public TextField txtHost;

    @FXML
    public TextField txtPort;

    @FXML
    public TextField txtName; 

    @FXML
    public Label txtMessage;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        txtName.setText(""); // campo vacio para que el usuario escriba su nombre
    }

    @FXML
    private void connectToServer() {
        // verificar que el usuario haya ingresado un nombre
        if (txtName.getText() == null || txtName.getText().trim().isEmpty()) {
            txtMessage.setTextFill(javafx.scene.paint.Color.RED);
            txtMessage.setText("Por favor, ingresa tu nombre");
            return;
        }
        Main.connectToServer();
    }

    @FXML
    private void setConfigLocal() {
        txtProtocol.setText("ws");
        txtHost.setText("localhost");
        txtPort.setText("3000");
        if (txtName.getText() == null || txtName.getText().trim().isEmpty()) {
            txtName.setPromptText("Ingresa tu nombre aquí");
        }
    }

    @FXML
    private void setConfigProxmox() {
        txtProtocol.setText("wss");
        txtHost.setText("user.ieti.site");
        txtPort.setText("443");
        if (txtName.getText() == null || txtName.getText().trim().isEmpty()) {
            txtName.setPromptText("Ingresa tu nombre aquí");
        }
    }
}