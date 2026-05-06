package tetris.engine;

import javafx.animation.AnimationTimer;
import tetris.engine.state.GameOverState;
import tetris.engine.state.GameState;
import tetris.engine.state.MenuState;
import tetris.engine.state.PausedState;
import tetris.engine.state.PlayingState;
import tetris.model.Board;
import tetris.model.Tetromino;
import tetris.model.TetrominoFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;



/**
 * Context in State Pattern. Holds current state and delegates behavior to it.
 */
public class GameEngine {
    private static final long UPDATE_INTERVAL_NS = 500_000_000L;
    private static final long TARGET_FRAME_INTERVAL_NS = 16_666_667L;

    private static final int MAX_LEVEL = 10;
    private int level = 1;
    private static final double LEVEL_SPEED_FACTOR = 1.3; // 30% speed increase per level
    private long updateIntervalNs = UPDATE_INTERVAL_NS;

    private final Board board;
    private final TetrominoFactory tetrominoFactory;

    private final GameState menuState;
    private final GameState playingState;
    private final GameState pausedState;
    private final GameState gameOverState;
    //sounds
    private SoundManager soundManager;

    private GameState currentState;
    private Tetromino currentTetromino;
    private TetrominoFactory.TetrominoType heldTetrominoType;
    private tetris.model.Tetromino heldTetromino;
    private int score;
    private int totalClearedLines;
    private final List<HoldPieceListener> holdPieceListeners;
    private final List<NextPieceListener> nextPieceListeners;

    private AnimationTimer gameLoopTimer;
    private GameRenderer renderer;
    private long previousNow;
    private long updateAccumulatorNs;
    private long renderAccumulatorNs;

    // Line clear animation state
    private boolean lineClearAnimating = false;
    private int[] linesToClear = null;
    private long lineClearElapsedNs = 0L;
    private int lineClearColorIndex = 0; // 0=yellow,1=red
    private static final long LINE_CLEAR_ANIMATION_NS = 600_000_000L; // 600ms
    private static final long LINE_CLEAR_FLASH_PERIOD_NS = 150_000_000L; // 150ms
    private static final long FAST_LINE_CLEAR_ANIMATION_NS = 300_000_000L; // 300ms for hard-drop
    private static final long FAST_LINE_CLEAR_FLASH_PERIOD_NS = 75_000_000L; // 75ms flash for hard-drop
    private long currentLineClearAnimationNs = LINE_CLEAR_ANIMATION_NS;
    private long currentLineClearFlashPeriodNs = LINE_CLEAR_FLASH_PERIOD_NS;
    private final Random random = new Random();

    private TetrominoFactory.TetrominoType nextTetrominoType;
    // Swap (hold) flash animation state
    private boolean swapAnimating = false;
    private long swapElapsedNs = 0L;
    private static final long SWAP_FLASH_ANIMATION_NS = 220_000_000L; // 220ms
    private static final long SWAP_FLASH_PERIOD_NS = 55_000_000L; // 55ms flash period
    private Tetromino swapCandidate = null; // piece to place when animation completes
    private Tetromino swapNewHeld = null; // piece to set as held when animation completes

    // Language for UI/localized text. Null until set by UI; default to EN when accessed.
    public enum Language {
        EN,
        VI
    }

    private Language language = null;

    public void setLanguage(Language language) {
        this.language = language == null ? Language.EN : language;
    }

    public Language getLanguage() {
        return this.language == null ? Language.EN : this.language;
    }

