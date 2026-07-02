package com.undistractme.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Drives the offline, sensor-based "place your phone flat & face-down and stay still" challenge.
 *
 * All processing is 100% on-device using the accelerometer and gyroscope. No data is collected,
 * stored, or transmitted. Listeners are registered at [SensorManager.SENSOR_DELAY_UI] and are
 * unregistered as soon as the challenge stops or completes to conserve battery.
 */
@Singleton
class SensorChallengeManager @Inject constructor(
    private val sensorManager: SensorManager
) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _state = MutableStateFlow(EMPTY_STATE)
    val state: StateFlow<ChallengeState> = _state.asStateFlow()

    // Active-challenge configuration. Written on start(), read on the sensor + tick threads.
    @Volatile private var targetMillis: Long = 0L
    @Volatile private var requireFaceDown: Boolean = true
    @Volatile private var motionTolerance: Float = 1.0f

    // Live sensor-derived signals: written on the sensor callback thread, read by the tick loop.
    @Volatile private var positionValid: Boolean = false
    @Volatile private var movementDetected: Boolean = false
    @Volatile private var lastAccel: FloatArray? = null

    fun start(durationMillis: Long, requireFaceDown: Boolean, motionTolerance: Float) {
        // Ensure any previous challenge is fully torn down before starting a new one.
        stop()

        val safeTarget = durationMillis.coerceAtLeast(0L)
        this.targetMillis = safeTarget
        this.requireFaceDown = requireFaceDown
        this.motionTolerance = motionTolerance
        positionValid = false
        movementDetected = false
        lastAccel = null

        _state.value = ChallengeState(
            targetMillis = safeTarget,
            remainingMillis = safeTarget,
            isPositionValid = false,
            isRunning = true,
            isComplete = false
        )

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        tickJob = scope.launch {
            var remaining = safeTarget
            var lastTick = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(TICK_INTERVAL_MS)

                val now = SystemClock.elapsedRealtime()
                val elapsed = now - lastTick
                lastTick = now

                val valid = positionValid
                val moved = movementDetected
                // Consume the transient movement signal accumulated since the last tick.
                movementDetected = false

                val currentTarget = targetMillis

                if (!valid || moved) {
                    // Position lost or the user moved: reset the countdown.
                    remaining = currentTarget
                    _state.value = ChallengeState(
                        targetMillis = currentTarget,
                        remainingMillis = remaining,
                        isPositionValid = valid,
                        isRunning = true,
                        isComplete = false
                    )
                } else {
                    remaining -= elapsed
                    if (remaining <= 0L) {
                        remaining = 0L
                        _state.value = ChallengeState(
                            targetMillis = currentTarget,
                            remainingMillis = 0L,
                            isPositionValid = true,
                            isRunning = false,
                            isComplete = true
                        )
                        // Challenge satisfied: stop listening to save battery.
                        unregister()
                        break
                    } else {
                        _state.value = ChallengeState(
                            targetMillis = currentTarget,
                            remainingMillis = remaining,
                            isPositionValid = true,
                            isRunning = true,
                            isComplete = false
                        )
                    }
                }
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        unregister()
        lastAccel = null
        positionValid = false
        movementDetected = false
        _state.value = EMPTY_STATE
    }

    private fun unregister() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event.values)
            Sensor.TYPE_GYROSCOPE -> handleGyroscope(event.values)
        }
    }

    private fun handleAccelerometer(values: FloatArray) {
        if (values.size < 3) return
        val x = values[0]
        val y = values[1]
        val z = values[2]

        // Flat detection: little tilt on X/Y (gravity concentrated on the Z axis).
        val flat = abs(x) < FLAT_XY_BOUND && abs(y) < FLAT_XY_BOUND
        val faceDown = z <= -FACE_GRAVITY_BOUND && flat
        val faceUp = z >= FACE_GRAVITY_BOUND && flat
        val valid = if (requireFaceDown) faceDown else (faceDown || faceUp)

        // Motion detection via jerk (magnitude of the change between successive readings).
        val previous = lastAccel
        if (previous != null) {
            val dx = x - previous[0]
            val dy = y - previous[1]
            val dz = z - previous[2]
            val jerk = sqrt(dx * dx + dy * dy + dz * dz)
            if (jerk > ACCEL_MOTION_BASE + motionTolerance) {
                movementDetected = true
            }
        }
        lastAccel = floatArrayOf(x, y, z)

        if (positionValid != valid) {
            positionValid = valid
            // Reflect validity changes immediately for responsive UI without disturbing the
            // countdown bookkeeping owned by the tick loop.
            if (_state.value.isRunning) {
                _state.update { it.copy(isPositionValid = valid) }
            }
        }
    }

    private fun handleGyroscope(values: FloatArray) {
        if (values.size < 3) return
        val magnitude = sqrt(
            values[0] * values[0] + values[1] * values[1] + values[2] * values[2]
        )
        if (magnitude > GYRO_MOTION_BASE + motionTolerance) {
            movementDetected = true
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op: absolute accuracy is irrelevant to the flat/still heuristic.
    }

    private companion object {
        const val TICK_INTERVAL_MS = 250L
        const val FACE_GRAVITY_BOUND = 8.5f
        const val FLAT_XY_BOUND = 2.5f
        const val ACCEL_MOTION_BASE = 2.0f
        const val GYRO_MOTION_BASE = 0.6f

        val EMPTY_STATE = ChallengeState(
            targetMillis = 0L,
            remainingMillis = 0L,
            isPositionValid = false,
            isRunning = false,
            isComplete = false
        )
    }
}
