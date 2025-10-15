package com.project;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;



public class Main extends Application {

    final int WINDOW_WIDTH = 800;
    final int WINDOW_HEIGHT = 600;

    public static CtrlConfig ctrlConfig;
    public static CtrlListaJugadores ctrlListPlayers;
    public static CtrlWait ctlWait;
   

    @Override
    public void start(Stage stage) throws Exception {

        // Vistas

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml"); 
        UtilsViews.addView(getClass(), "ViewListPlayers", "/assets/viewListPlayers.fxml"); 
        UtilsViews.addView(getClass(), "ViewWait", "/assets/viewWait.fxml");
        // CUENTA REGRESIVA 
        UtilsViews.addView(getClass(), "ViewTablero", "/assets/tableroPartida.fxml"); 
        
        // Controladores
        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlListPlayers= (CtrlListaJugadores) UtilsViews.getController("ViewListPlayers");
        ctlWait = (CtrlWait) UtilsViews.getController("ViewWait");


        Scene scene = new Scene(UtilsViews.parentContainer);

        stage.setScene(scene);
        stage.setTitle("CONNECTA-4");
        stage.setWidth(WINDOW_WIDTH);
        stage.setHeight(WINDOW_HEIGHT);
        stage.show();

        // Afegeix una icona només si no és un Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            Image icon = new Image("file:icons/icon.png");
            stage.getIcons().add(icon);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
