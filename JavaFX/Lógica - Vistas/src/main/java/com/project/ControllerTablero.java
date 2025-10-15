package com.project;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.event.ActionEvent;

import java.net.URL;
import java.util.ResourceBundle;

public class ControllerTablero implements Initializable {

    // --- FXML
    @FXML private Canvas canvas;
    @FXML private Button buttonAdd;   // opcional (ara deixa caure una fitxa en una columna aleatòria)
    @FXML private Button buttonClear; // reset

    // --- Dibuix i mida
    private GraphicsContext gc;

    // Config de la graella Connecta 4
    private static final int COLS = 7;
    private static final int ROWS = 6;

    // Mides calculades a partir del canvas
    private double margin = 20;       // marge exterior
    private double boardX, boardY;    // origen del tauler
    private double cell;              // mida de cel·la (quadrada)
    private double boardW, boardH;    // mida tauler

    // Zona clicable de capçalera (sobre el tauler)
    private double headerH;           // alçada de la franja superior clicable

    // Estat del tauler: 0 = buit, 1 = vermell (per ara només un color)
    private final int[][] board = new int[ROWS][COLS];

    // Animació de caiguda
    private boolean animating = false;
    private int animCol = -1, animRow = -1; // destí
    private double animY;                    // posició Y actual del centre de la fitxa que cau
    private double targetY;                  // posició Y final del centre de la fitxa
    private double fallSpeed = 1200;         // velocitat de caiguda (px/s). Ajusta al teu gust.

    // Timer (fa servir la teva classe)
    private AnimacionFichas timer;
    private long lastRunNanos = 0; // per calcular dt

    // --- Lógica del juego
    private int jugadorActual = 1; // 1 = rojo, 2 = azul
    private boolean partidaTerminada = false;


