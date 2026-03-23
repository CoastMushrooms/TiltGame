package com.deadpeek.animlib;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * ZombieWalkAnim
 * Frame-by-frame walk cycle from a horizontal sprite sheet.
 * Speed scales with the zombie's movement speed multiplier.
 *
 * Usage:
 *   ZombieWalkAnim anim = new ZombieWalkAnim(spriteSheet, frameCount, frameW, frameH);
 *   call update(deltaMs, speedMultiplier) and draw(canvas, x, y) each frame.
 */
public class ZombieWalkAnim {

    private final Bitmap sheet;
    private final int frameCount;
    private final int frameW;
    private final int frameH;
    private final Paint paint;

    private int   currentFrame  = 0;
    private float frameTimer    = 0f;
    private float baseFrameMs   = 120f; // ms per frame at speed 1.0

    public ZombieWalkAnim(Bitmap spriteSheet, int frameCount, int frameW, int frameH) {
        this.sheet      = spriteSheet;
        this.frameCount = frameCount;
        this.frameW     = frameW;
        this.frameH     = frameH;
        this.paint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    /**
     * @param deltaMs        milliseconds since last update
     * @param speedMultiplier 1.0 = normal, 2.0 = twice as fast, etc.
     */
    public void update(long deltaMs, float speedMultiplier) {
        float frameDuration = baseFrameMs / Math.max(speedMultiplier, 0.1f);
        frameTimer += deltaMs;
        if (frameTimer >= frameDuration) {
            frameTimer -= frameDuration;
            currentFrame = (currentFrame + 1) % frameCount;
        }
    }

    public void draw(Canvas canvas, float x, float y) {
        if (sheet == null || sheet.isRecycled()) return;

        Rect src = new Rect(currentFrame * frameW, 0,
                currentFrame * frameW + frameW, frameH);
        Rect dst = new Rect((int) x, (int) y,
                (int) x + frameW, (int) y + frameH);

        canvas.drawBitmap(sheet, src, dst, paint);
    }

    /** Draw with custom destination size */
    public void draw(Canvas canvas, float x, float y, int drawW, int drawH) {
        if (sheet == null || sheet.isRecycled()) return;

        Rect src = new Rect(currentFrame * frameW, 0,
                currentFrame * frameW + frameW, frameH);
        Rect dst = new Rect((int) x, (int) y,
                (int) x + drawW, (int) y + drawH);

        canvas.drawBitmap(sheet, src, dst, paint);
    }

    public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    public int getCurrentFrame()    { return currentFrame; }
    public void reset()             { currentFrame = 0; frameTimer = 0f; }
}