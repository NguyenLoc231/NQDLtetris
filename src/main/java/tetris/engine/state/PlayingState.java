package tetris.engine.state;

import tetris.engine.GameAction;
import tetris.engine.GameEngine;
import tetris.engine.GameRenderer;

public class PlayingState implements GameState {
    @Override
    public void enter(GameEngine engine) {
        // Spawn first piece through update loop.
        engine.getSoundManager().startBackgroundMusic();
    }

    @Override
    public void exit(GameEngine engine) {
        // No-op.
        engine.getSoundManager().stopBackgroundMusic();
    }

    @Override
    public void handleInput(GameEngine engine, GameAction action) {
        switch (action) {
            case LEFT:
                engine.moveCurrent(-1, 0);
                break;
            case RIGHT:
                engine.moveCurrent(1, 0);
                break;
            case DOWN:
                engine.moveCurrent(0, 1);
                break;
            case ROTATE:
                engine.rotateCurrentClockwise();
                break;
            case HARD_DROP:
                boolean gameOver = engine.hardDrop();
                if (gameOver) {
                    engine.changeState(engine.getGameOverState());
                }
                break;
            case HOLD:
                if (engine.holdCurrentPiece()) {
                    engine.changeState(engine.getGameOverState());
                }
                break;
            case PAUSE:
                engine.changeState(engine.getPausedState());
                break;
            case BACK_TO_MENU:
                engine.changeState(engine.getMenuState());
                break;
            default:
                // Ignore unsupported commands in playing state.
                break;
        }
    }

    @Override
    public void update(GameEngine engine) {
        boolean gameOver = engine.stepDown();
        if (gameOver) {
            engine.changeState(engine.getGameOverState());
        }
    }

    @Override
    public void render(GameEngine engine, GameRenderer renderer) {
        renderer.clear();
        renderer.drawBoard(engine.getBoard(), engine.getCurrentTetromino(), engine.getGhostY(), engine.getSwapFlashTetromino(), engine.isSwapFlashVisible());
        renderer.drawText(engine.getText("label.score") + engine.getScore(), 0, 21);
        renderer.drawText(engine.getText("label.lines") + engine.getTotalClearedLines(), 0, 22);
        renderer.present();
    }

    @Override
    public String name() {
        return "PLAYING";
    }
}
