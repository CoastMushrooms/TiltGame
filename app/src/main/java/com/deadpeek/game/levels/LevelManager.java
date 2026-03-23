package com.deadpeek.game.levels;

/**
 * LevelManager
 * Returns a LevelConfig for any given level number.
 * Levels 1–6 are hand-crafted; 7+ are generated procedurally.
 */
public class LevelManager {

    private static final LevelConfig[] CONFIGS = {
            null, // index 0 unused

            // Level 1 — tutorial
            new LevelConfig(1,  3,  1, 1.0f, true,  4000L, false, false, 2500,
                    "Learn to peek and shoot"),

            // Level 2 — dual lanes
            new LevelConfig(2,  5,  2, 1.1f, true,  3500L, false, false, 2200,
                    "Two lanes, stay alert"),

            // Level 3 — manual aim introduced
            new LevelConfig(3,  8,  2, 1.3f, false, 3000L, false, false, 1800,
                    "Manual aim — drag to target"),

            // Level 4 — armored zombies appear
            new LevelConfig(4, 10,  3, 1.5f, false, 2500L, true,  false, 1600,
                    "Armored enemies need more hits"),

            // Level 5 — boss + tight reload
            new LevelConfig(5, 12,  3, 1.7f, false, 2000L, true,  true,  1400,
                    "Boss incoming — hold your ground"),

            // Level 6 — wave chaos
            new LevelConfig(6, 16,  3, 2.0f, false, 1500L, true,  true,  1100,
                    "Full chaos — survive as long as you can"),
    };

    public static LevelConfig getConfig(int level) {
        if (level >= 1 && level <= 6) return CONFIGS[level];

        // Procedural: scale difficulty beyond level 6
        int   zombies   = 16 + (level - 6) * 3;
        float speed     = 2.0f + (level - 6) * 0.15f;
        long  reload    = Math.max(1000L, 1500L - (level - 6) * 80L);
        int   spawnMs   = Math.max(700, 1100 - (level - 6) * 50);

        return new LevelConfig(level, zombies, 3, speed, false,
                reload, true, true, spawnMs,
                "Level " + level + " — chaos intensifies");
    }

    public static boolean isLastHandcraftedLevel(int level) { return level == 6; }
}
