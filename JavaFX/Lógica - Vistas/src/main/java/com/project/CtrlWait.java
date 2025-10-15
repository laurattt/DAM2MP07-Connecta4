package com.project;

import java.net.URL;
import java.util.ResourceBundle;

//import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;

public class CtrlWait implements Initializable {

    @FXML
    public Label txtTitle;

    @FXML
    public Label txtPlayer0;

    @FXML
    public Label txtPlayer1;

    //@FXML
    //public void startCountdown(ActionEvent event){
        //UtilsViews.setViewAnimating("ViewTablero");
    //}

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        txtPlayer0.setText(CtrlConfig.namePlayer);
        txtPlayer1.setText(CtrlListaJugadores.jugadorSeleccionado);
    }


}

// arreglar esto >:( 