package tetris.ui;

import javafx.application.Application;
import javafx.animation.AnimationTimer;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.effect.DropShadow;
import javafx.util.Duration;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import java.io.InputStream;
import javafx.stage.Stage;
import tetris.engine.GameAction;
import tetris.engine.GameEngine;
import tetris.model.Board;

import java.util.EnumSet;
import java.util.Set;

/**
 * Example JavaFX entry point showing how to wire GameEngine + BoardRenderer.
 */
public class TetrisFxAppExample extends Application {
    private static final double CELL_SIZE = 30.0;
    private static final long MOVE_INITIAL_DELAY_NS = 120_000_000L;
    private static final long MOVE_REPEAT_NS = 50_000_000L;
    private static final long SOFT_DROP_REPEAT_NS = 35_000_000L;

    private final Set<KeyCode> pressedKeys = EnumSet.noneOf(KeyCode.class);

    private GameEngine engine;
    private HoldPanelRenderer holdPanelRenderer;
    private AnimationTimer inputTimer;

    private long leftHeldNs;
    private long rightHeldNs;
    private long downHeldNs;
    private long leftRepeatNs;
    private long rightRepeatNs;
    private long downRepeatNs;

    private Label scoreLabel;
    private Label linesLabel;
    private Label levelLabel;
    private StackPane canvasStack;
    private VBox helpOverlay;
    private StackPane languageOverlay;
    private StackPane levelOverlay;
    private StackPane pauseOverlay;
    private StackPane exitConfirmOverlay;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(Board.COLUMNS * CELL_SIZE, (Board.ROWS + 3) * CELL_SIZE);
        Canvas holdCanvas = new Canvas(6 * CELL_SIZE, 12 * CELL_SIZE);

        BoardRenderer renderer = new BoardRenderer(canvas, CELL_SIZE);
        holdPanelRenderer = new HoldPanelRenderer(holdCanvas, CELL_SIZE);
        engine = new GameEngine();
        engine.addHoldPieceListener(holdPanelRenderer);
        engine.addNextPieceListener(holdPanelRenderer);
        // Load custom font (Fredoka One) if available.
        try (InputStream is = getClass().getResourceAsStream("/fonts/FredokaOne-Regular.ttf")) {
            if (is != null) {
                Font.loadFont(is, 10);
            }
        } catch (Exception ex) {
            System.err.println("Could not load Fredoka One font: " + ex.getMessage());
        }

        // HUD labels
        scoreLabel = new Label("Score: 0");
        scoreLabel.setFont(Font.font("Fredoka One", 18));
        scoreLabel.getStyleClass().add("hud-label");
        scoreLabel.setTextFill(Color.WHITE);
        scoreLabel.setEffect(new DropShadow(6, Color.web("#4B0082", 0.85)));

        linesLabel = new Label("Lines: 0");
        linesLabel.setFont(Font.font("Fredoka One", 14));
        linesLabel.getStyleClass().add("hud-label");
        linesLabel.setTextFill(Color.WHITE);
        linesLabel.setEffect(new DropShadow(6, Color.web("#4B0082", 0.85)));

        levelLabel = new Label(engine.getText("label.level") + engine.getLevel());
        levelLabel.setFont(Font.font("Fredoka One", 14));
        levelLabel.getStyleClass().add("level-label");
        levelLabel.setTextFill(Color.WHITE);
        levelLabel.setEffect(new DropShadow(6, Color.web("#4B0082", 0.85)));

        // Canvas stack for overlay and styling
        canvasStack = new StackPane(canvas);
        canvasStack.getStyleClass().add("canvas-stack");
        // Add renderer overlay node (styled via CSS) so states can show overlays.
        canvasStack.getChildren().add(renderer.getOverlayNode());
        DropShadow ds = new DropShadow();
        ds.setRadius(8);
        ds.setOffsetY(2);
        ds.setColor(Color.color(0,0,0,0.7));
        canvas.setEffect(ds);
        holdCanvas.setEffect(ds);

