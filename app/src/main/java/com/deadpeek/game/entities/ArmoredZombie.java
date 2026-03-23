package com.deadpeek.game.entities;

import com.deadpeek.animlib.ZombieDeathAnim;
import com.deadpeek.animlib.ZombieWalkAnim;

/**
 * ArmoredZombie
 * 3× HP of a normal zombie. Moves slightly slower.
 * Helmet / chest-plate should be visible in the sprite.
 */
public class ArmoredZombie extends Zombie {

    public ArmoredZombie(float startX, float y, int lane,
                         ZombieWalkAnim walkAnim, ZombieDeathAnim deathAnim) {
        super(startX, y, lane,
                /* speed */ 1.1f,
                /* hp    */ 9,
                ZombieType.ARMORED,
                walkAnim, deathAnim);
    }

    @Override
    public boolean takeDamage(int dmg) {
        // First 3 points of damage are absorbed by armour (1 dmg instead)
        int effectiveDmg = (currentHp > maxHp - 3) ? 1 : dmg;
        return super.takeDamage(effectiveDmg);
    }
}


// ─────────────────────────────────────────────────────────────────────────────


/**
 * BossZombie (in same file for convenience — split out if you prefer)
 * Appears from level 5 onward. High HP, faster, takes 2× screen rows.
 * Charges when low HP (below 30%).
 */
class BossZombie extends Zombie {

    private boolean charging = false;
    private static final float CHARGE_SPEED = 5f;
    private static final float BASE_SPEED   = 1.8f;

    public BossZombie(float startX, float y, int lane,
                      ZombieWalkAnim walkAnim, ZombieDeathAnim deathAnim) {
        super(startX, y, lane,
                BASE_SPEED,
                /* hp */ 20,
                ZombieType.BOSS,
                walkAnim, deathAnim);
    }

    @Override
    public void update(long deltaMs) {
        // Trigger charge at 30% HP
        if (isAlive() && (float) currentHp / maxHp < 0.3f && !charging) {
            charging = true;
            speed = CHARGE_SPEED;
        }
        super.update(deltaMs);
    }

    public boolean isCharging() { return charging; }
}