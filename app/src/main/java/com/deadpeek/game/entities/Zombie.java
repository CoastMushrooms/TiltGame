package com.deadpeek.game.entities;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.deadpeek.animlib.ZombieDeathAnim;
import com.deadpeek.animlib.ZombieWalkAnim;

/**
 * Zombie
 * Base class for all zombie types. Subclass for armored / boss variants.
 */
public class Zombie {

    public enum ZombieType { NORMAL, ARMORED, BOSS }
    public enum State { WALKING, DEAD }

    protected float x, y;
    protected float speed;
    protected int   maxHp;
    protected int   currentHp;
    protected int   lane;          // which horizontal row the zombie walks in
    protected ZombieType type;
    protected State state = State.WALKING;

    protected ZombieWalkAnim walkAnim;
    protected ZombieDeathAnim deathAnim;

    private final Paint hpBarBg  = makeBarPaint(0x88000000);
    private final Paint hpBarFg  = makeBarPaint(0xFF44FF44);
    private final Paint hpBarFgA = makeBarPaint(0xFFFF8800); // armored
    private final Paint hpBarFgB = makeBarPaint(0xFFFF2222); // boss

    private static final float DRAW_W = 80f;
    private static final float DRAW_H = 100f;

    public Zombie(float startX, float y, int lane, float speed, int hp, ZombieType type,
                  ZombieWalkAnim walkAnim, ZombieDeathAnim deathAnim) {
        this.x        = startX;
        this.y        = y;
        this.lane     = lane;
        this.speed    = speed;
        this.maxHp    = hp;
        this.currentHp= hp;
        this.type     = type;
        this.walkAnim = walkAnim;
        this.deathAnim= deathAnim;
    }

    public void update(long deltaMs) {
        if (state == State.DEAD) {
            deathAnim.update(deltaMs);
            return;
        }

        float speedMul = 1f + (speed - 1f); // speed is already a multiplier
        x -= speed * (deltaMs / 16f);       // move left per frame

        walkAnim.update(deltaMs, speedMul);
    }

    public void draw(Canvas canvas) {
        if (state == State.DEAD) {
            deathAnim.draw(canvas);
            return;
        }

        walkAnim.draw(canvas, x, y, (int) DRAW_W, (int) DRAW_H);
        drawHpBar(canvas);
    }

    private void drawHpBar(Canvas canvas) {
        float barW = DRAW_W;
        float barH = 6f;
        float barX = x;
        float barY = y - 10f;
        float fill = (float) currentHp / maxHp;

        canvas.drawRoundRect(new RectF(barX, barY, barX + barW, barY + barH), 3, 3, hpBarBg);

        Paint fg = (type == ZombieType.ARMORED) ? hpBarFgA :
                (type == ZombieType.BOSS)    ? hpBarFgB : hpBarFg;
        canvas.drawRoundRect(new RectF(barX, barY, barX + barW * fill, barY + barH), 3, 3, fg);
    }

    public boolean takeDamage(int dmg) {
        if (state == State.DEAD) return false;
        currentHp -= dmg;
        if (currentHp <= 0) {
            die();
            return true;
        }
        return false;
    }

    protected void die() {
        state = State.DEAD;
        currentHp = 0;
        deathAnim.start(x, y);
    }

    public boolean isFullyDead()     { return state == State.DEAD && deathAnim.isFinished(); }
    public boolean isAlive()         { return state == State.WALKING; }
    public boolean hasReachedWall(float wallX) { return x <= wallX && isAlive(); }
    public float getX()              { return x; }
    public float getY()              { return y; }
    public float getWidth()          { return DRAW_W; }
    public float getHeight()         { return DRAW_H; }
    public ZombieType getType()      { return type; }
    public int getLane()             { return lane; }

    private static Paint makeBarPaint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }
}
