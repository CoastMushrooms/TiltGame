package com.deadpeek.game;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

/**
 * GameThread
 * Drives the core game loop at a locked 60 fps.
 * Calls GameView.update() then GameView.draw() on each tick.
 * Properly pauses/resumes to avoid freeze-on-minimize bugs.
 */
public class GameThread extends Thread {

    private static final int TARGET_FPS   = 60;
    private static final long FRAME_TICKS = 1_000_000_000L / TARGET_FPS; // nanoseconds

    private final SurfaceHolder holder;
    private final GameView gameView;
    private volatile boolean running = false;

    public GameThread(SurfaceHolder holder, GameView gameView) {
        super("GameThread");
        this.holder   = holder;
        this.gameView = gameView;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    @Override
    public void run() {
        long previousTime = System.nanoTime();
        long lagNs        = 0;

        while (running) {
            long currentTime  = System.nanoTime();
            long elapsedNs    = currentTime - previousTime;
            previousTime      = currentTime;
            lagNs            += elapsedNs;

            // Update game logic (catch up if behind)
            while (lagNs >= FRAME_TICKS) {
                gameView.update(FRAME_TICKS / 1_000_000L); // pass millis
                lagNs -= FRAME_TICKS;
            }

            // Draw
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    synchronized (holder) {
                        gameView.draw(canvas);
                    }
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            // Sleep remainder of frame to avoid burning CPU
            long sleepNs = FRAME_TICKS - (System.nanoTime() - currentTime);
            if (sleepNs > 0) {
                try {
                    Thread.sleep(sleepNs / 1_000_000L, (int)(sleepNs % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