        // Language selection overlay (shown at startup) — will cover full window
        languageOverlay = new StackPane();
        languageOverlay.setAlignment(Pos.CENTER);

        Label langPrompt = new Label("Choose language / Chọn ngôn ngữ");
        langPrompt.setFont(Font.font("Fredoka One", 18));
        langPrompt.setTextFill(Color.WHITE);
        langPrompt.getStyleClass().add("language-prompt");
        langPrompt.setEffect(new DropShadow(6, Color.web("#4B0082", 0.85)));
        Button enButton = new Button("English");
        Button viButton = new Button("Tiếng Việt");
        HBox langButtons = new HBox(8, enButton, viButton);
        langButtons.setAlignment(Pos.CENTER);

        Canvas langGrid = new Canvas();
        langGrid.widthProperty().bind(languageOverlay.widthProperty());
        langGrid.heightProperty().bind(languageOverlay.heightProperty());
        // Redraw grid when size changes
        langGrid.widthProperty().addListener((obs, o, n) -> drawGrid(langGrid));
        langGrid.heightProperty().addListener((obs, o, n) -> drawGrid(langGrid));

        VBox langBox = new VBox(12, langPrompt, langButtons);
        langBox.setAlignment(Pos.CENTER);
        langBox.setPadding(new Insets(16));

        languageOverlay.getChildren().addAll(langGrid, langBox);

        // Help overlay will be built after language selection
        helpOverlay = null;
        levelOverlay = null;

        // Controls / status panel: contains score, lines, and control meanings
        Label moveLabel = new Label(engine.getText("help.move"));
        moveLabel.setFont(Font.font("Fredoka One", 12));
        moveLabel.getStyleClass().add("help-line");
        moveLabel.setTextFill(Color.WHITE);

        Label rotateLabel = new Label(engine.getText("help.rotate"));
        rotateLabel.setFont(Font.font("Fredoka One", 12));
        rotateLabel.getStyleClass().add("help-line");
        rotateLabel.setTextFill(Color.WHITE);

        Label softDropLabel = new Label(engine.getText("help.softdrop"));
        softDropLabel.setFont(Font.font("Fredoka One", 12));
        softDropLabel.getStyleClass().add("help-line");
        softDropLabel.setTextFill(Color.WHITE);

        Label holdLabel = new Label(engine.getText("help.hold"));
        holdLabel.setFont(Font.font("Fredoka One", 12));
        holdLabel.getStyleClass().add("help-line");
        holdLabel.setTextFill(Color.WHITE);

        Label hardDropLabel = new Label(engine.getText("help.harddrop"));
        hardDropLabel.setFont(Font.font("Fredoka One", 12));
        hardDropLabel.getStyleClass().add("help-line");
        hardDropLabel.setTextFill(Color.WHITE);

        Separator separator = new Separator();
        separator.getStyleClass().add("info-separator");
        separator.setPrefWidth(140);

        VBox infoPanel = new VBox(6);
        infoPanel.setAlignment(Pos.TOP_CENTER);
        infoPanel.getStyleClass().add("info-panel");
        infoPanel.setPadding(new Insets(8));
        infoPanel.getChildren().addAll(scoreLabel, linesLabel, separator, moveLabel, rotateLabel, softDropLabel, holdLabel, hardDropLabel);

        VBox rightBox = new VBox(12, levelLabel, holdCanvas, infoPanel);
        rightBox.setAlignment(Pos.TOP_CENTER);
        rightBox.setPadding(new Insets(8));

        HBox root = new HBox(12, canvasStack, rightBox);
        root.getStyleClass().add("root-background");
        // Wrap the main layout in a StackPane so overlays can cover the full window.
        StackPane rootStack = new StackPane(root);
        Scene scene = new Scene(rootStack);
        // Add the language overlay to the top-level stack so it covers whole window
        rootStack.getChildren().add(languageOverlay);
        languageOverlay.prefWidthProperty().bind(rootStack.widthProperty());
        languageOverlay.prefHeightProperty().bind(rootStack.heightProperty());
        // Load centralized stylesheet for theme and component styles.
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();
            boolean firstPress = pressedKeys.add(code);

