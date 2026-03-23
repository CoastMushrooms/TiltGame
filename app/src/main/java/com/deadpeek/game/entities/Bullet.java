package com.deadpeek.game.entities;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Bullet
 * Travels horizontally across the screen.
 * Direction: LEFT (-1) when player peeks left, RIGHT (+1) when peeking right.
 */
public class Bullet {

    private float x, y;
    private final float speed = 28f;  // pixels per frame @ 60fps
    private final int direction;      // -1 or +1
    private boolean active = true;

    private final Paint paint;

    public Bullet(float startX, float startY, int direction) {
        this.x         = startX;
        this.y         = startY;
        this.direction = direction;

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFFFE566);
        paint.setStyle(Paint.Style.FILL);
    }

    public void update(long deltaMs) {
        x += direction * speed * (deltaMs / 16f);
    }

    public void draw(Canvas canvas) {
        if (!active) return;
        canvas.drawOval(new RectF(x - 12, y - 5, x + 12, y + 5), paint);
    }

    /** Deactivate when hitting a zombie or leaving screen bounds */
    public void deactivate() { active = false; }
    public boolean isActive()    { return active; }
    public float   getX()        { return x; }
    public float   getY()        { return y; }

    /** Simple AABB hit-test against a zombie's bounding box */
    public boolean hits(Zombie z) {
        if (!active || !z.isAlive()) return false;
        return x >= z.getX() && x <= z.getX() + z.getWidth()
                && y >= z.getY() && y <= z.getY() + z.getHeight();
    }
}