    @Override
    public void initialize(URL url, ResourceBundle rb) {
        gc = canvas.getGraphicsContext2D();

        // Timer abans del primer redraw
        timer = new AnimacionFichas(
                fps -> update(),
                this::redraw,
                60
        );
        timer.start();

        // Enllaça mides del Canvas al BorderPane (opció B)
        bindCanvasSizeToParent();

        // Si vols un primer dibuix immediat (encara que les mides siguin petites):
        computeLayout();
        redraw();

        // Clics de columna
        canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleClickOnCanvas);
    }


    // Recalcula mides en funció del canvas
    private void computeLayout() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // Deixem una capçalera clicable a dalt
        headerH = 0.9 * Math.min(w, h) / 12.0; // una franja raonable; la pots ajustar

        // L’àrea disponible per al tauler
        double availableW = w - 2 * margin;
        double availableH = h - 2 * margin - headerH;

        // Cel·la quadrada que càpiga 7x6
        cell = Math.min(availableW / COLS, availableH / ROWS);

        boardW = cell * COLS;
        boardH = cell * ROWS;

        // Centrem horitzontalment; verticalment deixem header a dalt
        boardX = (w - boardW) / 2.0;
        boardY = margin + headerH;
    }

    // --- Controls de la UI
    @FXML
    private void actionAdd(ActionEvent event) {
        // Opcional: deixa caure una fitxa en una columna aleatòria (si no hi ha animació en curs)
        if (animating) return;
        int col = (int) Math.floor(Math.random() * COLS);
        tryDropInColumn(col);
    }

    @FXML
    private void actionClear(ActionEvent event) {
        resetBoard();
        redraw();
    }

    private void resetBoard() {
        for (int fila = 0; fila < ROWS; fila++) {
            for (int columna = 0; columna < COLS; columna++) {
                board[fila][columna] = 0;
            }
        }
        animating = false;
        animCol = animRow = -1;
        partidaTerminada = false;
        jugadorActual = 1;
    }


    // --- Interacció amb el canvas
    private void handleClickOnCanvas(MouseEvent e) {
        if (animating) return;

        double x = e.getX();
        double y = e.getY();

        // Només acceptem click a la franja superior (header) i dins de l’ample del tauler
        if (y < boardY && x >= boardX && x < boardX + boardW) {
            int col = (int) ((x - boardX) / cell);
            tryDropInColumn(col);
        }
    }

    private void tryDropInColumn(int columna) { // CAMBIOS EN ESTE MÉTODO ------------------------------------------
        if (partidaTerminada || animating) return;

        int filaDisponible = findLowestEmptyRow(columna);
        if (filaDisponible < 0) return; // columna llena

        // Configura animación de caída
        double celdaCentroYSuperior = boardY + cell * 0.5;
        double posicionInicialY = boardY - cell * 0.5;
        double posicionFinalY = celdaCentroYSuperior + filaDisponible * cell;

        animCol = columna;
        animRow = filaDisponible;
        animY = posicionInicialY;
        targetY = posicionFinalY;
        animating = true;
    lastRunNanos = 0;
}


    private int findLowestEmptyRow(int col) {
        for (int r = ROWS - 1; r >= 0; r--) {
            if (board[r][col] == 0) return r;
        }
        return -1;
    }

    // --- Bucle lògic (cridat per CnvTimer.runFunction)
    private void update() {
        // Calculem dt (segons) dins del controlador
        long now = System.nanoTime();
        double dt;
        if (lastRunNanos == 0) {
            dt = 0; // primer frame
        } else {
            dt = (now - lastRunNanos) / 1_000_000_000.0;
        }
        lastRunNanos = now;

        if (animating && dt > 0) {
            animY += fallSpeed * dt;
            if (animY >= targetY) {
                animY = targetY;
                animating = false;
                // Col·loca la fitxa al tauler
                if (animRow >= 0 && animCol >= 0) { // AQUI SE CAMBIARON COSAS ------------------------------
                    board[animRow][animCol] = jugadorActual;

                    // Comprobar si hay ganador
                    if (comprobarVictoria(animRow, animCol, jugadorActual)) {
                        partidaTerminada = true;
                        System.out.println("¡El jugador " + (jugadorActual == 1 ? "ROJO" : "AZUL") + " ha ganado!");
                    } else if (tableroLleno()) {
                        partidaTerminada = true;
                        System.out.println("¡Empate!");
                    } else {
                        // Cambiar turno
                        jugadorActual = (jugadorActual == 1) ? 2 : 1;
                    }
                }

            }
        }
    }

    // Comprueba si el jugador ha hecho cuatro en raya
    private boolean comprobarVictoria(int fila, int columna, int jugador) {
        return (contarEnLinea(fila, columna, 1, 0, jugador) >= 4 ||   // Horizontal
                contarEnLinea(fila, columna, 0, 1, jugador) >= 4 ||   // Vertical
                contarEnLinea(fila, columna, 1, 1, jugador) >= 4 ||   // Diagonal principal
                contarEnLinea(fila, columna, 1, -1, jugador) >= 4);   // Diagonal inversa
    }

    // Cuenta fichas consecutivas en una dirección
    private int contarEnLinea(int fila, int columna, int deltaFila, int deltaColumna, int jugador) {
        int contador = 1;

        // Dirección positiva
        int f = fila + deltaFila;
        int c = columna + deltaColumna;
        while (f >= 0 && f < ROWS && c >= 0 && c < COLS && board[f][c] == jugador) {
            contador++;
            f += deltaFila;
            c += deltaColumna;
        }

        // Dirección negativa
        f = fila - deltaFila;
        c = columna - deltaColumna;
        while (f >= 0 && f < ROWS && c >= 0 && c < COLS && board[f][c] == jugador) {
            contador++;
            f -= deltaFila;
            c -= deltaColumna;
        }

        return contador;
    }

    // Comprueba si el tablero está lleno
    private boolean tableroLleno() {
        for (int c = 0; c < COLS; c++) {
            if (board[0][c] == 0) return false;
        }
        return true;
    }


    // --- Dibuix (cridat per CnvTimer.drawFunction)
    private void redraw() {
        // Fons
        gc.setFill(Color.rgb(245, 245, 245));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Franja superior clicable (amb marques de columna)
        drawHeader();

        // Tauler (pla blau amb "forats" suggerits)
        drawBoardBackground();

        // Fitxes existents
        drawDiscsFromBoard();

        // Fitxa en caiguda (si n’hi ha)
        if (animating) drawFallingDisc();

        // Mostrar FPS del teu CnvTimer
        if (timer != null) {
        //    timer.draw(gc);
        }
    }

    private void drawHeader() {
        // Fons header
        gc.setFill(Color.rgb(230, 230, 230));
        gc.fillRect(0, margin, canvas.getWidth(), headerH);

        // Delimita l'àrea sobre el tauler
        gc.setStroke(Color.GRAY);
        gc.strokeRect(boardX, margin, boardW, headerH);

        // Marques de columna
        gc.setStroke(Color.rgb(180, 180, 180));
        for (int c = 1; c < COLS; c++) {
            double x = boardX + c * cell;
            gc.strokeLine(x, margin, x, margin + headerH);
        }
    }

    private void drawBoardBackground() {
        // Tauler blau
        gc.setFill(Color.rgb(30, 96, 199)); // blau Connecta 4
        gc.fillRect(boardX, boardY, boardW, boardH);

        // “Forats”: dibuixem cercles clars per suggerir els sockets
        double r = cell * 0.42; // radi dels forats
        for (int rIdx = 0; rIdx < ROWS; rIdx++) {
            for (int c = 0; c < COLS; c++) {
                double cx = boardX + c * cell + cell * 0.5;
                double cy = boardY + rIdx * cell + cell * 0.5;

                gc.setFill(Color.rgb(235, 235, 235));
                gc.fillOval(cx - r, cy - r, r * 2, r * 2);
            }
        }
    }

    private void drawDiscsFromBoard() { // CAMBIOS AQUIIIIIIIIIIIIIIIIIIIII
        double radioFicha = cell * 0.42;

        for (int fila = 0; fila < ROWS; fila++) {
            for (int columna = 0; columna < COLS; columna++) {
                int valorCelda = board[fila][columna];
                if (valorCelda == 0) continue; // celda vacía, no dibujar nada

                double centroX = boardX + columna * cell + cell * 0.5;
                double centroY = boardY + fila * cell + cell * 0.5;

                // Color según el jugador
                if (valorCelda == 1) {
                    gc.setFill(Color.RED);   // Jugador 1 (rojo)
                } else if (valorCelda == 2) {
                    gc.setFill(Color.BLUE);  // Jugador 2 (azul)
                }

                gc.fillOval(centroX - radioFicha, centroY - radioFicha, radioFicha * 2, radioFicha * 2);
            }
        }
    }


    private void drawFallingDisc() { // CAMBIOS AQUIIIIIIIIIIIIIIIIIIIII
        if (animCol < 0) return; // No hay ficha en animación

        double radioFicha = cell * 0.42;
        double centroX = boardX + animCol * cell + cell * 0.5;
        double centroY = animY;

        // Color según el turno del jugador
        gc.setFill(jugadorActual == 1 ? Color.RED : Color.BLUE);

        gc.fillOval(centroX - radioFicha, centroY - radioFicha, radioFicha * 2, radioFicha * 2);
    }


    // Lliga la mida del Canvas a l'espai disponible del pare (BorderPane center).
    private void bindCanvasSizeToParent() {
        // Quan canviï el pare, fem els binds
        canvas.parentProperty().addListener((o, oldP, newP) -> {
            if (newP instanceof Region region) {
                canvas.widthProperty().unbind();
                canvas.heightProperty().unbind();
                // Reserva ~120 px per la banda dreta (botons). Ajusta si cal.
                canvas.widthProperty().bind(region.widthProperty().subtract(120));
                canvas.heightProperty().bind(region.heightProperty());
            }
        });

        // També fem un bind “tardà” quan ja hi ha layout
        Platform.runLater(() -> {
            if (canvas.getParent() instanceof Region region) {
                canvas.widthProperty().unbind();
                canvas.heightProperty().unbind();
                canvas.widthProperty().bind(region.widthProperty().subtract(120));
                canvas.heightProperty().bind(region.heightProperty());
            }
            // Primer càlcul i repintat quan ja tenim mides > 0
            computeLayout();
            redraw();
        });

        // Recalcular quan canviïn les mides
        canvas.widthProperty().addListener((obs, ov, nv) -> { computeLayout(); redraw(); });
        canvas.heightProperty().addListener((obs, ov, nv) -> { computeLayout(); redraw(); });
    }

}
