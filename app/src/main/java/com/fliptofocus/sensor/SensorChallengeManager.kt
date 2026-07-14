package com.fliptofocus.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.fliptofocus.domain.model.ChallengeType
import com.fliptofocus.util.Haptics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Drives every offline unlock challenge (FLIP / WAIT / SHAKE / MATH), producing a single
 * [ChallengeState] flow the overlay observes. All processing is 100% on-device; nothing is
 * collected, stored, or transmitted. Sensor listeners are registered only for the challenge types
 * that need them and are unregistered as soon as the challenge stops or completes to save battery.
 *
 * The class name is retained for wiring compatibility; despite the name, WAIT and MATH use no
 * sensors at all (so they also work as reliable fallbacks on devices with faulty sensors).
 */
@Singleton
class SensorChallengeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sensorManager: SensorManager
) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _state = MutableStateFlow(ChallengeState())
    val state: StateFlow<ChallengeState> = _state.asStateFlow()

    // Active-challenge configuration (written on start(), read from sensor + tick threads).
    @Volatile private var type: ChallengeType = ChallengeType.FLIP
    @Volatile private var targetMillis: Long = 0L
    @Volatile private var startRemainingMillis: Long = 0L
    @Volatile private var requireFaceDown: Boolean = true
    @Volatile private var motionTolerance: Float = 1.0f
    @Volatile private var shakeTarget: Int = 0

    // FLIP live signals.
    @Volatile private var positionValid: Boolean = false
    @Volatile private var movementDetected: Boolean = false
    @Volatile private var lastAccel: FloatArray? = null

    // SHAKE.
    @Volatile private var shakeCount: Int = 0
    @Volatile private var lastShakeAt: Long = 0L

    // MATH (mutated on the overlay/main thread via submitMathAnswer + on start()).
    private var mathTotal: Int = 0
    private var mathSolved: Int = 0
    private var mathAnswer: Int = 0
    private var mathQuestion: String? = null
    private var mathOptions: List<Int> = emptyList()

    fun start(
        type: ChallengeType,
        durationMillis: Long,
        requireFaceDown: Boolean,
        motionTolerance: Float,
        shakeTarget: Int,
        mathTotal: Int,
        elapsedMillis: Long = 0L
    ) {
        // Ensure any previous challenge is fully torn down first.
        stop()

        this.type = type
        this.targetMillis = durationMillis.coerceAtLeast(0L)
        this.requireFaceDown = requireFaceDown
        this.motionTolerance = motionTolerance
        this.shakeTarget = shakeTarget.coerceAtLeast(1)
        this.mathTotal = mathTotal.coerceAtLeast(1)
        // How much time is left to display; lets a cooldown resume mid-way instead of restarting.
        this.startRemainingMillis = (this.targetMillis - elapsedMillis.coerceAtLeast(0L))
            .coerceIn(0L, this.targetMillis)

        positionValid = false
        movementDetected = false
        lastAccel = null
        shakeCount = 0
        mathSolved = 0

        when (type) {
            ChallengeType.FLIP -> {
                registerAccelerometer()
                registerGyroscope()
                emitTimed(startRemainingMillis, positionValid = false, complete = false)
                startTimerTick()
            }
            ChallengeType.WAIT, ChallengeType.COOLDOWN -> {
                emitTimed(startRemainingMillis, positionValid = true, complete = false)
                startTimerTick()
            }
            ChallengeType.SHAKE -> {
                registerAccelerometer()
                emitShake(complete = false)
            }
            ChallengeType.MATH -> {
                generateMathProblem()
                emitMath(complete = false)
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
        shakeCount = 0
        mathSolved = 0
        mathQuestion = null
        mathOptions = emptyList()
        _state.value = ChallengeState()
    }

    /**
     * Records an answer for the MATH challenge. A correct answer advances progress and moves to the
     * next problem (or completes); a wrong answer generates a fresh problem without progress so the
     * user cannot brute-force by tapping. No-op for non-MATH challenges.
     */
    fun submitMathAnswer(answer: Int) {
        if (type != ChallengeType.MATH || _state.value.isComplete) return
        if (answer == mathAnswer) {
            mathSolved++
            if (mathSolved >= mathTotal) {
                emitMath(complete = true)
            } else {
                generateMathProblem()
                emitMath(complete = false)
            }
        } else {
            generateMathProblem()
            emitMath(complete = false)
        }
    }

    // --- Timed challenges (FLIP / WAIT) -------------------------------------------------------

    private fun startTimerTick() {
        tickJob = scope.launch {
            var remaining = startRemainingMillis
            var lastTick = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(TICK_INTERVAL_MS)

                val now = SystemClock.elapsedRealtime()
                val elapsed = now - lastTick
                lastTick = now

                val requireStill = type == ChallengeType.FLIP
                val valid = if (requireStill) positionValid else true
                val moved = if (requireStill) movementDetected else false
                // Consume the transient movement signal accumulated since the last tick.
                movementDetected = false

                if (requireStill && (!valid || moved)) {
                    // Position lost or the user moved: reset the countdown.
                    remaining = targetMillis
                    emitTimed(remaining, positionValid = valid, complete = false)
                } else {
                    remaining -= elapsed
                    if (remaining <= 0L) {
                        emitTimed(0L, positionValid = true, complete = true)
                        unregister()
                        break
                    }
                    emitTimed(remaining, positionValid = valid, complete = false)
                }
            }
        }
    }

    // --- Sensor callbacks ---------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> when (type) {
                ChallengeType.FLIP -> handleFlipAccelerometer(event.values)
                ChallengeType.SHAKE -> handleShakeAccelerometer(event.values)
                else -> Unit
            }
            Sensor.TYPE_GYROSCOPE -> if (type == ChallengeType.FLIP) handleGyroscope(event.values)
        }
    }

    private fun handleFlipAccelerometer(values: FloatArray) {
        if (values.size < 3) return
        val x = values[0]
        val y = values[1]
        val z = values[2]

        val flat = abs(x) < FLAT_XY_BOUND && abs(y) < FLAT_XY_BOUND
        val faceDown = z <= -FACE_GRAVITY_BOUND && flat
        val faceUp = z >= FACE_GRAVITY_BOUND && flat
        val valid = if (requireFaceDown) faceDown else (faceDown || faceUp)

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
            val current = _state.value
            if (current.isRunning && current.type == ChallengeType.FLIP) {
                _state.value = current.copy(isPositionValid = valid)
                // Buzz when the phone moves OUT of the correct position, as a nudge.
                if (!valid) Haptics.blip(context)
            }
        }
    }

    private fun handleShakeAccelerometer(values: FloatArray) {
        if (values.size < 3) return
        val gForce = sqrt(
            values[0] * values[0] + values[1] * values[1] + values[2] * values[2]
        ) / SensorManager.GRAVITY_EARTH
        val now = SystemClock.elapsedRealtime()
        if (gForce > SHAKE_G_THRESHOLD && now - lastShakeAt > SHAKE_DEBOUNCE_MS) {
            lastShakeAt = now
            shakeCount++
            val complete = shakeCount >= shakeTarget
            emitShake(complete)
            if (complete) unregister()
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
        // No-op: absolute accuracy is irrelevant to our heuristics.
    }

    // --- MATH ---------------------------------------------------------------------------------

    private fun generateMathProblem() {
        val a = Random.nextInt(2, 13)
        val b = Random.nextInt(2, 13)
        val (question, answer) = when (Random.nextInt(3)) {
            0 -> "$a + $b" to (a + b)
            1 -> {
                val hi = maxOf(a, b)
                val lo = minOf(a, b)
                "$hi - $lo" to (hi - lo)
            }
            else -> "$a x $b" to (a * b)
        }
        val options = LinkedHashSet<Int>()
        options.add(answer)
        var guard = 0
        while (options.size < 4 && guard < 50) {
            guard++
            val candidate = answer + Random.nextInt(-6, 7)
            if (candidate >= 0 && candidate != answer) options.add(candidate)
        }
        mathAnswer = answer
        mathQuestion = question
        mathOptions = options.toList().shuffled()
    }

    // --- Emit helpers -------------------------------------------------------------------------

    private fun emitTimed(remaining: Long, positionValid: Boolean, complete: Boolean) {
        _state.value = ChallengeState(
            type = type,
            targetMillis = targetMillis,
            remainingMillis = remaining,
            isPositionValid = positionValid,
            isRunning = !complete,
            isComplete = complete
        )
    }

    private fun emitShake(complete: Boolean) {
        _state.value = ChallengeState(
            type = ChallengeType.SHAKE,
            isRunning = !complete,
            isComplete = complete,
            shakeTarget = shakeTarget,
            shakeCount = shakeCount
        )
    }

    private fun emitMath(complete: Boolean) {
        _state.value = ChallengeState(
            type = ChallengeType.MATH,
            isRunning = !complete,
            isComplete = complete,
            mathQuestion = mathQuestion,
            mathOptions = mathOptions,
            mathTotal = mathTotal,
            mathSolved = mathSolved
        )
    }

    // --- Sensor registration ------------------------------------------------------------------

    private fun registerAccelerometer() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    private fun registerGyroscope() {
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    private fun unregister() {
        sensorManager.unregisterListener(this)
    }

    private companion object {
        const val TICK_INTERVAL_MS = 250L
        const val FACE_GRAVITY_BOUND = 8.5f
        const val FLAT_XY_BOUND = 2.5f
        const val ACCEL_MOTION_BASE = 2.0f
        const val GYRO_MOTION_BASE = 0.6f
        const val SHAKE_G_THRESHOLD = 2.2f
        const val SHAKE_DEBOUNCE_MS = 250L
    }
}
