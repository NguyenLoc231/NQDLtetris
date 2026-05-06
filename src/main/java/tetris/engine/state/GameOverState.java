package tetris.engine.state;

import tetris.engine.GameAction;
import tetris.engine.GameEngine;
import tetris.engine.GameRenderer;

public class GameOverState implements GameState {
    @Override
    public void enter(GameEngine engine) {
        // No-op.
        engine.getSoundManager().stopBackgroundMusic();
    }

    @Override
    public void exit(GameEngine engine) {
        // No-op.
    }

    @Override
    public void handleInput(GameEngine engine, GameAction action) {
        if (action == GameAction.RESTART) {
            engine.resetGame();
            engine.changeState(engine.getPlayingState());
        } else if (action == GameAction.BACK_TO_MENU) {
            engine.changeState(engine.getMenuState());
        }
    }

    @Override
    public void update(GameEngine engine) {
        // No gameplay update after game over.
    }

    @Override
    public void render(GameEngine engine, GameRenderer renderer) {
        renderer.clear();
        renderer.drawBoard(engine.getBoard(), engine.getCurrentTetromino(), engine.getGhostY(), engine.getSwapFlashTetromino(), engine.isSwapFlashVisible());
        renderer.drawOverlay(engine.getText("gameover.title"), new String[] {
            engine.getText("label.score") + engine.getScore(),
            engine.getText("gameover.instructions")
        });
        renderer.present();
    }

    @Override
    public String name() {
        return "GAME_OVER";
    }
}
