package com.clientFX;

import java.net.URL;
import java.util.ResourceBundle;

import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;

public class CtrlGameView implements Initializable {

    @FXML
    public Label turnoDe;

    @FXML
    public Pane zonaTablero;

    @FXML
    public Pane bandejaFichas;

    private int currentTurn = 1;
    private String myRole = "";
    private int turnoActual = 1;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        turnoDe.setText("Esperando el comienzo del juego...");
    }

    /**
     * Método para recibir mensajes del servidor
     */

    public void receiveMessage(JSONObject msg) {
        String type = msg.optString("type");

        switch (type) {
            case "role" -> {
                myRole = msg.optString("value", "");
                System.out.println("Mi rol es: " + myRole);
            }

            case "turn" -> {
                turnoActual = msg.optInt("data", 1);
                boolean meToca = (myRole.equals("jugador1") && turnoActual == 1) ||
                                (myRole.equals("jugador2") && turnoActual == 2);

                turnoDe.setText(turnoActual == 1 ? "Turno Rojo" : "Turno Azul");

                // Activar o desactivar movimiento del ratón
                if (meToca) {
                    activarMovimientoRaton();
                } else {
                    desactivarMovimientoRaton();
                }
            }

            case "mousemove" -> {
                // Otro jugador ha movido el ratón, mostrarlo
                double x = msg.optDouble("x");
                double y = msg.optDouble("y");

                // Aquí actualizas la interfaz para mostrar ese movimiento (por ejemplo mover un nodo visual)
                System.out.println("Movimiento recibido: x=" + x + " y=" + y);
                // TODO: actualiza visualmente si quieres
            }
        }
    }

    private void activarMovimientoRaton() {
        zonaTablero.setOnMouseMoved(event -> {
            double x = event.getX();
            double y = event.getY();

            JSONObject msg = new JSONObject();
            msg.put("type", "mousemove");
            msg.put("x", x);
            msg.put("y", y);

            Main.wsClient.safeSend(msg.toString());
        });
    }

    private void desactivarMovimientoRaton() {
        zonaTablero.setOnMouseMoved(null);
    }



}