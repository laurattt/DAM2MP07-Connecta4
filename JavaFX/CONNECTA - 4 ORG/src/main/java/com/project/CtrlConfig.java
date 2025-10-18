package com.project;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
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
    public Label txtMessage;

    @FXML
    private TextField txtNamePlayer;
    public static String namePlayer;


    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    /* 
       @FXML
    private void connectToServer() {
        Main.connectToServer();
    } 
     
    */
  

    @FXML
    private void setConfigLocal() {
        txtProtocol.setText("ws");
        txtHost.setText("localhost");
        txtPort.setText("3000");
    }

    @FXML
    private void setConfigProxmox() {
        txtProtocol.setText("wss");
        txtHost.setText("user.ieti.site");
        txtPort.setText("443");
    }

   @FXML
    public void viewSelect(ActionEvent event) {
        namePlayer = txtNamePlayer.getText().trim();

        if (namePlayer.isEmpty()) {
            txtMessage.setText("Ingresa un nombre antes de continuar.");
            return;
        }

        UtilsViews.setViewAnimating("ViewListPlayers");
    }
}