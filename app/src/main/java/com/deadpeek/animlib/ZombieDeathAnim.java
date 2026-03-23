package com.deadpeek.animlib;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * ZombieDeathAnim
 * One-shot death animation: the zombie tilts, fades, and drops.
 * When isFinished() returns true, remove the entity from the game.
 *
 * Usage:
 *   ZombieDeathAnim anim = new ZombieDeathAnim(deathSheet, frameCount, frameW, frameH);
 *   anim.start(x, y);
 *   call update(deltaMs) and draw(canvas) each frame until isFinished().
 */
public class ZombieDeathAnim {

    private final Bitmap sheet;
    private final int frameCount;
    private final int frameW;
    private final int frameH;
    private final Paint paint;

    private float x, y;
    private int   currentFrame = 0;
    private float frameTimer   = 0f;
    private float rotation     = 0f;
    private float dropY        = 0f;
    private int   alpha        = 255;
    private boolean started    = false;
    private boolean finished   = false;

    private static final float FRAME_MS  = 80f;
    private static final float ROT_SPEED = 4.5f;   // degrees per frame
    private static final float DROP_SPEED = 3f;    // px per frame
    private static final int   FADE_RATE = 12;     // alpha reduction per frame

    public ZombieDeathAnim(Bitmap deathSheet, int frameCount, int frameW, int frameH) {
        this.sheet      = deathSheet;
        this.frameCount = frameCount;
        this.frameW     = frameW;
        this.frameH     = frameH;
        this.paint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void start(float x, float y) {
        this.x        = x;
        this.y        = y;
        currentFrame  = 0;
        frameTimer    = 0f;
        rotation      = 0f;
        dropY         = 0f;
        alpha         = 255;
        started       = true;
        finished      = false;
    }

    public void update(long deltaMs) {
        if (!started || finished) return;

        frameTimer += deltaMs;
        if (frameTimer >= FRAME_MS) {
            frameTimer -= FRAME_MS;
            currentFrame++;
            if (currentFrame >= frameCount) {
                currentFrame = frameCount - 1;
            }
        }

        rotation += ROT_SPEED;
        dropY    += DROP_SPEED;
        alpha    = Math.max(0, alpha - FADE_RATE);

        if (alpha == 0) finished = true;
    }

    public void draw(Canvas canvas) {
        if (!started || finished || sheet == null || sheet.isRecycled()) return;

        paint.setAlpha(alpha);

        Rect src = new Rect(currentFrame * frameW, 0,
                currentFrame * frameW + frameW, frameH);

        float cx = x + frameW / 2f;
        float cy = y + frameH / 2f + dropY;

        canvas.save();
        canvas.rotate(rotation, cx, cy);
        canvas.drawBitmap(sheet, src,
                new Rect((int)(cx - frameW/2f), (int)(cy - frameH/2f),
                        (int)(cx + frameW/2f), (int)(cy + frameH/2f)), paint);
        canvas.restore();
    }

    public boolean isFinished() { return finished; }
    public boolean isStarted()  { return started; }
}