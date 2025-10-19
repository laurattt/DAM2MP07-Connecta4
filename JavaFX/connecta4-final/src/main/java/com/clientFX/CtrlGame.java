package com.clientFX;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.event.ActionEvent;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class CtrlGame implements Initializable {

    @FXML private Canvas canvas;
    @FXML private Button buttonAdd;
    @FXML private Button buttonClear;
    @FXML private Circle rojo1, rojo2, rojo3, rojo4, rojo5, rojo6, rojo7, rojo8, rojo9, rojo10, rojo11, rojo12, rojo13, rojo14, rojo15, rojo16, rojo17, rojo18, rojo19, rojo20, rojo21;
    @FXML private Circle azul1, azul2, azul3, azul4, azul5, azul6, azul7, azul8, azul9, azul10, azul11, azul12, azul13, azul14, azul15, azul16, azul17, azul18, azul19, azul20, azul21;

    //variables tablero 
    private GraphicsContext gc;
    private static final int COLS = 7;
    private static final int ROWS = 6;
    private double margin = 20;
    private double boardX, boardY;
    private double cell;
    private double boardW, boardH;
    private double headerH;
    private final int[][] board = new int[ROWS][COLS];

    //variables aniamción
    private boolean animating = false;
    private int animCol = -1, animRow = -1;
    private double animY;
    private double targetY;
    private double fallSpeed = 800;

    private AnimacionFichas timer;
    private long lastRunNanos = 0;

    private int jugadorActual = 1;
    private boolean partidaTerminada = false;

    private Circle fichaArrastrada;
    private double offsetX, offsetY;

    //fichas
    private List<Circle> fichasRojas = new ArrayList<>();
    private List<Circle> fichasAzules = new ArrayList<>();
    


    @Override
    public void initialize(URL url, ResourceBundle rb) {

        gc = canvas.getGraphicsContext2D();

        setFixedCanvasSize();
        inicializarListasFichas();

        // animaciones
        timer = new AnimacionFichas(
                fps -> update(),
                this::redraw,
                60
        );
        timer.start();

        //canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleClickOnCanvas); 
        
        Platform.runLater(() -> {
            computeLayout();
            redraw();
        });
    }

   
    private void setFixedCanvasSize() {
        
        // mantiene constante el tamaño del canva (tablero), si se redimenciona se queda del mismo tamaño
        canvas.widthProperty().unbind();
        canvas.heightProperty().unbind();
        
        // tamaño fijo del tablero (si hay un error al cargar tablero, ajustar aqui)
        canvas.setWidth(1000);
        canvas.setHeight(500);
        
        // parent = contenedor
        canvas.parentProperty().addListener((o, oldParent, newParent) -> {
        if (newParent instanceof Region region) {

            Platform.runLater(() -> {
                // esto calcula el espacio que ha de tomar el tablero para dibujarse dentro del parent/contenedor 
                double anchoPadre = region.getWidth();
                double altoPadre = region.getHeight();

                // limites min
                double nuevoAncho = Math.min(anchoPadre - 250, 800);
                double nuevoAlto = Math.min(altoPadre - 100, 600);
                if (nuevoAncho < 400) nuevoAncho = 400;
                if (nuevoAlto < 300) nuevoAlto = 300;

                canvas.setWidth(nuevoAncho);
                canvas.setHeight(nuevoAlto);

                computeLayout();
                redraw();
            });
        }
    });}

    // fichas arrastre 
    @FXML
    private void iniciarArrastreFicha(MouseEvent event) {
        if (partidaTerminada || animating) {
            System.out.println("No se puede arrastrar - Partida terminada ");
            return;
        }
        
        fichaArrastrada = (Circle) event.getSource();
        Color colorFicha = (Color) fichaArrastrada.getFill();
        
        // turno verificacion
        boolean turnoCorrecto = (jugadorActual == 1 && colorFicha.equals(Color.RED)) || 
                               (jugadorActual == 2 && colorFicha.equals(Color.BLUE));
        
        if (turnoCorrecto) {
            //getScene da posicion del ratón (horizontal-vertical)
            offsetX = event.getSceneX(); 
            offsetY = event.getSceneY();
            //System.out.println("arrastro la ficha: " + jugadorActual);
        } else {
            fichaArrastrada = null;
            System.out.println("Respeta el turno");
        }
    }

    @FXML
    private void arrastrarFicha(MouseEvent event) {
        if (fichaArrastrada == null) return;
        
        // pilla coordenada de la ficha y resta la coordenada del raton, asi asegura la posición de arrastre (si no entiendes aqui, avisame)
        fichaArrastrada.setTranslateX(event.getSceneX() - offsetX);
        fichaArrastrada.setTranslateY(event.getSceneY() - offsetY);
        
        // redibujar para actualizar posicioj
        redraw();
    }

    @FXML
    private void soltarFicha(MouseEvent event) {
        if (fichaArrastrada == null) return;

        try {
            // calcula columna basada en la posición del mouse
            double mouseX = event.getSceneX();
            
            // convierte coordenadas de pantalla a coordenadas del canvas
            double canvasSceneX = canvas.localToScene(canvas.getBoundsInLocal()).getMinX();
            double relativeX = mouseX - canvasSceneX;
            
            // calcular columna
            int col = (int) ((relativeX - boardX) / cell);
            
            if (col >= 0 && col < COLS) {
                fichaArrastrada.setVisible(false); // hace invisible la ficha una vez hay arrastre
                tryDropInColumn(col);
            } else {
                System.out.println("Columna inválida: " + col);
                fichaArrastrada.setVisible(true); // regresa a donde estaba la ficha si la columna no es valida
            }
        } catch (Exception e) {
            System.out.println("Error al soltar ficha: " + e.getMessage());
            if (fichaArrastrada != null) {
                fichaArrastrada.setVisible(true); //  si pasa algun error hace que la ficha se regenere donde estaba 
            }
        }

        // regresa ficha a posicion
        fichaArrastrada.setTranslateX(0);
        fichaArrastrada.setTranslateY(0);
        fichaArrastrada = null;
    }

    private void inicializarListasFichas() {
        try {
            fichasRojas.clear();
            fichasAzules.clear();

            //fichas rojas 
            for (int i = 1; i <= 21; i++) {
                Circle ficha = (Circle) getClass().getDeclaredField("rojo" + i).get(this);
                if (ficha != null) {
                    fichasRojas.add(ficha);
                    ficha.setVisible(true); // visibilidad inicial
                }
            }
            
            //fichas azules
            for (int i = 1; i <= 21; i++) {
                Circle ficha = (Circle) getClass().getDeclaredField("azul" + i).get(this);
                if (ficha != null) {
                    fichasAzules.add(ficha);
                    ficha.setVisible(true); // visibilidad inicial
                }
            }
            
            System.out.println("Cantidad fichas: " + fichasRojas.size() + ", Azules: " + fichasAzules.size());
            
        } catch (Exception e) {
            System.out.println("Error inicializando listas de fichas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //metodo albert, calcula tamaño para el tablero dentro del canvas
    private void computeLayout() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        if (w <= 0 || h <= 0) return;

        headerH = 60;

        double availableW = w - 2 * margin;
        double availableH = h - 2 * margin - headerH;

        cell = Math.min(availableW / COLS, availableH / ROWS);
        
        // limita tamaño de celda para evitar problemas
        if (cell < 40) cell = 40;
        if (cell > 70) cell = 70; 

        boardW = cell * COLS;
        boardH = cell * ROWS;

        boardX = (w - boardW) / 2.0;
        boardY = margin + headerH;
        
        //System.out.println("Layout calculado: " + cell + ", Board: " + boardW + "x" + boardH);
    }

    @FXML // comprobacion si el jugador aun tiene fichas 
    private void actionAdd(ActionEvent event) {
        if (animating || partidaTerminada) return;
        
        // buscar una ficha disponible del jugador que esta en turno
        List<Circle> fichasDisponibles = (jugadorActual == 1) ? fichasRojas : fichasAzules;
        Circle fichaDisponible = null;
        
        for (Circle ficha : fichasDisponibles) {
            if (ficha != null && ficha.isVisible()) {
                fichaDisponible = ficha;
                break;
            }
        }
        
        if (fichaDisponible != null) {
            fichaDisponible.setVisible(false);
            int col = (int) Math.floor(Math.random() * COLS); //calculo para columna aleatoria (boton add)
            tryDropInColumn(col);
        } else {
            System.out.println("No hay fichas disponibles para el jugador " + jugadorActual);
        }
    }

    @FXML
    private void actionClear(ActionEvent event) { // reinicia interfaz
        resetBoard();
        redraw();
    }
    
    private void resetBoard() {
        // limpia tablero
        for (int fila = 0; fila < ROWS; fila++) {
            for (int columna = 0; columna < COLS; columna++) {
                board[fila][columna] = 0;
            }
        }
        
        // reinicia estado del juego
        animating = false;
        animCol = animRow = -1;
        partidaTerminada = false;
        jugadorActual = 1;

        // reiniciar visibilidad de TODAS las fichas usando las listas/al reiniciarlas todas hay emjor flujo 
        try {

            for (Circle ficha : fichasRojas) {
                if (ficha != null) {
                    ficha.setVisible(true);
                    ficha.setTranslateX(0);  // resetear posición de arrastre - posicion inicial
                    ficha.setTranslateY(0);
                }
            }
            
            for (Circle ficha : fichasAzules) {
                if (ficha != null) {
                    ficha.setVisible(true);
                    ficha.setTranslateX(0);  // resetear posición de arrastre - posicion inicial
                    ficha.setTranslateY(0);
                }
            }
            
            System.out.println("Fichas reiniciadas - Rojas: " + fichasRojas.size() + ", Azules: " + fichasAzules.size());
            
        } catch (Exception e) {
            System.out.println("Error reiniciando fichas: " + e.getMessage());
            e.printStackTrace();
        }
        
        // forzar redibujado
        redraw();
    }

    // private void handleClickOnCanvas(MouseEvent e) { // este metodo se encargaba de que cuando se presionaba en la parte de encima del tablero, cayera una ficha, comentado porque generaba errores 
    //     if (animating || partidaTerminada) return;

    //     double x = e.getX();
    //     double y = e.getY();

    //     if (y < boardY && x >= boardX && x < boardX + boardW) {
    //         int col = (int) ((x - boardX) / cell);
    //         tryDropInColumn(col);
    //     }
    // }

    //metodo albert
    private void tryDropInColumn(int columna) {
        if (partidaTerminada || animating) return;

        int filaDisponible = findLowestEmptyRow(columna);
        if (filaDisponible < 0) return;

        animCol = columna;
        animRow = filaDisponible;
        animY = boardY - cell * 2;
        targetY = boardY + filaDisponible * cell + cell * 0.5;
        animating = true;
        lastRunNanos = System.nanoTime();
        
        //System.out.println("Posición inicial Y: " + animY + ", Objetivo Y: " + targetY);
    }

    private int findLowestEmptyRow(int col) {
        for (int r = ROWS - 1; r >= 0; r--) {
            if (board[r][col] == 0) return r;
        }
        return -1;
    }

    private void update() {
        long now = System.nanoTime();
        double dt;
        
        if (lastRunNanos == 0) {
            dt = 0.016;
        } else {
            dt = (now - lastRunNanos) / 1_000_000_000.0;
            if (dt > 0.1) dt = 0.016;
        }
        lastRunNanos = now;

        if (animating) {
            animY += fallSpeed * dt;
            
            if (animY >= targetY) {
                animY = targetY;
                animating = false;
                
                if (animRow >= 0 && animCol >= 0) {
                    board[animRow][animCol] = jugadorActual;
                    
                    if (comprobarVictoria(animRow, animCol, jugadorActual)) {
                        partidaTerminada = true;
                        System.out.println("Ha ganado el Jugador " + (jugadorActual == 1 ? "ROJO" : "AZUL") + ":) !");
                    } else if (tableroLleno()) {
                        partidaTerminada = true;
                        System.out.println("Empate");
                    } else {
                        jugadorActual = (jugadorActual == 1) ? 2 : 1;
                        System.out.println("Turno del Jugador " + (jugadorActual == 1 ? "ROJO" : "AZUL"));
                    }
                }
            }
        }
    }

    private boolean comprobarVictoria(int fila, int columna, int jugador) {
        return (contarEnLinea(fila, columna, 1, 0, jugador) >= 4 ||
                contarEnLinea(fila, columna, 0, 1, jugador) >= 4 ||
                contarEnLinea(fila, columna, 1, 1, jugador) >= 4 ||
                contarEnLinea(fila, columna, 1, -1, jugador) >= 4);
    }

    private int contarEnLinea(int fila, int columna, int moverFila, int moverColumna, int jugador) {
        int contador = 1; // es igual a uno porque la ficha arrastrada ya ha contado

        //suma fichas hacia adelante
        int f = fila + moverFila;
        int c = columna + moverColumna;
        while (f >= 0 && f < ROWS && c >= 0 && c < COLS && board[f][c] == jugador) {
            contador++;
            f += moverFila;
            c += moverColumna;
        }

        //suma fichas hacia atras
        f = fila - moverFila;
        c = columna - moverColumna;
        while (f >= 0 && f < ROWS && c >= 0 && c < COLS && board[f][c] == jugador) {
            contador++;
            f -= moverFila;
            c -= moverColumna;
        }

        return contador;
    }

    private boolean tableroLleno() {
        for (int c = 0; c < COLS; c++) {
            if (board[0][c] == 0) return false;
        }
        return true;
    }

    private void redraw() {
        if (canvas.getWidth() <= 0 || canvas.getHeight() <= 0) return;

        gc.setFill(Color.rgb(245, 245, 245));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        drawHeader();
        drawBoardBackground();
        drawDiscsFromBoard();

        // ficha arrastrada SIEMPRE encima del tablero
        if (fichaArrastrada != null) {
            drawDraggedDisc();
        }

        // animación encima de todo
        if (animating) {
            drawFallingDisc();
        }
    }

    private void drawHeader() {
        gc.setFill(Color.rgb(230, 230, 230));
        gc.fillRect(0, margin, canvas.getWidth(), headerH);

        gc.setStroke(Color.GRAY);
        gc.strokeRect(boardX, margin, boardW, headerH);

        gc.setStroke(Color.rgb(180, 180, 180));
        for (int c = 1; c < COLS; c++) {
            double x = boardX + c * cell;
            gc.strokeLine(x, margin, x, margin + headerH);
        }
    }

    private void drawBoardBackground() {
        gc.setFill(Color.rgb(30, 96, 199));
        gc.fillRect(boardX, boardY, boardW, boardH);

        double r = cell * 0.42;
        for (int rIdx = 0; rIdx < ROWS; rIdx++) {
            for (int c = 0; c < COLS; c++) {
                double cx = boardX + c * cell + cell * 0.5;
                double cy = boardY + rIdx * cell + cell * 0.5;

                gc.setFill(Color.rgb(235, 235, 235));
                gc.fillOval(cx - r, cy - r, r * 2, r * 2);
            }
        }
    }

    private void drawDiscsFromBoard() {
        double radioFicha = cell * 0.42;

        for (int fila = 0; fila < ROWS; fila++) {
            for (int columna = 0; columna < COLS; columna++) {
                int valorCelda = board[fila][columna];
                
                double centroX = boardX + columna * cell + cell * 0.5;
                double centroY = boardY + fila * cell + cell * 0.5;

                // primero dibujar el "agujero" (fondo del hueco)
                gc.setFill(Color.rgb(235, 235, 235));
                gc.fillOval(centroX - radioFicha, centroY - radioFicha, radioFicha * 2, radioFicha * 2);

                // ñdibuja la ficha si existe (encima del agujero)
                if (valorCelda != 0) {
                    if (valorCelda == 1) {
                        gc.setFill(Color.RED);
                    } else if (valorCelda == 2) {
                        gc.setFill(Color.BLUE);
                    }
                    gc.fillOval(centroX - radioFicha, centroY - radioFicha, radioFicha * 2, radioFicha * 2);
                }
            }
        }
    }

    private void drawDraggedDisc() {
        if (fichaArrastrada != null) {
            double radioFicha = cell * 0.42;
            
            // obtener posición actual de la ficha arrastrada
            double centroX = fichaArrastrada.localToScene(fichaArrastrada.getCenterX(), 0).getX();
            double centroY = fichaArrastrada.localToScene(0, fichaArrastrada.getCenterY()).getY();
            
            // convertir a coordenadas del canvas
            double canvasX = centroX - canvas.getLayoutX() - canvas.getParent().getLayoutX();
            double canvasY = centroY - canvas.getLayoutY() - canvas.getParent().getLayoutY();
            
            // dibujar la ficha arrastrada en el canvas
            gc.setFill((Color) fichaArrastrada.getFill());
            gc.fillOval(canvasX - radioFicha, canvasY - radioFicha, radioFicha * 2, radioFicha * 2);
        }
    }

    private void drawFallingDisc() {
        if (animCol < 0) return;

        double radioFicha = cell * 0.42;
        double centroX = boardX + animCol * cell + cell * 0.5;
        double centroY = animY;

        // dibuja ficha que está encima del todo 
        gc.setFill(jugadorActual == 1 ? Color.RED : Color.BLUE);
        gc.fillOval(centroX - radioFicha, centroY - radioFicha, radioFicha * 2, radioFicha * 2);
    }
} 