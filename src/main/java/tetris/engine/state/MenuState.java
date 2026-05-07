package tetris.engine.state;

import tetris.engine.GameAction;
import tetris.engine.GameEngine;
import tetris.engine.GameRenderer;

public class MenuState implements GameState {
    @Override
    public void enter(GameEngine engine) {
        engine.getSoundManager().playMenuMusic();
    }

    @Override
    public void exit(GameEngine engine) {
        // No-op.
    }

    @Override
    public void handleInput(GameEngine engine, GameAction action) {
        if (action == GameAction.START) {
            engine.resetGame();
            engine.changeState(engine.getPlayingState());
        }
    }

    @Override
    public void update(GameEngine engine) {
        // No gameplay update in menu.
    }

    @Override
    public void render(GameEngine engine, GameRenderer renderer) {
        renderer.clear();
        renderer.drawBoard(engine.getBoard(), engine.getCurrentTetromino(), engine.getGhostY(), engine.getSwapFlashTetromino(), engine.isSwapFlashVisible());
        renderer.drawOverlay(engine.getText("menu.title"), new String[] { engine.getText("menu.start") });
        renderer.present();
    }

    @Override
    public String name() {
        return "MENU";
    }
}
