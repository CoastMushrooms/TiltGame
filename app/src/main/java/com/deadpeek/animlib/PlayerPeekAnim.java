package com.deadpeek.animlib;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

/**
 * PlayerPeekAnim
 * Slides the player sprite out from behind a wall on the left or right side.
 *
 * Usage:
 *   PlayerPeekAnim anim = new PlayerPeekAnim(playerBitmap, wallX, wallWidth, screenHeight);
 *   // Each frame, call update(tiltValue) then draw(canvas)
 *   // tiltValue: -1.0 = fully left, 0 = hidden, +1.0 = fully right
 */
public class PlayerPeekAnim {

    public enum PeekSide { HIDDEN, LEFT, RIGHT }

    private final Bitmap sprite;
    private final int wallCenterX;
    private final int wallWidth;
    private final int screenH;
    private final Paint paint;

    private float currentOffsetX = 0f;      // how far the player has slid out (pixels)
    private float targetOffsetX  = 0f;
    private PeekSide side = PeekSide.HIDDEN;

    private static final float SLIDE_SPEED  = 18f;  // pixels per frame
    private static final float PEEK_DISTANCE = 120f; // max peek distance in px

    public PlayerPeekAnim(Bitmap playerSprite, int wallCenterX, int wallWidth, int screenH) {
        this.sprite      = playerSprite;
        this.wallCenterX = wallCenterX;
        this.wallWidth   = wallWidth;
        this.screenH     = screenH;
        this.paint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    /**
     * tiltNorm: normalized tilt from -1 (left) to +1 (right).
     * Values between -0.15 and +0.15 are treated as hidden (dead zone).
     */
    public void update(float tiltNorm) {
        if (tiltNorm < -0.15f) {
            side = PeekSide.LEFT;
            targetOffsetX = -PEEK_DISTANCE * Math.min(Math.abs(tiltNorm), 1f);
        } else if (tiltNorm > 0.15f) {
            side = PeekSide.RIGHT;
            targetOffsetX = PEEK_DISTANCE * Math.min(tiltNorm, 1f);
        } else {
            side = PeekSide.HIDDEN;
            targetOffsetX = 0f;
        }

        // Lerp toward target for smooth slide
        float diff = targetOffsetX - currentOffsetX;
        if (Math.abs(diff) < 1f) {
            currentOffsetX = targetOffsetX;
        } else {
            currentOffsetX += diff * 0.18f;
        }
    }

    public void draw(Canvas canvas) {
        if (sprite == null || sprite.isRecycled()) return;

        int spriteW = sprite.getWidth();
        int spriteH = sprite.getHeight();

        // Base position: behind the wall center, vertically near bottom
        float baseX = wallCenterX - spriteW / 2f;
        float baseY = screenH - spriteH - 40f;

        float drawX = baseX + currentOffsetX;
        float drawY = baseY;

        // Flip sprite horizontally when peeking left
        if (side == PeekSide.LEFT) {
            Matrix matrix = new Matrix();
            matrix.postScale(-1, 1, drawX + spriteW / 2f, drawY + spriteH / 2f);
            canvas.save();
            canvas.concat(matrix);
            canvas.drawBitmap(sprite, drawX, drawY, paint);
            canvas.restore();
        } else {
            canvas.drawBitmap(sprite, drawX, drawY, paint);
        }
    }

    public PeekSide getSide()          { return side; }
    public boolean isExposed()         { return side != PeekSide.HIDDEN; }
    public float getCurrentOffsetX()   { return currentOffsetX; }
}