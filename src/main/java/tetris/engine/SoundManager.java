package tetris.engine;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class SoundManager {
    // Pool cho các âm thanh có thể phát liên tục
    private final List<AudioClip> rotateClips = new ArrayList<>();
    private final List<AudioClip> hardDropClips = new ArrayList<>();
    private int rotateIndex = 0;
    private int hardDropIndex = 0;

    // Các âm thanh khác chỉ cần 1 instance
    private AudioClip lineClearSound;
    private AudioClip gameOverSound;
    private AudioClip holdSound;

    private MediaPlayer backgroundMusic;
    private boolean soundEnabled = true;

    private static final int POOL_SIZE = 3;   // Số lượng bản sao cho mỗi âm thanh

    public SoundManager() {
        try {
            // Tạo pool cho rotate
            for (int i = 0; i < POOL_SIZE; i++) {
                rotateClips.add(loadAudioClip("/sounds/rotation.wav"));
            }
            // Tạo pool cho hard drop
            for (int i = 0; i < POOL_SIZE; i++) {
                hardDropClips.add(loadAudioClip("/sounds/touch floor.wav"));
            }

            lineClearSound = loadAudioClip("/sounds/delete line.wav");
            gameOverSound  = loadAudioClip("/sounds/gameover.wav");
            holdSound      = loadAudioClip("/sounds/rotation.wav");  // Dùng chung file với rotate nếu muốn, hoặc đổi sang file riêng
        } catch (Exception e) {
            System.err.println("Cannot load sound effects: " + e.getMessage());
            soundEnabled = false;
        }

        if (soundEnabled) {
            System.out.println("SoundManager: All sounds loaded successfully.");
        }

        // Nhạc nền
        try {
            URL musicUrl = getClass().getResource("/sounds/backgroundmusic.wav");
            if (musicUrl != null) {
                Media media = new Media(musicUrl.toExternalForm());
                backgroundMusic = new MediaPlayer(media);
                backgroundMusic.setCycleCount(MediaPlayer.INDEFINITE);
                backgroundMusic.setVolume(0.3);
                System.out.println("Background music loaded.");
            }
        } catch (Exception e) {
            System.err.println("Cannot load background music: " + e.getMessage());
        }
    }

    private AudioClip loadAudioClip(String path) {
        URL url = getClass().getResource(path);
        if (url == null) {
            throw new RuntimeException("File not found: " + path);
        }
        return new AudioClip(url.toExternalForm());
    }

    // Phát âm thanh dùng pool (xoay vòng)
    public void playRotate() {
        playFromPool(rotateClips, rotateIndex);
        rotateIndex = (rotateIndex + 1) % rotateClips.size();
    }

    public void playHardDrop() {
        playFromPool(hardDropClips, hardDropIndex);
        hardDropIndex = (hardDropIndex + 1) % hardDropClips.size();
    }

    private void playFromPool(List<AudioClip> pool, int index) {
        if (!soundEnabled || pool.isEmpty()) return;
        AudioClip clip = pool.get(index);
        if (clip != null) {
            clip.play();
        }
    }

    // Các âm thanh không cần pool
    public void playLineClear() { playIf(lineClearSound); }
    public void playGameOver()  { playIf(gameOverSound); }
    public void playHold()      { playIf(holdSound); }

    private void playIf(AudioClip clip) {
        if (clip != null && soundEnabled) {
            clip.play();
        }
    }

    // Nhạc nền
    public void startBackgroundMusic() {
        if (backgroundMusic != null && soundEnabled) {
            backgroundMusic.play();
        }
    }

    public void stopBackgroundMusic() {
        if (backgroundMusic != null) backgroundMusic.stop();
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
        if (!enabled && backgroundMusic != null) {
            backgroundMusic.stop();
        }
    }
    public void playMove() {}
}