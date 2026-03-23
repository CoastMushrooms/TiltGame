package com.deadpeek.game;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * TiltController
 *
 * Maps physical phone orientation to game peek state:
 *
 *   Portrait  (phone upright)          → HIDDEN,  peekAmount = 0.0
 *   Tilt LEFT  (top tilts left)        → LEFT,    peekAmount 0.0 → 1.0
 *   Tilt RIGHT (top tilts right)       → RIGHT,   peekAmount 0.0 → 1.0
 *
 * peekAmount = 0 means just starting to peek, 1 = fully landscape.
 */
public class TiltController implements SensorEventListener {

    public enum PeekSide { HIDDEN, LEFT, RIGHT }

    private final SensorManager sensorManager;
    private final Sensor        rotationSensor;

    private PeekSide side       = PeekSide.HIDDEN;
    private float    peekAmount = 0f;

    // Roll thresholds (radians)
    // 0 = upright portrait, PI/2 ≈ 1.57 = fully landscape
    private static final float PORTRAIT_THRESHOLD  = 0.20f; // ~11° — still hidden
    private static final float LANDSCAPE_THRESHOLD = 1.30f; // ~75° — fully peeked

    public TiltController(Context context) {
        sensorManager  = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void register() {
        if (rotationSensor != null)
            sensorManager.registerListener(this, rotationSensor,
                    SensorManager.SENSOR_DELAY_GAME);
    }

    public void unregister() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;

        float[] rotMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values);

        float[] orientation = new float[3];
        SensorManager.getOrientation(rotMatrix, orientation);

        // orientation[2] = roll
        // negative roll = tilting phone top to the left
        // positive roll = tilting phone top to the right
        float roll    = orientation[2];
        float absRoll = Math.abs(roll);

        if (absRoll < PORTRAIT_THRESHOLD) {
            side       = PeekSide.HIDDEN;
            peekAmount = 0f;
        } else {
            float raw  = (absRoll - PORTRAIT_THRESHOLD)
                    / (LANDSCAPE_THRESHOLD - PORTRAIT_THRESHOLD);
            peekAmount = Math.min(1f, Math.max(0f, raw));
            side       = (roll < 0) ? PeekSide.LEFT : PeekSide.RIGHT;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public float    getPeekAmount()      { return peekAmount; }
    public PeekSide getSide()            { return side; }
    public boolean  isHidden()           { return side == PeekSide.HIDDEN; }
    public boolean  isPeekingLeft()      { return side == PeekSide.LEFT;   }
    public boolean  isPeekingRight()     { return side == PeekSide.RIGHT;  }
    public boolean  isFullyPeeked()      { return peekAmount >= 0.85f;     }

    /** Legacy -1..+1 normalised value */
    public float getNormalizedRoll() {
        if (side == PeekSide.LEFT)  return -peekAmount;
        if (side == PeekSide.RIGHT) return  peekAmount;
        return 0f;
    }
}