            if (code != KeyCode.ESCAPE) {
                if (languageOverlay != null && languageOverlay.isVisible()) {
                    return;
                }
                if (levelOverlay != null && levelOverlay.isVisible()) {
                    return;
                }
            }

            // If pause overlay is visible, only accept resume/restart/escape keys
            if (pauseOverlay != null && pauseOverlay.isVisible()) {
                if (code != KeyCode.R && code != KeyCode.ENTER && code != KeyCode.ESCAPE) {
                    return;
                }
            }

            // One-shot actions should happen once when key goes down.
            if (!firstPress) {
                return;
            }

            switch (code) {
                case ENTER:
                    if (pauseOverlay != null && pauseOverlay.isVisible()) {
                        engine.handleInput(GameAction.RESUME);
                        pauseOverlay.setVisible(false);
                        if (rootStack.getChildren().contains(pauseOverlay)) {
                            rootStack.getChildren().remove(pauseOverlay);
                        }
                    } else {
                        engine.handleInput(GameAction.START);
                    }
                    break;
                case UP:
                    engine.handleInput(GameAction.ROTATE);
                    break;
                case SPACE:
                    engine.handleInput(GameAction.HARD_DROP);
                    break;
                case P:
                    engine.handleInput(GameAction.PAUSE);
                    if (pauseOverlay == null) buildPauseOverlay();
                    if (!rootStack.getChildren().contains(pauseOverlay)) rootStack.getChildren().add(pauseOverlay);
                    pauseOverlay.setVisible(true);
                    break;
                case C:
                    engine.handleInput(GameAction.HOLD);
                    break;
                case H:
                    if (helpOverlay != null) {
                        helpOverlay.setVisible(!helpOverlay.isVisible());
                    }
                    break;
                case R:
                    if (pauseOverlay != null && pauseOverlay.isVisible()) {
                        engine.handleInput(GameAction.RESTART);
                        pauseOverlay.setVisible(false);
                        if (rootStack.getChildren().contains(pauseOverlay)) rootStack.getChildren().remove(pauseOverlay);
                    } else if (engine.getCurrentState() == engine.getPausedState()) {
                        engine.handleInput(GameAction.RESTART);
                    } else if (engine.getCurrentState() == engine.getGameOverState()) {
                        engine.handleInput(GameAction.RESTART);
                    }
                    break;
                case ESCAPE:
                    // Nếu đang ở màn chọn level → về màn chọn ngôn ngữ
                    if (levelOverlay != null && levelOverlay.isVisible()) {
                        rootStack.getChildren().remove(levelOverlay);
                        levelOverlay.setVisible(false);
                        // Hiện lại màn chọn ngôn ngữ
                        if (!rootStack.getChildren().contains(languageOverlay)) {
                            rootStack.getChildren().add(languageOverlay);
                        }
                        languageOverlay.setVisible(true);
                        break;
                    }
                    // Nếu đang ở màn chọn ngôn ngữ → thoát game
                    if (languageOverlay != null && languageOverlay.isVisible()) {
                        if (exitConfirmOverlay == null) buildExitConfirmOverlay(rootStack);
                        languageOverlay.setVisible(false);
                        exitConfirmOverlay.setVisible(true);
                        break;
                    }
                    if (pauseOverlay != null && pauseOverlay.isVisible()) {
                        pauseOverlay.setVisible(false);
                        if (rootStack.getChildren().contains(pauseOverlay)) rootStack.getChildren().remove(pauseOverlay);
                        if (levelOverlay != null) {
                            if (!rootStack.getChildren().contains(levelOverlay)) rootStack.getChildren().add(levelOverlay);
                            levelOverlay.setVisible(true);
                        }
                    } else if (engine.getCurrentState() == engine.getGameOverState()) {
                        if (levelOverlay != null) {
                            if (!rootStack.getChildren().contains(levelOverlay)) {
                                rootStack.getChildren().add(levelOverlay);
                            }
                            levelOverlay.setVisible(true);
                        }
                    } else if (engine.getCurrentState() == engine.getPlayingState()) {
                        engine.handleInput(GameAction.PAUSE);
                        if (pauseOverlay == null) buildPauseOverlay();
                        if (!rootStack.getChildren().contains(pauseOverlay)) rootStack.getChildren().add(pauseOverlay);
                        pauseOverlay.setVisible(true);
                    } else {
                        engine.handleInput(GameAction.BACK_TO_MENU);
                    }
                    break;
                default:
                    break;
            }
        });
        scene.setOnKeyReleased(event -> pressedKeys.remove(event.getCode()));

        // Language selection button handlers: after language choose, show level overlay
        enButton.setOnAction(e -> {
            engine.setLanguage(tetris.engine.GameEngine.Language.EN);
            buildHelpOverlay();
            // remove language overlay and show level chooser
            rootStack.getChildren().remove(languageOverlay);
            languageOverlay.setVisible(false);

            // build level overlay with grid background and larger colored buttons (2 columns x 5 rows, zigzag order)
            levelOverlay = new StackPane();
            levelOverlay.setAlignment(Pos.CENTER);

            Canvas levelGrid = new Canvas();
            levelGrid.widthProperty().bind(levelOverlay.widthProperty());
            levelGrid.heightProperty().bind(levelOverlay.heightProperty());
            levelGrid.widthProperty().addListener((obs,o,n) -> drawGrid(levelGrid));
            levelGrid.heightProperty().addListener((obs,o,n) -> drawGrid(levelGrid));

            Label levelPrompt = new Label(engine.getText("level.title"));
            levelPrompt.setFont(Font.font("Fredoka One", 20));
            levelPrompt.setTextFill(Color.WHITE);

            GridPane grid = new GridPane();
            grid.setHgap(18);
            grid.setVgap(18);
            grid.setAlignment(Pos.CENTER);

            String[] colors = new String[]{"#ff6b6b","#ff9f43","#ffd166","#06d6a0","#4cc9f0","#1e90ff","#845ec2","#ff77a8","#d65db1","#8ac926"};
            int buttonSize = 96;
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 2; c++) {
                    int base = r * 2;
                    int lvl = (r % 2 == 0) ? (base + c + 1) : (base + (2 - c));
                    Button b = new Button(String.valueOf(lvl));
                    final int lv = lvl;
                    String color = colors[(lv - 1) % colors.length];
                    b.setPrefSize(buttonSize, buttonSize);
                    b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold; -fx-background-radius: 8;");

                    b.setOnMouseEntered(evt -> {
                        b.setEffect(new DropShadow(14, Color.web(color)));
                        b.setScaleX(1.06);
                        b.setScaleY(1.06);
                    });
                    b.setOnMouseExited(evt -> {
                        b.setEffect(null);
                        b.setScaleX(1.0);
                        b.setScaleY(1.0);
                    });
                    b.setOnMousePressed(evt -> { b.setScaleX(0.96); b.setScaleY(0.96); });
                    b.setOnMouseReleased(evt -> { b.setScaleX(1.06); b.setScaleY(1.06); });

                    b.setOnAction(ev -> {
                        engine.setLevel(lv);
                        engine.resetGame();
                        engine.changeState(engine.getPlayingState());
                        // Update HUD
                        scoreLabel.setText(engine.getText("label.score") + engine.getScore());
                        linesLabel.setText(engine.getText("label.lines") + engine.getTotalClearedLines());
                        levelLabel.setText(engine.getText("label.level") + engine.getLevel());
                        moveLabel.setText(engine.getText("help.move"));
                        rotateLabel.setText(engine.getText("help.rotate"));
                        softDropLabel.setText(engine.getText("help.softdrop"));
                        holdLabel.setText(engine.getText("help.hold"));
                        hardDropLabel.setText(engine.getText("help.harddrop"));

                        // Pulse animation on level label
                        ScaleTransition st = new ScaleTransition(Duration.millis(260), levelLabel);
                        st.setFromX(1.0); st.setFromY(1.0);
                        st.setToX(1.35); st.setToY(1.35);
                        st.setAutoReverse(true); st.setCycleCount(2); st.play();

                        rootStack.getChildren().remove(levelOverlay);
                        levelOverlay.setVisible(false);
                        rootStack.getChildren().add(helpOverlay);
                        helpOverlay.setVisible(false);

                        engine.start(renderer);
                        scene.getRoot().requestFocus();
                        startInputLoop();
                    });

                    grid.add(b, c, r);
                }
            }

            VBox centerBox = new VBox(18, levelPrompt, grid);
            centerBox.setAlignment(Pos.CENTER);
            centerBox.setPadding(new Insets(16));

            levelOverlay.getChildren().addAll(levelGrid, centerBox);
            levelOverlay.prefWidthProperty().bind(rootStack.widthProperty());
            levelOverlay.prefHeightProperty().bind(rootStack.heightProperty());
            rootStack.getChildren().add(levelOverlay);
        });

        viButton.setOnAction(e -> {
            engine.setLanguage(tetris.engine.GameEngine.Language.VI);
            buildHelpOverlay();
            // remove language overlay and show level chooser
            rootStack.getChildren().remove(languageOverlay);
            languageOverlay.setVisible(false);

            // build VI level overlay consistent with EN: 2 columns x 5 rows, zigzag order
            levelOverlay = new StackPane();
            levelOverlay.setAlignment(Pos.CENTER);
            levelOverlay.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 0% 100%, rgba(0,0,0,0.85), rgba(43,11,68,0.65));");

            Canvas levelGrid = new Canvas();
            levelGrid.widthProperty().bind(levelOverlay.widthProperty());
            levelGrid.heightProperty().bind(levelOverlay.heightProperty());
            levelGrid.widthProperty().addListener((obs,o,n) -> drawGrid(levelGrid));
            levelGrid.heightProperty().addListener((obs,o,n) -> drawGrid(levelGrid));

            Label levelPrompt = new Label(engine.getText("level.title"));
            levelPrompt.setFont(Font.font("Fredoka One", 20));
            levelPrompt.setTextFill(Color.WHITE);

            GridPane grid = new GridPane();
            grid.setHgap(18);
            grid.setVgap(18);
            grid.setAlignment(Pos.CENTER);

            String[] colors = new String[]{"#ff6b6b","#ff9f43","#ffd166","#06d6a0","#4cc9f0","#1e90ff","#845ec2","#ff77a8","#d65db1","#8ac926"};
            int buttonSize = 96;
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 2; c++) {
                    int base = r * 2;
                    int lvl = (r % 2 == 0) ? (base + c + 1) : (base + (2 - c));
                    Button b = new Button(String.valueOf(lvl));
                    final int lv = lvl;
                    String color = colors[(lv - 1) % colors.length];
                    b.setPrefSize(buttonSize, buttonSize);
                    b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold; -fx-background-radius: 8;");

                    b.setOnMouseEntered(evt -> { b.setEffect(new DropShadow(14, Color.web(color))); b.setScaleX(1.06); b.setScaleY(1.06); });
                    b.setOnMouseExited(evt -> { b.setEffect(null); b.setScaleX(1.0); b.setScaleY(1.0); });
                    b.setOnMousePressed(evt -> { b.setScaleX(0.96); b.setScaleY(0.96); });
                    b.setOnMouseReleased(evt -> { b.setScaleX(1.06); b.setScaleY(1.06); });

                    b.setOnAction(ev -> {
                        engine.setLevel(lv);
                        engine.resetGame();
                        engine.changeState(engine.getPlayingState());
                        scoreLabel.setText(engine.getText("label.score") + engine.getScore());
                        linesLabel.setText(engine.getText("label.lines") + engine.getTotalClearedLines());
                        moveLabel.setText(engine.getText("help.move"));
                        rotateLabel.setText(engine.getText("help.rotate"));
                        softDropLabel.setText(engine.getText("help.softdrop"));
                        holdLabel.setText(engine.getText("help.hold"));
                        hardDropLabel.setText(engine.getText("help.harddrop"));

                        rootStack.getChildren().remove(levelOverlay);
                        levelOverlay.setVisible(false);
                        rootStack.getChildren().add(helpOverlay);
                        helpOverlay.setVisible(false);

                        engine.start(renderer);
                        scene.getRoot().requestFocus();
                        startInputLoop();
                    });

                    grid.add(b, c, r);
                }
            }

            VBox centerBox = new VBox(18, levelPrompt, grid);
            centerBox.setAlignment(Pos.CENTER);
            centerBox.setPadding(new Insets(16));

            levelOverlay.getChildren().addAll(levelGrid, centerBox);
            levelOverlay.prefWidthProperty().bind(rootStack.widthProperty());
            levelOverlay.prefHeightProperty().bind(rootStack.heightProperty());
            rootStack.getChildren().add(levelOverlay);
        });

        stage.setTitle("Tetris - JavaFX Canvas");
        stage.setScene(scene);
        stage.show();

        // Do not start engine or input loop until language is selected.
        // engine.start(renderer) and startInputLoop() are invoked when user
        // clicks the language button handlers.
    }

    @Override
    public void stop() {
        if (inputTimer != null) {
            inputTimer.stop();
        }
        if (engine != null) {
            if (holdPanelRenderer != null) {
                engine.removeHoldPieceListener(holdPanelRenderer);
                engine.removeNextPieceListener(holdPanelRenderer);
            }
            engine.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void startInputLoop() {
        if (inputTimer != null) {
            inputTimer.stop();
        }
        inputTimer = new AnimationTimer() {
            private long previousNow;

            @Override
            public void handle(long now) {
                if (previousNow == 0L) {
                    previousNow = now;
                    return;
                }

                long deltaNs = now - previousNow;
                previousNow = now;

                processHorizontal(KeyCode.LEFT, deltaNs);
                processHorizontal(KeyCode.RIGHT, deltaNs);
                processSoftDrop(deltaNs);

                // Update HUD labels (localized after language selection)
                if (scoreLabel != null && engine != null) {
                    scoreLabel.setText(engine.getText("label.score") + engine.getScore());
                }
                if (linesLabel != null && engine != null) {
                    linesLabel.setText(engine.getText("label.lines") + engine.getTotalClearedLines());
                }
            }
        };
        inputTimer.start();
    }

    private void processHorizontal(KeyCode key, long deltaNs) {
        boolean isLeft = key == KeyCode.LEFT;
        long heldNs = isLeft ? leftHeldNs : rightHeldNs;
        long repeatNs = isLeft ? leftRepeatNs : rightRepeatNs;

        if (pressedKeys.contains(key)) {
            heldNs += deltaNs;
            repeatNs += deltaNs;

            boolean shouldMoveNow = heldNs <= deltaNs
                    || (heldNs >= MOVE_INITIAL_DELAY_NS && repeatNs >= MOVE_REPEAT_NS);

            if (shouldMoveNow) {
                engine.handleInput(isLeft ? GameAction.LEFT : GameAction.RIGHT);
                if (heldNs >= MOVE_INITIAL_DELAY_NS) {
                    repeatNs = 0L;
                }
            }
        } else {
            heldNs = 0L;
            repeatNs = 0L;
        }

        if (isLeft) {
            leftHeldNs = heldNs;
            leftRepeatNs = repeatNs;
        } else {
            rightHeldNs = heldNs;
            rightRepeatNs = repeatNs;
        }
    }

    private void processSoftDrop(long deltaNs) {
        if (pressedKeys.contains(KeyCode.DOWN)) {
            downHeldNs += deltaNs;
            downRepeatNs += deltaNs;

            boolean shouldDropNow = downHeldNs <= deltaNs || downRepeatNs >= SOFT_DROP_REPEAT_NS;
            if (shouldDropNow) {
                engine.handleInput(GameAction.DOWN);
                downRepeatNs = 0L;
            }
        } else {
            downHeldNs = 0L;
            downRepeatNs = 0L;
        }
    }

    private void buildHelpOverlay() {
        helpOverlay = new VBox(8);
        helpOverlay.setAlignment(Pos.TOP_LEFT);
        helpOverlay.setPadding(new Insets(12));
        helpOverlay.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 0% 100%, rgba(0,0,0,0.85), rgba(43,11,68,0.65)); -fx-background-radius: 8;");
        Label helpTitle = new Label(engine.getText("help.title"));
        helpTitle.setFont(Font.font("Fredoka One", 16));
        helpTitle.setTextFill(Color.WHITE);
        helpTitle.setStyle("-fx-background-color: rgba(0,0,0,0.45); -fx-padding: 6 8; -fx-background-radius: 6; -fx-border-color: #4B0082; -fx-border-width: 1; -fx-border-radius: 6;");
        helpTitle.setEffect(new DropShadow(6, Color.web("#4B0082", 0.85)));
        helpOverlay.getChildren().add(helpTitle);

        String[] helpLines = new String[] {
            engine.getText("help.move"),
            engine.getText("help.rotate"),
            engine.getText("help.softdrop"),
            engine.getText("help.hold"),
            engine.getText("help.harddrop"),
            engine.getText("help.controls"),
            engine.getText("help.menu"),
            engine.getText("help.goal")
        };

        for (String line : helpLines) {
            Label l = new Label(line);
            l.setTextFill(Color.WHITE);
            l.setStyle("-fx-background-color: rgba(0,0,0,0.35); -fx-padding: 4 6; -fx-background-radius: 6; -fx-border-color: #4B0082; -fx-border-width: 1; -fx-border-radius: 6;");
            l.setEffect(new DropShadow(4, Color.web("#4B0082", 0.8)));
            helpOverlay.getChildren().add(l);
        }
        helpOverlay.setVisible(false);
    }

    private void buildPauseOverlay() {
        pauseOverlay = new StackPane();
        pauseOverlay.setAlignment(Pos.CENTER);

        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 0% 100%, rgba(0,0,0,0.9), rgba(43,11,68,0.75)); -fx-background-radius: 8; -fx-border-color: #4B0082; -fx-border-width: 1; -fx-border-radius: 8;");

        Label title = new Label(engine.getText("paused.title"));
        title.setFont(Font.font("Fredoka One", 20));
        title.setTextFill(Color.WHITE);
        title.setEffect(new DropShadow(6, Color.web("#4B0082", 0.85)));

        String restartText = engine.getLanguage() == GameEngine.Language.VI ? "R: Chơi lại" : "R: Restart";
        String resumeText = engine.getLanguage() == GameEngine.Language.VI ? "Enter: Tiếp tục" : "Enter: Resume";
        String levelText = engine.getLanguage() == GameEngine.Language.VI ? "Esc: Về chọn cấp độ" : "Esc: Level selection";

        Label rLabel = new Label(restartText);
        rLabel.setTextFill(Color.WHITE);
        rLabel.setStyle("-fx-background-color: rgba(0,0,0,0.35); -fx-padding: 6 10; -fx-background-radius: 6; -fx-border-color: #4B0082; -fx-border-width: 1; -fx-border-radius: 6;");
        Label eLabel = new Label(resumeText);
        eLabel.setTextFill(Color.WHITE);
        eLabel.setStyle("-fx-background-color: rgba(0,0,0,0.35); -fx-padding: 6 10; -fx-background-radius: 6; -fx-border-color: #4B0082; -fx-border-width: 1; -fx-border-radius: 6;");
        Label sLabel = new Label(levelText);
        sLabel.setTextFill(Color.WHITE);
        sLabel.setStyle("-fx-background-color: rgba(0,0,0,0.35); -fx-padding: 6 10; -fx-background-radius: 6; -fx-border-color: #4B0082; -fx-border-width: 1; -fx-border-radius: 6;");

        box.getChildren().addAll(title, rLabel, eLabel, sLabel);
        pauseOverlay.getChildren().add(box);
        pauseOverlay.setVisible(false);
    }

    private void drawGrid(Canvas canvas) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        // background fill
        gc.setFill(Color.rgb(10, 6, 20, 0.85));
        gc.fillRect(0, 0, w, h);

        double spacing = 24.0;
        gc.setStroke(Color.rgb(255,255,255,0.06));
        gc.setLineWidth(1.0);

        // 45-degree lines
        for (double x = -h; x < w; x += spacing) {
            gc.strokeLine(x, 0, x + h, h);
        }

        // -45-degree lines
        for (double x = 0; x < w + h; x += spacing) {
            gc.strokeLine(x, 0, x - h, h);
        }
    }

    private void buildExitConfirmOverlay(StackPane rootStack) {
        exitConfirmOverlay = new StackPane();
        exitConfirmOverlay.setAlignment(Pos.CENTER);

        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 0% 100%, rgba(0,0,0,0.9), rgba(43,11,68,0.75)); -fx-background-radius: 8; -fx-border-color: #4B0082; -fx-border-width: 1; -fx-border-radius: 8;");

        String msg  = (engine.getLanguage() == GameEngine.Language.VI) ? "Bạn có muốn thoát game?" : "Do you want to quit?";
        String yes  = (engine.getLanguage() == GameEngine.Language.VI) ? "Thoát" : "Quit";
        String no   = (engine.getLanguage() == GameEngine.Language.VI) ? "Hủy"  : "Cancel";

        Label msgLabel = new Label(msg);
        msgLabel.setFont(Font.font("Fredoka One", 18));
        msgLabel.setTextFill(Color.WHITE);
        msgLabel.setEffect(new DropShadow(6, Color.web("#4B0082", 0.85)));

        Button yesBtn = new Button(yes);
        yesBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 24;");
        yesBtn.setOnMouseEntered(e -> yesBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 24;"));
        yesBtn.setOnMouseExited(e  -> yesBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 24;"));
        yesBtn.setOnAction(e -> javafx.application.Platform.exit());

        Button noBtn = new Button(no);
        noBtn.setStyle("-fx-background-color: #555; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 24;");
        noBtn.setOnMouseEntered(e -> noBtn.setStyle("-fx-background-color: #333; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 24;"));
        noBtn.setOnMouseExited(e  -> noBtn.setStyle("-fx-background-color: #555; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 24;"));
        noBtn.setOnAction(e -> {
            exitConfirmOverlay.setVisible(false);
            languageOverlay.setVisible(true);
        });

        HBox btnRow = new HBox(16, yesBtn, noBtn);
        btnRow.setAlignment(Pos.CENTER);

        box.getChildren().addAll(msgLabel, btnRow);
        exitConfirmOverlay.getChildren().add(box);
        exitConfirmOverlay.setVisible(false);

        exitConfirmOverlay.prefWidthProperty().bind(rootStack.widthProperty());
        exitConfirmOverlay.prefHeightProperty().bind(rootStack.heightProperty());
        rootStack.getChildren().add(exitConfirmOverlay);
    }
}