    /**
     * Minimal localized string lookup used by state renderers and UI.
     */
    public String getText(String key) {
        Language lang = getLanguage();
        switch (lang) {
            case VI:
                switch (key) {
                    case "menu.title": return "TETRIS";
                    case "menu.start": return "Nhấn ENTER để bắt đầu";
                    case "paused.title": return "Tạm dừng";
                    case "paused.instructions": return "Nhấn R để chơi lại, Esc để về menu";
                    case "gameover.title": return "KẾT THÚC";
                    case "gameover.instructions": return "Nhấn R để chơi lại, Esc để về menu";
                    case "label.score": return "Điểm: ";
                    case "label.lines": return "Hàng: ";
                    case "help.title": return "Hướng dẫn chơi";
                    case "help.move": return "Di chuyển: ← →";
                    case "help.rotate": return "Xoay: ↑";
                    case "help.softdrop": return "Rơi mềm: ↓";
                    case "help.harddrop": return "Rơi nhanh: Space";
                    case "help.hold": return "Giữ khối: C";
                    case "help.controls": return "Bắt đầu: Enter | Tạm dừng: P | Tiếp tục: R";
                    case "help.menu": return "Quay về menu: Esc | Hiện/ẩn hướng dẫn: H";
                    case "help.goal": return "Mục tiêu: Hoàn thành hàng để ghi điểm: 1=100,2=300,3=500,4=800";
                    case "level.title": return "Chọn cấp độ";
                    default: return key;
                }
            default:
                switch (key) {
                    case "menu.title": return "TETRIS";
                    case "menu.start": return "Press ENTER to start";
                    case "paused.title": return "PAUSED";
                    case "paused.instructions": return "Press R to restart, Esc to return to menu";
                    case "gameover.title": return "GAME OVER";
                    case "gameover.instructions": return "Press R to restart, Esc to return to menu";
                    case "label.score": return "Score: ";
                    case "label.level": return "Level: ";
                    case "label.lines": return "Lines: ";
                    case "help.title": return "How to play";
                    case "help.move": return "Move: ← →";
                    case "help.rotate": return "Rotate: ↑";
                    case "help.softdrop": return "Soft drop: ↓";
                    case "help.harddrop": return "Hard drop: Space";
                    case "help.hold": return "Hold: C";
                    case "help.controls": return "Start: Enter | Pause: P | Resume: R";
                    case "help.menu": return "Back to menu: Esc | Toggle help: H";
                    case "help.goal": return "Goal: clear lines to score: 1=100,2=300,3=500,4=800";
                    case "level.title": return "Choose Level";
                    default: return key;
                }
        }
    }

    public GameEngine() {
        //sound
        this.soundManager = new SoundManager();


        this.board = new Board();
        this.tetrominoFactory = new TetrominoFactory();

        this.menuState = new MenuState();
        this.playingState = new PlayingState();
        this.pausedState = new PausedState();
        this.gameOverState = new GameOverState();
        this.holdPieceListeners = new CopyOnWriteArrayList<HoldPieceListener>();
        this.nextPieceListeners = new CopyOnWriteArrayList<NextPieceListener>();
        // initialize next piece
        this.nextTetrominoType = tetrominoFactory.randomType();
        this.heldTetrominoType = null;
        this.heldTetromino = null;

        this.currentState = menuState;
        this.currentState.enter(this);

        // Ensure update interval matches initial level
        setLevel(this.level);
    }

    public void setLevel(int newLevel) {
        if (newLevel < 1) {
            newLevel = 1;
        }
        if (newLevel > MAX_LEVEL) {
            newLevel = MAX_LEVEL;
        }
        this.level = newLevel;
        double factor = Math.pow(LEVEL_SPEED_FACTOR, this.level - 1);
        this.updateIntervalNs = (long) (UPDATE_INTERVAL_NS / factor);
    }

    public int getLevel() {
        return this.level;
    }

    public TetrominoFactory.TetrominoType getNextTetrominoType() {
        return this.nextTetrominoType;
    }

    public void addNextPieceListener(NextPieceListener listener) {
        if (listener != null) {
            nextPieceListeners.add(listener);
            listener.onNextPieceChanged(nextTetrominoType);
        }
    }

    public void removeNextPieceListener(NextPieceListener listener) {
        nextPieceListeners.remove(listener);
    }

    private void notifyNextPieceChanged(TetrominoFactory.TetrominoType nextType) {
        for (NextPieceListener l : nextPieceListeners) {
            l.onNextPieceChanged(nextType);
        }
    }

    public void changeState(GameState nextState) {
        currentState.exit(this);
        currentState = nextState;
        currentState.enter(this);
        // Hide overlay when switching to playing state so gameplay is visible.
        if (renderer != null && nextState == playingState) {
            renderer.hideOverlay();
        }
    }

    public void handleInput(GameAction action) {
        if (lineClearAnimating || swapAnimating) {
            // Ignore input while clearing or swap animation plays
            return;
        }

        currentState.handleInput(this, action);
    }

    public void update() {
        if (lineClearAnimating || swapAnimating) {
            // Pause game updates while animations play
            return;
        }

        currentState.update(this);
    }

    public void render(GameRenderer renderer) {
        currentState.render(this, renderer);
    }

