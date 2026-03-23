package com.deadpeek.animlib;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * PlayerReloadAnim
 * Plays a duck-behind-wall → weapon bob → return sequence.
 * Also draws a reload progress bar on screen.
 *
 * Usage:
 *   PlayerReloadAnim anim = new PlayerReloadAnim(sprite, wallCenterX, screenH, reloadDurationMs);
 *   call start() to begin, update(deltaMs) each frame, draw(canvas).
 *   isComplete() returns true when reload finishes.
 */
public class PlayerReloadAnim {

    public enum Phase { IDLE, DUCKING, RELOADING, RISING }

    private final Bitmap sprite;
    private final int wallCenterX;
    private final int screenH;
    private final long totalDurationMs;
    private final Paint barBgPaint;
    private final Paint barFgPaint;
    private final Paint paint;

    private Phase phase = Phase.IDLE;
    private long elapsed = 0;
    private float duckProgress = 0f;   // 0=visible, 1=fully behind wall
    private float bobOffset    = 0f;

    private static final long DUCK_MS = 200;
    private static final long RISE_MS = 200;

    public PlayerReloadAnim(Bitmap playerSprite, int wallCenterX,
                            int screenH, long reloadDurationMs) {
        this.sprite          = playerSprite;
        this.wallCenterX     = wallCenterX;
        this.screenH         = screenH;
        this.totalDurationMs = reloadDurationMs;

        paint      = new Paint(Paint.ANTI_ALIAS_FLAG);
        barBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barBgPaint.setColor(0x88000000);
        barFgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barFgPaint.setColor(0xFFFFD700); // gold reload bar
    }

    public void start() {
        phase   = Phase.DUCKING;
        elapsed = 0;
    }

    public void update(long deltaMs) {
        if (phase == Phase.IDLE) return;

        elapsed += deltaMs;

        switch (phase) {
            case DUCKING:
                duckProgress = Math.min(1f, (float) elapsed / DUCK_MS);
                if (elapsed >= DUCK_MS) {
                    phase   = Phase.RELOADING;
                    elapsed = 0;
                }
                break;

            case RELOADING:
                // Weapon bob: sine wave on the sprite
                bobOffset = (float) Math.sin(elapsed * 0.012) * 6f;
                if (elapsed >= totalDurationMs) {
                    phase   = Phase.RISING;
                    elapsed = 0;
                    bobOffset = 0;
                }
                break;

            case RISING:
                duckProgress = 1f - Math.min(1f, (float) elapsed / RISE_MS);
                if (elapsed >= RISE_MS) {
                    phase        = Phase.IDLE;
                    duckProgress = 0f;
                }
                break;
        }
    }

    public void draw(Canvas canvas) {
        if (sprite == null || sprite.isRecycled()) return;

        int spriteW = sprite.getWidth();
        int spriteH = sprite.getHeight();

        float baseX = wallCenterX - spriteW / 2f;
        float baseY = screenH - spriteH - 40f;

        // Duck effect: translate the sprite downward off-screen
        float duckShift = duckProgress * (spriteH + 40f);
        float drawX = baseX;
        float drawY = baseY + duckShift + bobOffset;

        canvas.drawBitmap(sprite, drawX, drawY, paint);

        // Reload bar (only shown while actively reloading)
        if (phase == Phase.RELOADING) {
            float barW   = 200f;
            float barH   = 18f;
            float barX   = wallCenterX - barW / 2f;
            float barY   = screenH - 80f;
            float fill   = (float) elapsed / totalDurationMs;

            canvas.drawRoundRect(new RectF(barX, barY, barX + barW, barY + barH),
                    9, 9, barBgPaint);
            canvas.drawRoundRect(new RectF(barX, barY, barX + barW * fill, barY + barH),
                    9, 9, barFgPaint);
        }
    }

    public boolean isComplete()  { return phase == Phase.IDLE && duckProgress == 0f; }
    public boolean isReloading() { return phase != Phase.IDLE; }
    public Phase getPhase()      { return phase; }
    public float getReloadProgress() {
        if (phase == Phase.RELOADING) return (float) elapsed / totalDurationMs;
        return 0f;
    }
}