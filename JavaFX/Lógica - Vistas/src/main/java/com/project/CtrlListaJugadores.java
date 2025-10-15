package com.project;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import org.json.JSONArray;
import org.json.JSONObject;
import com.shared.ClientData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public class CtrlListaJugadores {

    @FXML
    private ChoiceBox<String> seleccionContrincante;

    @FXML
    private Label txtMessage;

    @FXML
    private Button playButton;

    private static final String FILE_PATH = "dataJSON/jugadores.json";
    public static String jugadorSeleccionado;

    @FXML
    public void initialize() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            crearArchivoInicial();
        }

        cargarJugadores();

        txtMessage.setText("Selecciona un contrincante para comenzar");
    }


     @FXML
     public void viewChange(ActionEvent event) {
        String elegido = seleccionContrincante.getValue();

        if (elegido == null) {
            txtMessage.setText("Por favor selecciona un contrincante antes de continuar"); // esto no va, revisa zzz
        } else {
            txtMessage.setText("Has seleccionado: " + elegido);
        }

        jugadorSeleccionado = seleccionContrincante.getValue();
        UtilsViews.setViewAnimating("ViewTablero");
    }


    private void crearArchivoInicial() {
        JSONArray jugadores = new JSONArray();

        jugadores.put(new ClientData("Miguel", "rojo").toJSON());
        jugadores.put(new ClientData("Nick", "azul").toJSON());
        jugadores.put(new ClientData("Mateo", "verde").toJSON());
        jugadores.put(new ClientData("Andrés", "negro").toJSON());

        try (FileWriter fw = new FileWriter(FILE_PATH)) {
            fw.write(jugadores.toString(2)); 
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void cargarJugadores() {
        try {
            String contenido = Files.readString(new File(FILE_PATH).toPath());
            JSONArray arr = new JSONArray(contenido);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                ClientData jugador = ClientData.fromJSON(obj);
                seleccionContrincante.getItems().add(jugador.name);
            }

        } catch (Exception e) {
            txtMessage.setText("Error cargando jugadores: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
