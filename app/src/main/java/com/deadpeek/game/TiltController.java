package com.deadpeek.game;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class TiltController implements SensorEventListener {

    public enum PeekSide { HIDDEN, LEFT, RIGHT }

    private final SensorManager sensorManager;
    private final Sensor         rotationSensor;

    private PeekSide side       = PeekSide.HIDDEN;
    private float    peekAmount = 0f;

    // --- NEW: PC Support Variables ---
    private PeekSide manualSide = PeekSide.HIDDEN;
    private float manualAmount = 0f;

    private static final float PORTRAIT_THRESHOLD  = 0.20f;
    private static final float LANDSCAPE_THRESHOLD = 1.30f;

    public TiltController(Context context) {
        sensorManager  = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    // NEW: Method for GameView to call when A/D or Arrows are pressed
    public void setManualPeek(PeekSide side, float amount) {
        this.manualSide = side;
        this.manualAmount = amount;
    }

    public void register() {
        if (rotationSensor != null)
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    public void unregister() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;

        // If keyboard is active, ignore the physical sensor
        if (manualSide != PeekSide.HIDDEN) return;

        float[] rotMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values);
        float[] orientation = new float[3];
        SensorManager.getOrientation(rotMatrix, orientation);

        float roll    = orientation[2];
        float absRoll = Math.abs(roll);

        if (absRoll < PORTRAIT_THRESHOLD) {
            side       = PeekSide.HIDDEN;
            peekAmount = 0f;
        } else {
            float raw  = (absRoll - PORTRAIT_THRESHOLD) / (LANDSCAPE_THRESHOLD - PORTRAIT_THRESHOLD);
            peekAmount = Math.min(1f, Math.max(0f, raw));
            side       = (roll < 0) ? PeekSide.LEFT : PeekSide.RIGHT;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // GETTERS: These now return the Manual value if it's active
    public float getPeekAmount() { 
        return (manualSide != PeekSide.HIDDEN) ? manualAmount : peekAmount; 
    }

    public PeekSide getSide() { 
        return (manualSide != PeekSide.HIDDEN) ? manualSide : side; 
    }

    public boolean isHidden()       { return getSide() == PeekSide.HIDDEN; }
    public boolean isPeekingLeft()  { return getSide() == PeekSide.LEFT;   }
    public boolean isPeekingRight() { return getSide() == PeekSide.RIGHT;  }
    public boolean isFullyPeeked()  { return getPeekAmount() >= 0.85f;     }

    public float getNormalizedRoll() {
        PeekSide s = getSide();
        float a = getPeekAmount();
        if (s == PeekSide.LEFT)  return -a;
        if (s == PeekSide.RIGHT) return  a;
        return 0f;
    }
}