    public void start(GameRenderer renderer) {
        this.renderer = renderer;
        this.previousNow = 0L;
        this.updateAccumulatorNs = 0L;
        this.renderAccumulatorNs = 0L;

        if (gameLoopTimer != null) {
            gameLoopTimer.stop();
        }

        gameLoopTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (previousNow == 0L) {
                    previousNow = now;
                    return;
                }

                long deltaTimeNs = now - previousNow;
                previousNow = now;

                // Advance line-clear animation timer if active
                if (lineClearAnimating) {
                    lineClearElapsedNs += deltaTimeNs;
                }
                // Advance swap animation timer if active
                if (swapAnimating) {
                    swapElapsedNs += deltaTimeNs;
                }

                updateAccumulatorNs += deltaTimeNs;
                renderAccumulatorNs += deltaTimeNs;

                while (updateAccumulatorNs >= updateIntervalNs) {
                    GameEngine.this.update();
                    updateAccumulatorNs -= updateIntervalNs;
                }

                while (renderAccumulatorNs >= TARGET_FRAME_INTERVAL_NS) {
                    GameEngine.this.render(GameEngine.this.renderer);
                    renderAccumulatorNs -= TARGET_FRAME_INTERVAL_NS;
                }

                // Finalize line clear after animation duration
                if (lineClearAnimating && lineClearElapsedNs >= currentLineClearAnimationNs) {
                    finalizeLineClearAnimation();
                }
                // Finalize swap after animation duration
                if (swapAnimating && swapElapsedNs >= SWAP_FLASH_ANIMATION_NS) {
                    finalizeSwapAnimation();
                }
            }
        };

        gameLoopTimer.start();
    }

    public void stop() {
        if (gameLoopTimer != null) {
            gameLoopTimer.stop();
        }
    }

    public void resetGame() {
        board.clear();
        currentTetromino = null;
        setHeldTetrominoType(null);
        this.heldTetromino = null;
        score = 0;
        totalClearedLines = 0;
    }

    public boolean stepDown() {
        if (currentTetromino == null) {
            return spawnRandomTetromino();
        }

        int nextY = currentTetromino.getY() + 1;
        if (board.checkCollision(currentTetromino.getX(), nextY, currentTetromino.getShapeRef())) {
            board.lock(currentTetromino);
            soundManager.playHardDrop();  
            // Detect full lines first and play animation if any.
            
            int[] fullLines = board.getFullLines();
            if (fullLines != null && fullLines.length > 0) {
                // Start line clear animation and postpone removal/spawn until it's done.
                startLineClearAnimation(fullLines);
                // Clear current tetromino reference since it's locked to the board now.
                currentTetromino = null;
                return false;
            }

            int cleared = board.clearFullLines();
            totalClearedLines += cleared;
            score += calculateScore(cleared);
            return spawnRandomTetromino();
            
        }

        currentTetromino.setPosition(currentTetromino.getX(), nextY);
        return false;
    }

    /**
     * Perform a hard drop: move current piece to the lowest valid Y, lock it,
     * clear lines, update score, and spawn the next piece. Returns true if
     * spawning the next piece collides (game over condition).
     */
    public boolean hardDrop() {
        if (currentTetromino == null) {
            return spawnRandomTetromino();
        }

        int ghostY = getGhostY();
        currentTetromino.setPosition(currentTetromino.getX(), ghostY);
        board.lock(currentTetromino);
        soundManager.playHardDrop(); 
        int[] fullLines = board.getFullLines();
        if (fullLines != null && fullLines.length > 0) {
            // Faster, snappier animation when lines are cleared by hard drop
            startLineClearAnimation(fullLines, FAST_LINE_CLEAR_ANIMATION_NS, FAST_LINE_CLEAR_FLASH_PERIOD_NS);
            // Current tetromino is locked on board; clear reference and postpone spawn until animation completes.
            currentTetromino = null;
            return false;
        }

        return spawnRandomTetromino();
    }

    public void moveCurrent(int dx, int dy) {
        if (currentTetromino == null) {
            return;
        }

        int targetX = currentTetromino.getX() + dx;
        int targetY = currentTetromino.getY() + dy;
        if (!board.checkCollision(targetX, targetY, currentTetromino.getShapeRef())) {
            currentTetromino.setPosition(targetX, targetY);
        }
    }

    public void rotateCurrentClockwise() {
        if (currentTetromino == null) {
            return;
        }

        currentTetromino.rotateClockwise();
        if (board.checkCollision(
                currentTetromino.getX(),
                currentTetromino.getY(),
                currentTetromino.getShapeRef()
        )) {
            // Revert if rotation collides.
            currentTetromino.rotateCounterClockwise();
            
        }
        else {
            soundManager.playRotate();
        }

    }

    public boolean holdCurrentPiece() {
        if (currentTetromino == null) {
            return false;
        }
        TetrominoFactory.TetrominoType currentType = tetrominoFactory.typeFromId(currentTetromino.getId());

        // If no piece is held yet, store a copy of the current piece and spawn next immediately.
        if (heldTetromino == null) {
            heldTetromino = tetrominoFactory.createFrom(currentTetromino, 0, 0);
            setHeldTetrominoType(currentType);
            soundManager.playHold(); 
            return spawnRandomTetromino();
        }

        // Prepare swapped candidate (preserve rotation) and a new held copy.
        Tetromino swapped = tetrominoFactory.createFrom(heldTetromino, currentTetromino.getX(), currentTetromino.getY());
        Tetromino newHeld = tetrominoFactory.createFrom(currentTetromino, 0, 0);

        // If placing the swapped piece collides, try simple kicks; if still colliding, cancel swap.
        if (board.checkCollision(swapped.getX(), swapped.getY(), swapped.getShapeRef())) {
            int[] dxs = new int[]{-1, 1, -2, 2};
            boolean placed = false;
            for (int dx : dxs) {
                if (!board.checkCollision(swapped.getX() + dx, swapped.getY(), swapped.getShapeRef())) {
                    swapped.setPosition(swapped.getX() + dx, swapped.getY());
                    placed = true;
                    break;
                }
            }
            if (!placed && !board.checkCollision(swapped.getX(), swapped.getY() - 1, swapped.getShapeRef())) {
                swapped.setPosition(swapped.getX(), swapped.getY() - 1);
                placed = true;
            }

            if (!placed) {
                // Cannot place swapped piece safely — cancel swap and do nothing.
                return false;
            }
        }

        // Start a brief flash animation showing where the swapped piece will land,
        // then commit the swap when the animation completes.
        startSwapAnimation(swapped, newHeld);
        soundManager.playHold();
        return false;
    }

    public GameState getMenuState() {
        return menuState;
    }

    public GameState getPlayingState() {
        return playingState;
    }

    public GameState getPausedState() {
        return pausedState;
    }

    public GameState getGameOverState() {
        return gameOverState;
    }

    public GameState getCurrentState() {
        return currentState;
    }

    public Board getBoard() {
        return board;
    }

    public Tetromino getCurrentTetromino() {
        return currentTetromino;
    }

    public TetrominoFactory.TetrominoType getHeldTetrominoType() {
        return heldTetrominoType;
    }

    public void addHoldPieceListener(HoldPieceListener listener) {
        if (listener != null) {
            holdPieceListeners.add(listener);
            listener.onHoldPieceChanged(heldTetrominoType);
        }
    }

    public void removeHoldPieceListener(HoldPieceListener listener) {
        holdPieceListeners.remove(listener);
    }

    /**
     * Simulates dropping current piece vertically until the next step collides.
     * Returns the deepest valid Y for rendering ghost piece.
     */
    public int getGhostY() {
        if (currentTetromino == null) {
            return -1;
        }

        int targetX = currentTetromino.getX();
        int ghostY = currentTetromino.getY();
        int[][] shape = currentTetromino.getShapeRef();
        while (!board.checkCollision(targetX, ghostY + 1, shape)) {
            ghostY++;
        }

        return ghostY;
    }

    public int getScore() {
        return score;
    }

    public int getTotalClearedLines() {
        return totalClearedLines;
    }

    private boolean spawnRandomTetromino() {
        TetrominoFactory.TetrominoType toSpawn = (nextTetrominoType != null) ? nextTetrominoType : tetrominoFactory.randomType();
        currentTetromino = tetrominoFactory.create(toSpawn);
        // prepare next piece and notify listeners
        nextTetrominoType = tetrominoFactory.randomType();
        
        notifyNextPieceChanged(nextTetrominoType);
        // unlimited holds: no per-turn reset necessary
        if (board.checkCollision(currentTetromino.getX(), currentTetromino.getY(), currentTetromino.getShapeRef())) {
        soundManager.playGameOver();   
        return true;
        }
        return board.checkCollision(currentTetromino.getX(), currentTetromino.getY(), currentTetromino.getShapeRef());
        
    }

    private boolean spawnTetrominoOfType(TetrominoFactory.TetrominoType type) {
        currentTetromino = tetrominoFactory.create(type);
        // unlimited holds: no per-turn reset necessary
        return board.checkCollision(currentTetromino.getX(), currentTetromino.getY(), currentTetromino.getShapeRef());
    }

    private void setHeldTetrominoType(TetrominoFactory.TetrominoType type) {
        heldTetrominoType = type;
        notifyHoldPieceChanged();
    }

    private void notifyHoldPieceChanged() {
        for (HoldPieceListener listener : holdPieceListeners) {
            listener.onHoldPieceChanged(heldTetrominoType);
        }
    }

    private int calculateScore(int clearedLines) {
        switch (clearedLines) {
            case 1:
                return 100;
            case 2:
                return 300;
            case 3:
                return 500;
            case 4:
                return 800;
            default:
                return 0;
        }
    }

    private void startLineClearAnimation(int[] rows) {
        startLineClearAnimation(rows, LINE_CLEAR_ANIMATION_NS, LINE_CLEAR_FLASH_PERIOD_NS);
    }

    private void startLineClearAnimation(int[] rows, long animationNs, long flashPeriodNs) {
        if (rows == null || rows.length == 0) {
            return;
        }
        this.linesToClear = rows.clone();
        this.lineClearColorIndex = random.nextInt(2); // 0 or 1
        this.lineClearElapsedNs = 0L;
        this.lineClearAnimating = true;
        this.currentLineClearAnimationNs = animationNs > 0 ? animationNs : LINE_CLEAR_ANIMATION_NS;
        this.currentLineClearFlashPeriodNs = flashPeriodNs > 0 ? flashPeriodNs : LINE_CLEAR_FLASH_PERIOD_NS;
        board.setPendingClearRows(rows, lineClearColorIndex, currentLineClearFlashPeriodNs);
    }

    private void finalizeLineClearAnimation() {
        if (linesToClear == null || linesToClear.length == 0) {
            lineClearAnimating = false;
            lineClearElapsedNs = 0L;
            return;
        }

        int cleared = linesToClear.length;

        // Remove the rows from the board and clear pending markers
        board.removeLines(linesToClear);
        board.clearPendingClearRows();
//sound
        soundManager.playLineClear();
        // Reset animation state
        lineClearAnimating = false;
        lineClearElapsedNs = 0L;

        // Update score and counters
        totalClearedLines += cleared;
        score += calculateScore(cleared);

        // Spawn next piece and handle game over
        boolean gameOver = spawnRandomTetromino();
        if (gameOver) {
            
            changeState(gameOverState);

        }
    }

    private void startSwapAnimation(Tetromino candidate, Tetromino newHeld) {
        if (candidate == null) return;
        this.swapCandidate = candidate;
        this.swapNewHeld = newHeld;
        this.swapElapsedNs = 0L;
        this.swapAnimating = true;
    }

    private void finalizeSwapAnimation() {
        if (!swapAnimating || swapCandidate == null) {
            swapAnimating = false;
            swapElapsedNs = 0L;
            swapCandidate = null;
            swapNewHeld = null;
            return;
        }

        // Commit the swap: set current tetromino to candidate and update held piece
        currentTetromino = swapCandidate;
        swapCandidate = null;

        if (swapNewHeld != null) {
            this.heldTetromino = swapNewHeld;
            setHeldTetrominoType(tetrominoFactory.typeFromId(heldTetromino.getId()));
            swapNewHeld = null;
        }

        swapAnimating = false;
        swapElapsedNs = 0L;
    }

    public Tetromino getSwapFlashTetromino() {
        return swapAnimating ? swapCandidate : null;
    }

    public boolean isSwapFlashVisible() {
        if (!swapAnimating) return false;
        long period = SWAP_FLASH_PERIOD_NS > 0 ? SWAP_FLASH_PERIOD_NS : LINE_CLEAR_FLASH_PERIOD_NS;
        return ((swapElapsedNs / period) % 2) == 0;
    }
    public SoundManager getSoundManager() {
    return soundManager;
}
}
