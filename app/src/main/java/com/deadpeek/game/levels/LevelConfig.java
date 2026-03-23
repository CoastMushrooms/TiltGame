package com.deadpeek.game.levels;

/**
 * LevelConfig
 * Immutable snapshot of one level's parameters.
 */
public class LevelConfig {

    public final int    level;
    public final int    zombieCount;
    public final int    laneCount;
    public final float  zombieBaseSpeed;
    public final boolean autoAim;
    public final long   reloadDurationMs;
    public final boolean hasArmoredZombies;
    public final boolean hasBoss;
    public final int    spawnIntervalMs;   // ms between zombie spawns
    public final String description;

    public LevelConfig(int level, int zombieCount, int laneCount,
                       float zombieBaseSpeed, boolean autoAim,
                       long reloadDurationMs, boolean hasArmoredZombies,
                       boolean hasBoss, int spawnIntervalMs, String description) {
        this.level             = level;
        this.zombieCount       = zombieCount;
        this.laneCount         = laneCount;
        this.zombieBaseSpeed   = zombieBaseSpeed;
        this.autoAim           = autoAim;
        this.reloadDurationMs  = reloadDurationMs;
        this.hasArmoredZombies = hasArmoredZombies;
        this.hasBoss           = hasBoss;
        this.spawnIntervalMs   = spawnIntervalMs;
        this.description       = description;
    }
}


// ─────────────────────────────────────────────────────────────────────────────


