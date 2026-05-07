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

    private boolean soundEnabled = true;

    private static final int POOL_SIZE = 3;   // Số lượng bản sao cho mỗi âm thanh

    // Background music tracks
    private MediaPlayer menuMusic;
    private MediaPlayer gameplayLowMusic;   // lv1-6 (loop)
    private MediaPlayer gameplayHighP1;     // lv7-10 part 1 (no loop -> auto to p2)
    private MediaPlayer gameplayHighP2;     // lv7-10 part 2 (loop)

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
            holdSound      = loadAudioClip("/sounds/rotation.wav");
        } catch (Exception e) {
            System.err.println("Cannot load sound effects: " + e.getMessage());
            soundEnabled = false;
        }

        if (soundEnabled) {
            System.out.println("SoundManager: All sounds loaded successfully.");
        }

        // Nhạc nền menu và màn hình phụ
        menuMusic = createMediaPlayer("/sounds/backgroundmusic.wav", true);
        // Nhạc nền gameplay lv1-6
        gameplayLowMusic = createMediaPlayer("/sounds/backgroundmusic_lv1_lv6.wav", true);
        // Nhạc nền gameplay lv7-10 (2 phần)
        gameplayHighP1 = createMediaPlayer("/sounds/backgroundmusic_lv7_lv10_p1.wav", false);
        gameplayHighP2 = createMediaPlayer("/sounds/backgroundmusic_lv7_lv10_p2.wav", true);

        // Khi p1 phát xong -> tự động chuyển sang p2
        if (gameplayHighP1 != null) {
            gameplayHighP1.setOnEndOfMedia(() -> {
                if (soundEnabled && gameplayHighP1 != null) {
                    gameplayHighP1.stop();
                    gameplayHighP1.seek(javafx.util.Duration.ZERO);
                }
                if (gameplayHighP2 != null && soundEnabled) {
                    gameplayHighP2.seek(javafx.util.Duration.ZERO);
                    gameplayHighP2.play();
                }
            });
        }
    }

    private MediaPlayer createMediaPlayer(String path, boolean loop) {
        try {
            URL url = getClass().getResource(path);
            if (url == null) {
                System.err.println("Music file not found: " + path);
                return null;
            }
            Media media = new Media(url.toExternalForm());
            MediaPlayer player = new MediaPlayer(media);
            if (loop) {
                player.setCycleCount(MediaPlayer.INDEFINITE);
            }
            player.setVolume(0.3);
            System.out.println("Loaded music: " + path);
            return player;
        } catch (Exception e) {
            System.err.println("Cannot load music: " + path + " - " + e.getMessage());
            return null;
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

    /**
     * Phát nhạc nền cho menu / paused / game over / overlay screens.
     */
    public void playMenuMusic() {
        stopAllBackgroundMusic();
        if (menuMusic != null && soundEnabled) {
            menuMusic.seek(javafx.util.Duration.ZERO);
            menuMusic.play();
        }
    }

    /**
     * Phát nhạc nền gameplay dựa trên level.
     * - lv 1-6: backgroundmusic_lv1_lv6.wav (loop)
     * - lv 7-10: backgroundmusic_lv7_lv10_p1.wav (no loop) -> auto p2 (loop)
     */
    public void playGameplayMusic(int level) {
        stopAllBackgroundMusic();
        if (!soundEnabled) return;

        if (level >= 1 && level <= 6) {
            if (gameplayLowMusic != null) {
                gameplayLowMusic.seek(javafx.util.Duration.ZERO);
                gameplayLowMusic.play();
            }
        } else if (level >= 7 && level <= 10) {
            if (gameplayHighP1 != null) {
                gameplayHighP1.seek(javafx.util.Duration.ZERO);
                gameplayHighP1.play();
            }
        }
    }

    /**
     * Dừng toàn bộ nhạc nền.
     */
    public void stopAllBackgroundMusic() {
        if (menuMusic != null) menuMusic.stop();
        if (gameplayLowMusic != null) gameplayLowMusic.stop();
        if (gameplayHighP1 != null) gameplayHighP1.stop();
        if (gameplayHighP2 != null) gameplayHighP2.stop();
    }

    // Legacy API compatibility
    public void startBackgroundMusic() {
        playMenuMusic();
    }

    public void stopBackgroundMusic() {
        stopAllBackgroundMusic();
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
        if (!enabled) {
            stopAllBackgroundMusic();
        }
    }

    public void playMove() {}
}