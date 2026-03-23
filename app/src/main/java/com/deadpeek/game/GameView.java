package com.deadpeek.game;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.deadpeek.animlib.*;
import com.deadpeek.game.entities.*;
import com.deadpeek.game.levels.LevelConfig;
import com.deadpeek.game.levels.LevelManager;
import com.deadpeek.game.sound.SoundManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * GameView — master SurfaceView for Dead Peek.
 *
 * ORIENTATION MODEL:
 *   Portrait  = player hidden behind wall. Safe zone. Can reload here.
 *   Tilt left = player peeks out left side. Zombies approach from far left.
 *   Tilt right= player peeks out right side. Zombies approach from far right.
 *
 * peekAmount (0→1) drives how much of the battlefield is revealed.
 * At peekAmount=0 only the wall is visible. At 1 the full side-scrolling
 * battlefield is on screen.
 *
 * GAME STATES:
 *   BRIEFING  → per-level intro card (tap to start)
 *   RUNNING   → active gameplay
 *   LEVEL_CLEAR → wave cleared, tap to next level
 *   GAME_OVER → lives depleted, tap to retry
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    // ── States ────────────────────────────────────────────────────────────────
    private enum GameState { BRIEFING, RUNNING, LEVEL_CLEAR, GAME_OVER }
    private GameState gameState = GameState.BRIEFING;

    // ── Systems ───────────────────────────────────────────────────────────────
    private GameThread     gameThread;
    private TiltController tilt;
    private SoundManager   sound;

    // ── Level ─────────────────────────────────────────────────────────────────
    private int         currentLevel   = 1;
    private LevelConfig levelCfg;
    private int         zombiesSpawned = 0;
    private int         zombiesKilled  = 0;
    private long        spawnTimer     = 0;

    // ── Player state ──────────────────────────────────────────────────────────
    private int     ammo      = 10;
    private int     maxAmmo   = 10;
    private boolean reloading = false;
    private long    reloadElapsed = 0;
    private int     score     = 0;
    private int     lives     = 3;

    // ── Manual aim ────────────────────────────────────────────────────────────
    private boolean aimActive = false;
    private float   aimX = 0f, aimY = 0f;

    // ── Entities ──────────────────────────────────────────────────────────────
    private final List<Zombie> zombies = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();

    // ── Screen dimensions ─────────────────────────────────────────────────────
    private int screenW, screenH;

    // Wall geometry (portrait view)
    // The wall is drawn as a tall concrete slab filling most of portrait width.
    private int wallLeft, wallRight; // X bounds of the wall face
    private int wallTop = 0, wallBottom;

    // ── Bitmaps ───────────────────────────────────────────────────────────────
    private Bitmap playerBmp;
    private Bitmap zombieSheet;
    private Bitmap zombieDeathSheet;
    private Bitmap armoredSheet;
    private Bitmap bgBmp;
    private Bitmap wallBmp;

    // ── Paints ────────────────────────────────────────────────────────────────
    private Paint bgPaint, wallPaint, wallEdgePaint;
    private Paint hudPaint, hudSmallPaint, hudValuePaint;
    private Paint reticlePaint;
    private Paint overlayPaint;
    private Paint briefingBgPaint;
    private Paint titlePaint, bodyPaint, subPaint, accentPaint, dimPaint;
    private Paint reloadBarBg, reloadBarFg;
    private Paint ammoBoxPaint, ammoBoxEmpty;
    private Paint peekIndicatorPaint;

    // ── Briefing animation ────────────────────────────────────────────────────
    private long  briefingFadeMs  = 0;
    private static final long BRIEFING_FADE_DURATION = 400;

    public GameView(Context context) { this(context, null); }
    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
        setFocusable(true);
        initPaints();
        tilt  = new TiltController(context);
        sound = new SoundManager(context);
        sound.init();
    }

    // ─── SurfaceHolder.Callback ───────────────────────────────────────────────

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        screenW     = getWidth();
        screenH     = getHeight();
        wallLeft    = screenW / 2 - screenW / 5;
        wallRight   = screenW / 2 + screenW / 5;
        wallBottom  = screenH;

        loadBitmaps();
        loadLevel(currentLevel);

        gameThread = new GameThread(holder, this);
        gameThread.setRunning(true);
        gameThread.start();
        tilt.register();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int fmt, int w, int h) {
        screenW    = w; screenH    = h;
        wallLeft   = w / 2 - w / 5;
        wallRight  = w / 2 + w / 5;
        wallBottom = h;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        tilt.unregister();
        gameThread.setRunning(false);
        boolean retry = true;
        while (retry) {
            try { gameThread.join(); retry = false; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    // ─── Level loading ────────────────────────────────────────────────────────

    private void loadLevel(int level) {
        levelCfg       = LevelManager.getConfig(level);
        zombiesSpawned = 0;
        zombiesKilled  = 0;
        spawnTimer     = 0;
        ammo           = maxAmmo;
        reloading      = false;
        reloadElapsed  = 0;
        zombies.clear();
        bullets.clear();
        gameState      = GameState.BRIEFING;
        briefingFadeMs = 0;

        if (level >= 4) sound.crossfadeToIntense();
        else            sound.crossfadeToCalm();
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    public void update(long deltaMs) {
        if (gameState == GameState.BRIEFING) {
            briefingFadeMs += deltaMs;
            return;
        }
        if (gameState != GameState.RUNNING) return;

        float peekAmt  = tilt.getPeekAmount();
        TiltController.PeekSide peekSide = tilt.getSide();
        boolean exposed = peekAmt > 0.15f;

        // ── Reload (only allowed while hidden behind wall) ────────────────────
        if (reloading) {
            if (!tilt.isHidden()) {
                // Interrupted — peeking out cancels reload
                reloading     = false;
                reloadElapsed = 0;
            } else {
                reloadElapsed += deltaMs;
                if (reloadElapsed >= levelCfg.reloadDurationMs) {
                    reloading     = false;
                    reloadElapsed = 0;
                    ammo          = maxAmmo;
                    sound.playSfx(SoundManager.SFX_RELOAD_DONE);
                }
            }
        }

        // ── Zombie spawning ───────────────────────────────────────────────────
        if (zombiesSpawned < levelCfg.zombieCount) {
            spawnTimer += deltaMs;
            if (spawnTimer >= levelCfg.spawnIntervalMs) {
                spawnTimer = 0;
                spawnZombie();
            }
        }

        // ── Bullets ───────────────────────────────────────────────────────────
        Iterator<Bullet> bi = bullets.iterator();
        while (bi.hasNext()) {
            Bullet b = bi.next();
            b.update(deltaMs);
            if (b.getX() < -100 || b.getX() > screenW + 100) {
                b.deactivate(); bi.remove(); continue;
            }
            for (Zombie z : zombies) {
                if (b.hits(z)) {
                    boolean killed = z.takeDamage(1);
                    b.deactivate();
                    if (killed) {
                        zombiesKilled++;
                        score += scoreForZombie(z);
                        sound.playSfx(SoundManager.SFX_ZOMBIE_GROWL);
                    }
                    break;
                }
            }
            if (!b.isActive()) bi.remove();
        }

        // ── Zombies ───────────────────────────────────────────────────────────
        // Wall X from which zombies approach (center of screen in game coords)
        float wallX = screenW / 2f;

        Iterator<Zombie> zi = zombies.iterator();
        while (zi.hasNext()) {
            Zombie z = zi.next();
            z.update(deltaMs);
            if (z.isFullyDead()) { zi.remove(); continue; }

            // Only the zombie on the peeked side can reach/attack
            boolean onActiveSide =
                    (z.getLane() >= 0 && peekSide == TiltController.PeekSide.LEFT  && z.getX() > 0) ||
                            (z.getLane() >= 0 && peekSide == TiltController.PeekSide.RIGHT && z.getX() < screenW);

            if (z.hasReachedWall(wallX) && z.isAlive()) {
                z.takeDamage(999);
                lives--;
                sound.playSfx(SoundManager.SFX_ZOMBIE_GROWL);
                if (lives <= 0) { gameState = GameState.GAME_OVER; return; }
            }
        }

        // ── Level clear ───────────────────────────────────────────────────────
        if (zombiesSpawned >= levelCfg.zombieCount
                && zombies.isEmpty()
                && gameState == GameState.RUNNING) {
            gameState = GameState.LEVEL_CLEAR;
        }
    }

    // ─── Draw ─────────────────────────────────────────────────────────────────

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (canvas == null) return;

        float peek = tilt.getPeekAmount();
        TiltController.PeekSide side = tilt.getSide();

        // ── Background ────────────────────────────────────────────────────────
        drawBackground(canvas, peek, side);

        // ── Battlefield (only visible while peeking) ──────────────────────────
        if (peek > 0.02f) {
            drawBattlefield(canvas, peek, side);
        }

        // ── Wall ──────────────────────────────────────────────────────────────
        drawWall(canvas, peek, side);

        // ── Player ────────────────────────────────────────────────────────────
        drawPlayer(canvas, peek, side);

        // ── HUD ───────────────────────────────────────────────────────────────
        if (gameState == GameState.RUNNING || gameState == GameState.LEVEL_CLEAR
                || gameState == GameState.GAME_OVER) {
            drawHUD(canvas, peek);
        }

        // ── Reload bar ────────────────────────────────────────────────────────
        if (reloading) drawReloadBar(canvas);

        // ── Overlays ──────────────────────────────────────────────────────────
        if (gameState == GameState.BRIEFING)    drawBriefing(canvas);
        if (gameState == GameState.LEVEL_CLEAR) drawLevelClear(canvas);
        if (gameState == GameState.GAME_OVER)   drawGameOver(canvas);
    }

    // ── Background ────────────────────────────────────────────────────────────

    private void drawBackground(Canvas canvas, float peek, TiltController.PeekSide side) {
        // Sky / street background — always draw
        if (bgBmp != null && !bgBmp.isRecycled()) {
            // Scale bg to fill screen
            Rect dst = new Rect(0, 0, screenW, screenH);
            canvas.drawBitmap(bgBmp, null, dst, null);
        } else {
            // Fallback gradient sky
            canvas.drawRect(0, 0, screenW, screenH * 0.55f, makeColorPaint(0xFF0D1B2A));
            canvas.drawRect(0, screenH * 0.55f, screenW, screenH, makeColorPaint(0xFF1A1200));
        }
    }

    // ── Battlefield ───────────────────────────────────────────────────────────

    private void drawBattlefield(Canvas canvas, float peek, TiltController.PeekSide side) {
        // Clip to the side that's visible
        canvas.save();

        // Reveal from left or right edge based on peek side + amount
        float revealW = screenW * peek;
        if (side == TiltController.PeekSide.LEFT) {
            canvas.clipRect(0, 0, revealW, screenH);
        } else {
            canvas.clipRect(screenW - revealW, 0, screenW, screenH);
        }

        // Ground
        Paint gnd = makeColorPaint(0xFF1C1400);
        canvas.drawRect(0, screenH * 0.65f, screenW, screenH, gnd);
        // Road marking
        Paint road = makeColorPaint(0xFF2A2200);
        canvas.drawRect(0, screenH * 0.72f, screenW, screenH * 0.78f, road);

        // Draw zombies on this side
        for (Zombie z : zombies) {
            // Scale zombie with perspective: closer to wall = bigger
            float zx = z.getX();
            float distRatio; // 0 = far, 1 = at wall
            if (side == TiltController.PeekSide.LEFT) {
                distRatio = 1f - (zx / (float) screenW);
            } else {
                distRatio = zx / (float) screenW;
            }
            distRatio = Math.max(0f, Math.min(1f, distRatio));
            float scale = 0.4f + distRatio * 0.6f; // 40% when far, 100% when close

            canvas.save();
            float drawW = z.getWidth() * scale;
            float drawH = z.getHeight() * scale;
            float drawY = screenH * 0.65f - drawH + (distRatio * drawH * 0.3f);
            canvas.translate(zx - drawW / 2f, drawY);
            canvas.scale(scale, scale);
            z.draw(canvas);
            canvas.restore();
        }

        // Draw bullets
        for (Bullet b : bullets) b.draw(canvas);

        // Aim reticle (manual aim levels)
        if (!levelCfg.autoAim && aimActive && peek > 0.5f) {
            canvas.drawCircle(aimX, aimY, 28, reticlePaint);
            canvas.drawLine(aimX - 38, aimY, aimX + 38, aimY, reticlePaint);
            canvas.drawLine(aimX, aimY - 38, aimX, aimY + 38, reticlePaint);
        }

        canvas.restore();
    }

    // ── Wall ──────────────────────────────────────────────────────────────────

    private void drawWall(Canvas canvas, float peek, TiltController.PeekSide side) {
        // As the player tilts, the wall slides left or right to reveal the side
        float wallShift = peek * (screenW * 0.45f);
        float wL, wR;

        if (side == TiltController.PeekSide.LEFT || side == TiltController.PeekSide.HIDDEN) {
            wL = wallLeft  - wallShift;
            wR = wallRight - wallShift;
        } else {
            wL = wallLeft  + wallShift;
            wR = wallRight + wallShift;
        }

        // Wall body
        if (wallBmp != null && !wallBmp.isRecycled()) {
            Rect dst = new Rect((int)wL, 0, (int)wR, screenH);
            canvas.drawBitmap(wallBmp, null, dst, null);
        } else {
            canvas.drawRect(wL, 0, wR, screenH, wallPaint);
            // Brick lines
            for (int row = 0; row < screenH; row += 60) {
                int offset = (row / 60 % 2 == 0) ? 0 : 40;
                for (float col = wL - 10 + offset; col < wR + 10; col += 80) {
                    canvas.drawLine(col, row, col, row + 58, wallEdgePaint);
                }
                canvas.drawLine(wL, row, wR, row, wallEdgePaint);
            }
        }

        // Edge shadows
        Paint shadowL = new Paint();
        shadowL.setShader(new LinearGradient(wL - 30, 0, wL + 10, 0,
                0x88000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(wL - 30, 0, wL + 10, screenH, shadowL);

        Paint shadowR = new Paint();
        shadowR.setShader(new LinearGradient(wR - 10, 0, wR + 30, 0,
                0x00000000, 0x88000000, Shader.TileMode.CLAMP));
        canvas.drawRect(wR - 10, 0, wR + 30, screenH, shadowR);
    }

    // ── Player ────────────────────────────────────────────────────────────────

    private void drawPlayer(Canvas canvas, float peek, TiltController.PeekSide side) {
        if (playerBmp == null || playerBmp.isRecycled()) return;

        int pw = playerBmp.getWidth();
        int ph = playerBmp.getHeight();

        // Player stands at bottom-center, behind wall
        // As peek increases, slides out to the appropriate side
        float baseX = screenW / 2f - pw / 2f;
        float baseY = screenH - ph - 60f;

        float slideX = peek * (screenW * 0.38f);
        float drawX;

        if (side == TiltController.PeekSide.LEFT) {
            drawX = baseX - slideX;
            // Flip for looking left
            canvas.save();
            canvas.scale(-1, 1, baseX + pw / 2f, baseY + ph / 2f);
            canvas.drawBitmap(playerBmp, drawX, baseY, null);
            canvas.restore();
        } else if (side == TiltController.PeekSide.RIGHT) {
            drawX = baseX + slideX;
            canvas.drawBitmap(playerBmp, drawX, baseY, null);
        } else {
            // Portrait — fully behind wall
            canvas.drawBitmap(playerBmp, baseX, baseY, null);
        }
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private void drawHUD(Canvas canvas, float peek) {
        // Level badge — top left
        String lvlStr = "LEVEL " + currentLevel;
        canvas.drawText(lvlStr, 24, 54, hudPaint);

        // Score — top right
        String scoreStr = "SCORE  " + score;
        float scoreW = hudPaint.measureText(scoreStr);
        canvas.drawText(scoreStr, screenW - scoreW - 24, 54, hudPaint);

        // Lives — below level
        String livesStr = "LIVES  ";
        canvas.drawText(livesStr, 24, 96, hudSmallPaint);
        for (int i = 0; i < 3; i++) {
            Paint hp = makeColorPaint(i < lives ? 0xFFE84040 : 0x55E84040);
            hp.setTextSize(28f);
            hp.setAntiAlias(true);
            canvas.drawText("♥", 110 + i * 34, 96, hp);
        }

        // Ammo boxes — bottom center, always visible
        drawAmmoHUD(canvas);

        // Peek indicator — subtle arrow when in portrait
        if (peek < 0.1f && gameState == GameState.RUNNING) {
            drawPeekHint(canvas);
        }

        // "BEHIND COVER" label when hidden
        if (tilt.isHidden() && gameState == GameState.RUNNING) {
            float cx = screenW / 2f;
            float cy = screenH * 0.18f;
            canvas.drawText("BEHIND COVER", cx, cy, dimPaint);
            if (!reloading && ammo < maxAmmo) {
                canvas.drawText("Tap to reload", cx, cy + 38, dimPaint);
            }
        }
    }

    private void drawAmmoHUD(Canvas canvas) {
        int boxW = 22, boxH = 30, gap = 6;
        int totalW = maxAmmo * (boxW + gap) - gap;
        float startX = screenW / 2f - totalW / 2f;
        float y = screenH - 56f;

        for (int i = 0; i < maxAmmo; i++) {
            Paint p = (i < ammo) ? ammoBoxPaint : ammoBoxEmpty;
            float bx = startX + i * (boxW + gap);
            canvas.drawRoundRect(new RectF(bx, y, bx + boxW, y + boxH), 4, 4, p);
        }
        canvas.drawText("AMMO", screenW / 2f, y + boxH + 22, hudSmallPaint);
    }

    private void drawPeekHint(Canvas canvas) {
        float cx = screenW / 2f;
        float y  = screenH / 2f - 40f;
        Paint p  = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x55FFFFFF);
        p.setTextSize(22f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("← Tilt to peek →", cx, y, p);
    }

    // ── Reload bar ────────────────────────────────────────────────────────────

    private void drawReloadBar(Canvas canvas) {
        float barW  = screenW * 0.55f;
        float barH  = 20f;
        float barX  = screenW / 2f - barW / 2f;
        float barY  = screenH * 0.35f;
        float fill  = Math.min(1f, (float) reloadElapsed / levelCfg.reloadDurationMs);

        canvas.drawRoundRect(new RectF(barX, barY, barX + barW, barY + barH),
                10, 10, reloadBarBg);
        canvas.drawRoundRect(new RectF(barX, barY, barX + barW * fill, barY + barH),
                10, 10, reloadBarFg);

        Paint lbl = new Paint(Paint.ANTI_ALIAS_FLAG);
        lbl.setColor(0xFFFFD700);
        lbl.setTextSize(20f);
        lbl.setTextAlign(Paint.Align.CENTER);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("RELOADING...", screenW / 2f, barY - 10f, lbl);

        Paint warn = new Paint(Paint.ANTI_ALIAS_FLAG);
        warn.setColor(0xAAFF4444);
        warn.setTextSize(17f);
        warn.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Stay behind the wall!", screenW / 2f, barY + barH + 24f, warn);
    }

    // ── Briefing screen ───────────────────────────────────────────────────────

    private void drawBriefing(Canvas canvas) {
        // Fade in
        float alpha = Math.min(1f, briefingFadeMs / (float) BRIEFING_FADE_DURATION);
        int a = (int)(alpha * 255);

        // Dark overlay
        Paint bg = new Paint();
        bg.setColor(0xEE0A0E18);
        bg.setAlpha(a);
        canvas.drawRect(0, 0, screenW, screenH, bg);

        if (alpha < 0.3f) return; // wait for fade before drawing text

        float cx = screenW / 2f;
        float marginX = screenW * 0.08f;
        float cardL = marginX;
        float cardR = screenW - marginX;
        float cardW = cardR - cardL;

        // Card background
        Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
        card.setColor(0xFF0F1923);
        card.setAlpha(Math.min(255, (int)(alpha * 300)));
        canvas.drawRoundRect(new RectF(cardL, screenH * 0.06f, cardR, screenH * 0.94f),
                24, 24, card);

        // Card border
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(0xFF8B0000);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2f);
        border.setAlpha(a);
        canvas.drawRoundRect(new RectF(cardL, screenH * 0.06f, cardR, screenH * 0.94f),
                24, 24, border);

        float y = screenH * 0.13f;

        // ── LEVEL badge ──────────────────────────────────────────────────────
        withAlpha(titlePaint, a);
        titlePaint.setTextSize(spW(52));
        canvas.drawText("LEVEL  " + currentLevel, cx, y, titlePaint);
        y += spW(14);

        // Red underline
        Paint line = makeColorPaint(0xFFAA0000);
        line.setAlpha(a);
        line.setStrokeWidth(2f);
        canvas.drawLine(cx - cardW * 0.3f, y, cx + cardW * 0.3f, y, line);
        y += spW(22);

        // ── Description ──────────────────────────────────────────────────────
        withAlpha(accentPaint, a);
        accentPaint.setTextSize(spW(17));
        canvas.drawText(levelCfg.description.toUpperCase(), cx, y, accentPaint);
        y += spW(34);

        // ── Divider ──────────────────────────────────────────────────────────
        Paint divider = makeColorPaint(0x33FFFFFF);
        divider.setAlpha((int)(a * 0.4f));
        divider.setStrokeWidth(1f);
        canvas.drawLine(cardL + 32, y, cardR - 32, y, divider);
        y += spW(24);

        // ── Enemies this level ────────────────────────────────────────────────
        withAlpha(subPaint, a);
        subPaint.setTextSize(spW(15));
        canvas.drawText("ENEMIES", cx, y, subPaint);
        y += spW(10);

        withAlpha(bodyPaint, a);
        bodyPaint.setTextSize(spW(16));
        canvas.drawText("⬦ " + levelCfg.zombieCount + " zombies across " + levelCfg.laneCount
                + (levelCfg.laneCount == 1 ? " lane" : " lanes"), cx, y, bodyPaint);
        y += spW(22);

        if (levelCfg.hasArmoredZombies) {
            canvas.drawText("⬦ Armored zombies — takes 3 hits", cx, y, bodyPaint);
            y += spW(22);
        }
        if (levelCfg.hasBoss) {
            Paint bossP = new Paint(bodyPaint);
            bossP.setColor(0xFFFF4444);
            bossP.setAlpha(a);
            canvas.drawText("⬦ BOSS — charges at low health!", cx, y, bossP);
            y += spW(22);
        }

        // ── Privileges ────────────────────────────────────────────────────────
        y += spW(8);
        canvas.drawLine(cardL + 32, y, cardR - 32, y, divider);
        y += spW(22);

        withAlpha(subPaint, a);
        canvas.drawText("YOUR LOADOUT", cx, y, subPaint);
        y += spW(10);

        withAlpha(bodyPaint, a);
        canvas.drawText("⬦ " + maxAmmo + " rounds per magazine", cx, y, bodyPaint);
        y += spW(22);
        canvas.drawText("⬦ Reload time: " + (levelCfg.reloadDurationMs / 1000f) + "s  (stay hidden!)", cx, y, bodyPaint);
        y += spW(22);
        canvas.drawText("⬦ Aim: " + (levelCfg.autoAim ? "AUTO" : "MANUAL — drag to aim"), cx, y, bodyPaint);
        y += spW(22);

        // ── How to play (level 1 only) ────────────────────────────────────────
        if (currentLevel == 1) {
            y += spW(8);
            canvas.drawLine(cardL + 32, y, cardR - 32, y, divider);
            y += spW(22);

            withAlpha(subPaint, a);
            canvas.drawText("HOW TO PLAY", cx, y, subPaint);
            y += spW(10);

            withAlpha(bodyPaint, a);
            bodyPaint.setTextSize(spW(15));
            String[] tips = {
                    "⬦ Hold phone upright — you're behind the wall",
                    "⬦ Tilt LEFT to peek left & shoot left side",
                    "⬦ Tilt RIGHT to peek right & shoot right side",
                    "⬦ Tap screen to fire when peeking",
                    "⬦ Go upright + tap to reload when empty",
                    "⬦ Don't let zombies reach the wall!"
            };
            for (String tip : tips) {
                canvas.drawText(tip, cx, y, bodyPaint);
                y += spW(21);
            }
        }

        // ── Tap to start ─────────────────────────────────────────────────────
        float tapY = screenH * 0.89f;
        Paint pulse = new Paint(Paint.ANTI_ALIAS_FLAG);
        long now = System.currentTimeMillis();
        float pulseAlpha = 0.55f + 0.45f * (float) Math.sin(now / 400.0);
        pulse.setColor(0xFFFFFFFF);
        pulse.setAlpha((int)(pulseAlpha * a));
        pulse.setTextSize(spW(20));
        pulse.setTextAlign(Paint.Align.CENTER);
        pulse.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("TAP TO START", cx, tapY, pulse);
    }

    // ── Level clear / Game over ───────────────────────────────────────────────

    private void drawLevelClear(Canvas canvas) {
        Paint bg = makeColorPaint(0xCC0A1208);
        canvas.drawRect(0, 0, screenW, screenH, bg);

        float cx = screenW / 2f;
        float cy = screenH / 2f;

        titlePaint.setTextSize(spW(52));
        titlePaint.setColor(0xFF88FF66);
        canvas.drawText("WAVE CLEAR", cx, cy - spW(40), titlePaint);

        bodyPaint.setTextSize(spW(20));
        bodyPaint.setColor(0xFFCCCCCC);
        canvas.drawText("Score: " + score, cx, cy + spW(10), bodyPaint);
        canvas.drawText("Tap to continue →", cx, cy + spW(50), bodyPaint);

        // Reset colours for next use
        titlePaint.setColor(0xFFFFFFFF);
        bodyPaint.setColor(0xFFBBBBBB);
    }

    private void drawGameOver(Canvas canvas) {
        Paint bg = makeColorPaint(0xCC1A0000);
        canvas.drawRect(0, 0, screenW, screenH, bg);

        float cx = screenW / 2f;
        float cy = screenH / 2f;

        titlePaint.setTextSize(spW(52));
        titlePaint.setColor(0xFFFF3333);
        canvas.drawText("GAME OVER", cx, cy - spW(40), titlePaint);

        bodyPaint.setTextSize(spW(20));
        bodyPaint.setColor(0xFFCCCCCC);
        canvas.drawText("Final score: " + score, cx, cy + spW(10), bodyPaint);
        canvas.drawText("Tap to try again", cx, cy + spW(50), bodyPaint);

        titlePaint.setColor(0xFFFFFFFF);
        bodyPaint.setColor(0xFFBBBBBB);
    }

    // ─── Touch input ──────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_DOWN
                && e.getAction() != MotionEvent.ACTION_MOVE
                && e.getAction() != MotionEvent.ACTION_UP) return true;

        float tx = e.getX(), ty = e.getY();

        // Briefing → start level
        if (gameState == GameState.BRIEFING && e.getAction() == MotionEvent.ACTION_UP) {
            if (briefingFadeMs > BRIEFING_FADE_DURATION) {
                gameState = GameState.RUNNING;
            }
            return true;
        }

        // Level clear → next level
        if (gameState == GameState.LEVEL_CLEAR && e.getAction() == MotionEvent.ACTION_UP) {
            currentLevel++;
            loadLevel(currentLevel);
            return true;
        }

        // Game over → restart
        if (gameState == GameState.GAME_OVER && e.getAction() == MotionEvent.ACTION_UP) {
            currentLevel = 1; score = 0; lives = 3;
            loadLevel(1);
            return true;
        }

        if (gameState != GameState.RUNNING) return true;

        // Reload — tap while hidden and not reloading
        if (tilt.isHidden() && !reloading && ammo < maxAmmo
                && e.getAction() == MotionEvent.ACTION_DOWN) {
            reloading     = true;
            reloadElapsed = 0;
            return true;
        }

        // Cancel reload if tapping while hidden and already reloading (no effect)
        if (tilt.isHidden()) return true;

        // Shoot
        if (!levelCfg.autoAim) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    aimActive = true; aimX = tx; aimY = ty; break;
                case MotionEvent.ACTION_MOVE:
                    aimX = tx; aimY = ty; break;
                case MotionEvent.ACTION_UP:
                    if (aimActive) { shoot(aimX, aimY); aimActive = false; }
                    break;
            }
        } else {
            if (e.getAction() == MotionEvent.ACTION_DOWN) shoot(tx, ty);
        }

        return true;
    }

    // ─── Shoot ────────────────────────────────────────────────────────────────

    private void shoot(float targetX, float targetY) {
        if (reloading || tilt.isHidden()) return;

        if (ammo <= 0) {
            sound.playSfx(SoundManager.SFX_EMPTY_CLICK);
            return;
        }

        sound.playSfx(SoundManager.SFX_GUNSHOT);
        ammo--;

        int dir = (tilt.getSide() == TiltController.PeekSide.LEFT) ? -1 : 1;
        float bulletX = screenW / 2f + (dir * 50f);
        float bulletY = levelCfg.autoAim ? findAutoAimY() : targetY;

        bullets.add(new Bullet(bulletX, bulletY, dir));
    }

    private float findAutoAimY() {
        for (Zombie z : zombies) {
            if (z.isAlive()) return z.getY() + z.getHeight() / 2f;
        }
        return screenH * 0.55f;
    }

    // ─── Spawn ────────────────────────────────────────────────────────────────

    private void spawnZombie() {
        int lane = (int)(Math.random() * levelCfg.laneCount);

        // Zombies come from opposite sides — left side zombies approach from far left
        // right side from far right
        boolean spawnLeft = (Math.random() < 0.5);
        float startX = spawnLeft ? -80f : screenW + 80f;

        Zombie.ZombieType type = Zombie.ZombieType.NORMAL;
        if (levelCfg.hasArmoredZombies && Math.random() < 0.35)
            type = Zombie.ZombieType.ARMORED;
        if (levelCfg.hasBoss && zombiesSpawned == levelCfg.zombieCount - 1)
            type = Zombie.ZombieType.BOSS;

        Bitmap sheet = (type == Zombie.ZombieType.ARMORED && armoredSheet != null)
                ? armoredSheet : zombieSheet;

        ZombieWalkAnim  walkA  = new ZombieWalkAnim(sheet, 8, 64, 80);
        ZombieDeathAnim deathA = new ZombieDeathAnim(zombieDeathSheet, 6, 64, 80);

        float laneY = screenH * 0.45f + lane * 60f;

        Zombie z;
        if (type == Zombie.ZombieType.ARMORED) {
            z = new ArmoredZombie(startX, laneY, lane, walkA, deathA);
        } else {
            z = new Zombie(startX, laneY, lane,
                    levelCfg.zombieBaseSpeed, 3, type, walkA, deathA);
        }

        zombies.add(z);
        zombiesSpawned++;
    }

    private int scoreForZombie(Zombie z) {
        return switch (z.getType()) {
            case ARMORED -> 150;
            case BOSS    -> 500;
            default      -> 100;
        };
    }

    // ─── Assets ───────────────────────────────────────────────────────────────

    private void loadBitmaps() {
        try {
            playerBmp        = BitmapFactory.decodeResource(getResources(), R.drawable.player);
            zombieSheet      = BitmapFactory.decodeResource(getResources(), R.drawable.zombie_walk);
            zombieDeathSheet = BitmapFactory.decodeResource(getResources(), R.drawable.zombie_death);
            armoredSheet     = BitmapFactory.decodeResource(getResources(), R.drawable.zombie_armored);
            bgBmp            = BitmapFactory.decodeResource(getResources(), R.drawable.bg_street);
            wallBmp          = BitmapFactory.decodeResource(getResources(), R.drawable.wall_concrete);
        } catch (Exception e) {
            // Fallback placeholders if drawables missing
            playerBmp        = makePlaceholder(80, 120, 0xFF4488FF);
            zombieSheet      = makePlaceholder(512, 80, 0xFF44BB44);
            zombieDeathSheet = makePlaceholder(384, 80, 0xFFBB4444);
            armoredSheet     = zombieSheet;
        }
    }

    private Bitmap makePlaceholder(int w, int h, int color) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(color);
        return b;
    }

    // ─── Paint helpers ────────────────────────────────────────────────────────

    private void initPaints() {
        Typeface bold = Typeface.DEFAULT_BOLD;

        bgPaint      = makeColorPaint(0xFF0D1B2A);
        wallPaint    = makeColorPaint(0xFF3A3835);
        wallEdgePaint= makeColorPaint(0xFF252320);
        wallEdgePaint.setStyle(Paint.Style.STROKE);
        wallEdgePaint.setStrokeWidth(1.5f);

        hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudPaint.setColor(0xFFFFFFFF);
        hudPaint.setTextSize(32f);
        hudPaint.setTextAlign(Paint.Align.LEFT);
        hudPaint.setTypeface(bold);

        hudSmallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudSmallPaint.setColor(0xFFAAAAAA);
        hudSmallPaint.setTextSize(20f);
        hudSmallPaint.setTextAlign(Paint.Align.CENTER);
        hudSmallPaint.setTypeface(bold);

        hudValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudValuePaint.setColor(0xFFFFD700);
        hudValuePaint.setTextSize(22f);
        hudValuePaint.setTextAlign(Paint.Align.LEFT);

        reticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        reticlePaint.setColor(0xAAFF4444);
        reticlePaint.setStyle(Paint.Style.STROKE);
        reticlePaint.setStrokeWidth(3f);

        overlayPaint = makeColorPaint(0xBB000000);

        briefingBgPaint = makeColorPaint(0xEE080E18);

        titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTypeface(bold);

        bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(0xFFBBBBBB);
        bodyPaint.setTextAlign(Paint.Align.CENTER);

        subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(0xFF888888);
        subPaint.setTextSize(24f);
        subPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setTypeface(bold);

        accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        accentPaint.setColor(0xFFCC3333);
        accentPaint.setTextAlign(Paint.Align.CENTER);
        accentPaint.setTypeface(bold);

        dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimPaint.setColor(0x88FFFFFF);
        dimPaint.setTextSize(22f);
        dimPaint.setTextAlign(Paint.Align.CENTER);

        reloadBarBg = makeColorPaint(0xFF222222);
        reloadBarBg.setStyle(Paint.Style.FILL);
        reloadBarFg = makeColorPaint(0xFFFFD700);
        reloadBarFg.setStyle(Paint.Style.FILL);

        ammoBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ammoBoxPaint.setColor(0xFFFFD700);
        ammoBoxPaint.setStyle(Paint.Style.FILL);

        ammoBoxEmpty = new Paint(Paint.ANTI_ALIAS_FLAG);
        ammoBoxEmpty.setColor(0xFF333333);
        ammoBoxEmpty.setStyle(Paint.Style.FILL);

        peekIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        peekIndicatorPaint.setColor(0x44FFFFFF);
    }

    private Paint makeColorPaint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    private void withAlpha(Paint p, int alpha) {
        p.setAlpha(alpha);
    }

    /** Scale-independent pixel helper (approximate) */
    private float spW(float dp) {
        return dp * (screenW / 400f);
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    public void onPause()  { tilt.unregister(); sound.pause();   }
    public void onResume() { tilt.register();   sound.resume();  }
    public void onDestroy(){ sound.release(); }
}
