package com.deadpeek.game.sound;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;

import com.deadpeek.game.R;

/**
 * SoundManager
 * SoundPool handles all low-latency SFX.
 * MediaPlayer handles two background music tracks with crossfade.
 *
 * Call init() once. Call release() in onDestroy.
 */
public class SoundManager {

    // SFX IDs
    public static final int SFX_GUNSHOT     = 0;
    public static final int SFX_EMPTY_CLICK = 1;
    public static final int SFX_RELOAD_DONE = 2;
    public static final int SFX_ZOMBIE_GROWL= 3;

    private SoundPool soundPool;
    private final int[] soundIds = new int[4];
    private boolean loaded = false;

    private MediaPlayer musicPlayerA;
    private MediaPlayer musicPlayerB;
    private boolean isPlayingA = true;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public SoundManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void init() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(attrs)
                .build();

        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            // All 4 sounds must load before we mark ready
            boolean allLoaded = true;
            for (int id : soundIds) if (id == 0) { allLoaded = false; break; }
            if (allLoaded) loaded = true;
        });

        // Load SFX from res/raw — add your files as:
        //   res/raw/sfx_gunshot.ogg
        //   res/raw/sfx_empty_click.ogg
        //   res/raw/sfx_reload_done.ogg
        //   res/raw/sfx_zombie_growl.ogg
        soundIds[SFX_GUNSHOT]      = soundPool.load(context, R.raw.sfx_gunshot,      1);
        soundIds[SFX_EMPTY_CLICK]  = soundPool.load(context, R.raw.sfx_empty_click,  1);
        soundIds[SFX_RELOAD_DONE]  = soundPool.load(context, R.raw.sfx_reload_done,  1);
        soundIds[SFX_ZOMBIE_GROWL] = soundPool.load(context, R.raw.sfx_zombie_growl, 1);

        // Start with track A (calm, levels 1–3)
        musicPlayerA = MediaPlayer.create(context, R.raw.music_calm);
        musicPlayerA.setLooping(true);
        musicPlayerA.setVolume(0.6f, 0.6f);
        musicPlayerA.start();

        musicPlayerB = MediaPlayer.create(context, R.raw.music_intense);
        musicPlayerB.setLooping(true);
        musicPlayerB.setVolume(0f, 0f);
    }

    /** Play a SFX by constant (e.g. SFX_GUNSHOT). */
    public void playSfx(int sfxId) {
        if (!loaded || sfxId < 0 || sfxId >= soundIds.length) return;
        soundPool.play(soundIds[sfxId], 1f, 1f, 1, 0, 1f);
    }

    /**
     * Crossfade to the intense track (call when reaching level 4+).
     * Fades over ~1500ms to avoid jarring jump.
     */
    public void crossfadeToIntense() {
        if (!isPlayingA) return;
        isPlayingA = false;
        musicPlayerB.start();
        crossfade(musicPlayerA, musicPlayerB, 1500, 0);
    }

    public void crossfadeToCalm() {
        if (isPlayingA) return;
        isPlayingA = true;
        musicPlayerA.start();
        crossfade(musicPlayerB, musicPlayerA, 1500, 0);
    }

    private void crossfade(MediaPlayer from, MediaPlayer to, int durationMs, int elapsed) {
        float progress = Math.min(1f, (float) elapsed / durationMs);
        from.setVolume(0.6f * (1f - progress), 0.6f * (1f - progress));
        to.setVolume(0.6f * progress, 0.6f * progress);

        if (elapsed < durationMs) {
            handler.postDelayed(() ->
                    crossfade(from, to, durationMs, elapsed + 50), 50);
        } else {
            from.pause();
            from.seekTo(0);
        }
    }

    public void pause() {
        if (musicPlayerA != null && musicPlayerA.isPlaying()) musicPlayerA.pause();
        if (musicPlayerB != null && musicPlayerB.isPlaying()) musicPlayerB.pause();
    }

    public void resume() {
        if (isPlayingA && musicPlayerA != null) musicPlayerA.start();
        else if (!isPlayingA && musicPlayerB != null) musicPlayerB.start();
    }

    public void release() {
        if (soundPool != null) { soundPool.release(); soundPool = null; }
        if (musicPlayerA != null) { musicPlayerA.release(); musicPlayerA = null; }
        if (musicPlayerB != null) { musicPlayerB.release(); musicPlayerB = null; }
    }
}