package com.platypii.baselinexr

// import com.platypii.baselinexr.measurements.MLocation // Unused
import android.util.Log
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.jarvis.FlightMode
import com.platypii.baselinexr.location.AtmosphericModel
import com.platypii.baselinexr.location.KalmanFilter3D
import com.platypii.baselinexr.location.MotionEstimator
import com.platypii.baselinexr.location.PolarLibrary
import com.platypii.baselinexr.location.SimpleEstimator
import com.platypii.baselinexr.util.HeadPoseUtil
import kotlin.math.*

/**
 * Canopy data point structure matching the TypeScript Cpoint format
 */
data class CanopyPoint(
    val millis: Long = 0,
    // canopy position relative to pilot
    val cpE: Double = 0.0,
    val cpD: Double = 0.0,
    val cpN: Double = 0.0,
    // canopy speed
    val vcpE: Double = 0.0,
    val vcpD: Double = 0.0,
    val vcpN: Double = 0.0,
    // aoa
    val cpaoa: Double = 0.0,
    // sustained
    val cpvxs: Double = 0.0,
    val cpvys: Double = 0.0,
    // roll
    val roll: Double = 0.0
)

/**
 * Compute wind-adjusted canopy orientation for a single time step using 90Hz predicted state
 * Converted from TypeScript computewindadjustedcanopyorientation function
 */
fun computeCanopyOrientation(
    predictedState: KalmanFilter3D.KFState,
    gpsaltitude: Double,
    currentMillis: Long,
    useRealOrientation: Boolean = false
): CanopyPoint {
    val GRAVITY = 9.80665 // m/s²

    // Get velocity components from predicted state (ENU coordinates)
    val vN = predictedState.velocity.z  // North velocity
    val vE = predictedState.velocity.x  // East velocity
    val vD = -predictedState.velocity.y // Down velocity (negative of up velocity)
    // Get velocity components from predicted state (ENU coordinates)
    val aN = predictedState.acceleration().z  // North velocity
    val aE = predictedState.acceleration().x  // East velocity
    val aD = -predictedState.acceleration().y // Down velocity (negative of up velocity)

    val vel = sqrt(vN * vN + vE * vE + vD * vD)

    val accelDminusG: Double = aD - GRAVITY


    // Calculate acceleration due to drag (projection onto velocity)



    val proj: Double = (aN * vN + aE * vE + accelDminusG * vD) / vel

    val dragN = proj * vN / vel
    val dragE = proj * vE / vel
    val dragD = proj * vD / vel

    // Calculate correct sign for drag
    val dragSign = -sign(dragN * vN + dragE * vE + dragD * vD)

    //val accelDrag = dragSign * sqrt(dragN * dragN + dragE * dragE + dragD * dragD)


    // Calculate acceleration due to lift (rejection from velocity)
    val liftN: Double = aN - dragN
    val liftE: Double = aE - dragE
    val liftD = accelDminusG - dragD
    // val accelLift = sqrt(liftN * liftN + liftE * liftE + liftD * liftD)

    // Wingsuit/pilot parameters
    val m = 77.5 // mass in kg
    val s = 2.0  // wing area in m²

    // Calculate atmospheric density
    val rho = AtmosphericModel.calculateDensity(gpsaltitude.toFloat(), 10f).toDouble()

    // Calculate pilot's drag contribution
    val dynamicPressure = rho * vel * vel / 2
    val pilotCd = 0.35
    val pilotAccelDrag = pilotCd * dynamicPressure * s / m

    // Pilot's drag components
    val pdragN = if (vel > 0) -vN / vel * pilotAccelDrag else 0.0
    val pdragE = if (vel > 0) -vE / vel * pilotAccelDrag else 0.0
    val pdragD = if (vel > 0) -vD / vel * pilotAccelDrag else 0.0

    // Calculate canopy normal force (subtract pilot's drag from total aerodynamic force)
    // Using predicted acceleration components
    val cnormN = liftN + dragN - pdragN
    val cnormE = liftE + dragE - pdragE
    val cnormD = liftD + dragD - pdragD

    // Canopy position relative to pilot
     //   .cpE * ((5.1 - .325) / 3) + (cdata[p].cplng - cdata[cur].cplng) * 111320 * Math.cos(cdata[p].cplat * Math.PI / 180), y: -cdata[p].cpD * ((5.1 - .325) / 3) + (cdata[cur].cpalt - cdata[p].cpalt), z: cdata[p].cpN * ((5.1 - .325) / 3
    val lineLength = if (useRealOrientation) 3.0 else 3.0 // meters from pilot's CG to aerodynamic center
    val magf = sqrt(cnormN * cnormN + cnormE * cnormE + cnormD * cnormD)

    val cpE = if (magf > 0) lineLength * cnormE / magf else 0.0
    val cpD = if (magf > 0) lineLength * cnormD / magf else 0.0
    val cpN = if (magf > 0) lineLength * cnormN / magf else 0.0

    // Global canopy position (simplified - would need proper coordinate conversion)
    //val cplat = gpsLocation.latitude + cpN / 111320.0
    //val cplng = gpsLocation.longitude + cpE / (111320.0 * cos(gpsLocation.latitude * PI / 180))
    //val cpalt = gpsLocation.altitude_gps - cpD

    // For single time step, canopy speed equals pilot speed (no rotation calculation)
    // In full implementation, this would use historical data for rotation speed
    val vcpE = vE
    val vcpD = vD
    val vcpN = vN

    // Calculate canopy AOA
    // Angle between canopy position vector and velocity vector
    val dot = cpE * vcpE + cpD * vcpD + cpN * vcpN
    val cpMag = sqrt(cpE * cpE + cpD * cpD + cpN * cpN)
    val vcpMag = sqrt(vcpE * vcpE + vcpD * vcpD + vcpN * vcpN)

    val anglenv = if (cpMag > 0 && vcpMag > 0) {
        PI - acos(dot / (cpMag * vcpMag))
    } else {
        0.0
    }

    val trimAngle = PI / 2 + 6 * PI / 180 // 6 degrees
    val cpaoa = PI - trimAngle - anglenv

    return CanopyPoint(
        millis = currentMillis,
        cpE = cpE,
        cpD = cpD,
        cpN = cpN,
        vcpE = vcpE,
        vcpD = vcpD,
        vcpN = vcpN,
        cpaoa = cpaoa,
        roll = predictedState.roll // Use roll from predicted state
    )
}
class WingsuitCanopySystem : SystemBase() {

    companion object {
        private const val TAG = "WingsuitCanopySystem"
        private const val WINGSUIT_SCALE = 0.05f
        private const val CANOPY_SCALE = 0.08f
        private const val MODEL_HEIGHT_OFFSET = -3.0f
        private const val CANOPY_MODEL_HEIGHT_OFFSET = -0.3f
        private const val MIN_SPEED_THRESHOLD = 0.5 // m/s minimum speed to show models
        private const val ENLARGEMENT_FACTOR = 5f
    }

    private var wingsuitEntity: Entity? = null
    private var canopyEntity: Entity? = null
    private var initialized = false
    private var isEnlarged = false
    private var lastGpsTime: Long = 0 // Track when GPS data was last updated
    private final var isRealCanopyOrientation = true // Flag to control canopy positioning mode

    override fun execute() {
        if (!initialized) {
            initializeModels()
        }

        // Update driven by 90Hz GpsToWorldTransform predictDelta() calls
        updateModelOrientationAndVisibility()
    }

    private fun initializeModels() {
        // Initial model alignment rotation (to fix model orientation) needs to be applied every time...
        val initialWingsuitRotation = createQuaternionFromEuler(0f, (Math.PI).toFloat(), (-Math.PI/2).toFloat()+Adjustments.yawAdjustment)
        val initialCanopyRotation = createQuaternionFromEuler(0f, (Math.PI).toFloat(), (-Math.PI/2).toFloat()+Adjustments.yawAdjustment)

        // Create wingsuit entity using tsimwingsuit.glb model with initial rotation
        wingsuitEntity = Entity.create(
            Mesh("tsimwingsuit.glb".toUri()),
            Transform(Pose(Vector3(0f), initialWingsuitRotation)),
            Scale(Vector3(WINGSUIT_SCALE)),
            Visible(false) // Start invisible
        )

        // Create canopy entity using cp2 model with initial rotation
        canopyEntity = Entity.create(
            Mesh("cp2.gltf".toUri()),
            Transform(Pose(Vector3(0f), initialCanopyRotation)),
            Scale(Vector3(CANOPY_SCALE)),
            Visible(false) // Start invisible
        )
        initialized = true
        Log.i(TAG, "WingsuitCanopySystem initialized")
    }

    private fun updateModelOrientationAndVisibility() {
        // Check if wingsuit/canopy models are enabled
        if (!VROptions.current.showWingsuitCanopy) {
            wingsuitEntity?.setComponent(Visible(false))
            canopyEntity?.setComponent(Visible(false))
            return
        }

        val motionEstimator = Services.location.motionEstimator
        val lastUpdate = motionEstimator.getLastUpdate()

        // Get head position to place models near it
        val headPose = HeadPoseUtil.getHeadPose(systemManager) ?: return
        if (headPose == Pose()) return

        // Position models below and in front of the head
        val wingsuitPosition = headPose.t + Vector3(0f, MODEL_HEIGHT_OFFSET, 0f)

        val ofst = if (isRealCanopyOrientation) -0.3f else MODEL_HEIGHT_OFFSET
        val canopyPosition = headPose.t + Vector3(0f, ofst, 0f)

        // Check if we have fresh GPS data and location is valid
        if (lastUpdate == null || !Services.location.isFresh) {
            // Hide both models when no GPS data
            wingsuitEntity?.setComponent(Visible(false))
            canopyEntity?.setComponent(Visible(false))
            return
        }

        // Check if GPS data has been updated since last frame
        val hasNewGpsData = lastUpdate.millis != lastGpsTime
        if (hasNewGpsData) {
            lastGpsTime = lastUpdate.millis
        }

        // Check if moving fast enough
        if (lastUpdate.groundSpeed() < MIN_SPEED_THRESHOLD) {
            // Hide both models when not moving
            wingsuitEntity?.setComponent(Visible(false))
            canopyEntity?.setComponent(Visible(false))
            return
        }

        // Determine which model to show based on flight mode
        val flightMode = Services.flightComputer.flightMode
        val isCanopyMode = flightMode == FlightMode.MODE_CANOPY || flightMode == FlightMode.MODE_GROUND

        if (isCanopyMode) {
            // Show canopy, hide wingsuit
            wingsuitEntity?.setComponent(Visible(false))
            updateCanopyOrientation(motionEstimator, canopyPosition, hasNewGpsData)
        } else {
            // Show wingsuit, hide canopy (for all other flight modes)
            canopyEntity?.setComponent(Visible(false))
            updateWingsuitOrientation(motionEstimator, wingsuitPosition, hasNewGpsData)
        }
    }

    private fun updateWingsuitOrientation(motionEstimator: MotionEstimator, position: Vector3, hasNewGpsData: Boolean) {
        val wingsuitEntity = this.wingsuitEntity ?: return

        // Use cached predicted state from 90Hz GpsToWorldTransform updates with interpolated wingsuit parameters
        val (velocity, rollRad) = when (motionEstimator) {
            is KalmanFilter3D -> {
                // Get cached predicted state from last predictDelta() call with 90Hz interpolated roll
                val predictedState = motionEstimator.getCachedPredictedState(System.currentTimeMillis())
                Pair(predictedState.velocity, predictedState.roll.toFloat())
            }
            is SimpleEstimator -> {
                Pair(motionEstimator.v, 0f)
            }
            else -> {
                // Fallback to GPS velocity if not a known estimator type
                val lastUpdate = motionEstimator.getLastUpdate()
                if (lastUpdate != null) {
                    Pair(com.platypii.baselinexr.util.tensor.Vector3(lastUpdate.vE, lastUpdate.climb, lastUpdate.vN), 0f)
                } else {
                    return // No data available
                }
            }
        }

        // Calculate pitch (angle from horizontal)
        val horizontalSpeed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z).toFloat()
        val pitchRad = atan2(-velocity.y.toFloat(), horizontalSpeed) // Negative because up is positive

        // Calculate flight yaw for +Z forward coordinate system
        // For +Z forward: yaw = atan2(vZ, vX) where vZ=North, vX=East
        val flightYaw = -atan2(velocity.z.toFloat(), velocity.x.toFloat())

        // Calculate AOA from measured KL/KD coefficients using polar data
        val aoaDeg = when (motionEstimator) {
            is KalmanFilter3D -> {
                val predictedState = motionEstimator.getCachedPredictedState(System.currentTimeMillis())
                val lastUpdate = motionEstimator.getLastUpdate()
                if (lastUpdate != null) {
                    PolarLibrary.convertKlKdToAOA(predictedState.kl, predictedState.kd, lastUpdate.altitude_gps,
                        PolarLibrary.AURA_FIVE_POLAR)
                } else {
                    10.0 // Default AOA if no GPS data
                }
            }
            else -> 10.0 // Default AOA for other estimators
        }
        val aoa = aoaDeg * Math.PI / 180 // Convert to radians

        // Create two separate rotations and combine them
        // 1. Model  offset
        val modelRotation = createQuaternionFromEuler(0f, (Math.PI).toFloat()+aoa.toFloat(), (Math.PI).toFloat())

        // 2. Flight attitude
        val attitudeRotation = createQuaternionFromEuler(rollRad, pitchRad, (-flightYaw -Math.PI/2 -  Adjustments.yawAdjustment   ).toFloat())
        // val controlRotation = createQuaternionFromEuler(0f, aoa.toFloat(), 0f)
        // Combine rotations: first apply offset, then attitude

        // val wsRotation = multiplyQuaternions(attitudeRotation, controlRotation)

        val rotation = multiplyQuaternions(modelRotation, attitudeRotation)

        // Use enlarged scale if enabled
        val scale = if (isEnlarged) WINGSUIT_SCALE * ENLARGEMENT_FACTOR else WINGSUIT_SCALE

        // Update wingsuit position and orientation
        wingsuitEntity.setComponents(
            listOf(
                Scale(Vector3(scale)),
                Transform(Pose(position, rotation)),
                Visible(true)
            )
        )

        Log.d(TAG, "Wingsuit: vE=${velocity.x.toInt()}, vN=${velocity.z.toInt()}, climb=${velocity.y.toInt()}, pitch=${Math.toDegrees(pitchRad.toDouble()).toInt()}°, heading=${Math.toDegrees(flightYaw.toDouble()).toInt()}°, roll=${Math.toDegrees(rollRad.toDouble()).toInt()}°, AOA=${aoaDeg.toInt()}° | Model forward=+Z, left=+X")
    }

    private fun updateCanopyOrientation(motionEstimator: MotionEstimator, position: Vector3, hasNewGpsData: Boolean) {
        val canopyEntity = this.canopyEntity ?: return

        // Compute advanced canopy orientation using physics modeling
        val canopyPoint = when (motionEstimator) {
            is KalmanFilter3D -> {
                val predictedState = motionEstimator.getCachedPredictedState(System.currentTimeMillis())
                val lastUpdate = motionEstimator.getLastUpdate()
                if (lastUpdate != null) {
                    computeCanopyOrientation(predictedState, lastUpdate.altitude_gps, System.currentTimeMillis(), isRealCanopyOrientation)
                } else {
                    return // No GPS data available
                }
            }
            else -> {
                // Fallback to simple orientation for other estimators
                val velocity = when (motionEstimator) {
                    is SimpleEstimator -> motionEstimator.v
                    else -> {
                        val lastUpdate = motionEstimator.getLastUpdate()
                        if (lastUpdate != null) {
                            com.platypii.baselinexr.util.tensor.Vector3(lastUpdate.vE, lastUpdate.climb, lastUpdate.vN)
                        } else {
                            return // No data available
                        }
                    }
                }

                // Simple physics for non-Kalman estimators
                val horizontalSpeed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z).toFloat()
                val pitchRad = atan2(-velocity.y.toFloat(), horizontalSpeed)
                val flightYaw = -atan2(velocity.z.toFloat(), velocity.x.toFloat())
                val rollRad = 0f // No roll data for simple estimators

                // Create simple rotations
                val modelRotation = createQuaternionFromEuler(0f, (Math.PI).toFloat(), (Math.PI).toFloat())
                val attitudeRotation = createQuaternionFromEuler(rollRad, pitchRad, (-flightYaw - Math.PI/2 - Adjustments.yawAdjustment).toFloat())
                val rotation = multiplyQuaternions(modelRotation, attitudeRotation)

                // Update canopy with simple physics and return
                val scale = if (isRealCanopyOrientation) 4.0f else CANOPY_SCALE
                canopyEntity.setComponents(
                    listOf(
                        Scale(Vector3(scale)),
                        Transform(Pose(position, rotation)),
                        Visible(true)
                    )
                )
                return
            }
        }

        // Use advanced canopy physics results for orientation
        // Calculate flight attitude from velocity components in canopy point
        val horizontalSpeed = sqrt(canopyPoint.vcpE * canopyPoint.vcpE + canopyPoint.vcpN * canopyPoint.vcpN).toFloat()
        val pitchRad = atan2( canopyPoint.vcpD.toFloat(), horizontalSpeed) // Negative because up is positive
        val flightYaw = -atan2(canopyPoint.vcpN.toFloat(), canopyPoint.vcpE.toFloat()) // +Z forward coordinate system
        val rollRad = canopyPoint.roll.toFloat() // Use computed roll from canopy physics

        // Use computed AOA from canopy physics
        val aoa = (canopyPoint.cpaoa ).toFloat()

        // Create rotations using physics-based values
        // 1. Model offset with computed AOA
        val modelRotation = createQuaternionFromEuler(0f, (Math.PI).toFloat() + aoa, (Math.PI).toFloat())

        // 2. Flight attitude with physics-based roll
        val attitudeRotation = createQuaternionFromEuler(rollRad, pitchRad, (-flightYaw - Math.PI/2 - Adjustments.yawAdjustment).toFloat())

        // Combine rotations
        val rotation = multiplyQuaternions(modelRotation, attitudeRotation)


        // Use enlarged scale if enabled
        val scale = if (isRealCanopyOrientation) 1.0f else CANOPY_SCALE

        // Position calculation based on canopy orientation mode
        val finalPosition = position //if (isRealCanopyOrientation) {
            // Real canopy mode: Position canopy model at its actual location using physics
            // The model is centered on top of canopy, so we need to account for line length


        // Update canopy position and orientation
        canopyEntity.setComponents(
            listOf(
                Scale(Vector3(scale)),
                Transform(Pose(finalPosition, rotation)),
                Visible(true)
            )
        )

        //Log.d(TAG, "Canopy: vE=${velocity.x}, vN=${velocity.z}, climb=${velocity.y}, pitch=${Math.toDegrees(pitchRad.toDouble())}°, yaw=${Math.toDegrees(yawRad)}°, roll=${Math.toDegrees(rollRad.toDouble())}°")
    }

    /**
     * Create a quaternion from Euler angles (roll, pitch, yaw) in radians
     * Meta Spatial uses Y-up coordinate system
     * Order: Y(yaw) * X(pitch) * Z(roll) - intrinsic rotations
     */
    private fun createQuaternionFromEuler(roll: Float, pitch: Float, yaw: Float): Quaternion {
        // Convert to half angles
        val halfYaw = yaw * 0.5f
        val halfPitch = pitch * 0.5f
        val halfRoll = roll * 0.5f

        val cy = cos(halfYaw)
        val sy = sin(halfYaw)
        val cp = cos(halfPitch)
        val sp = sin(halfPitch)
        val cr = cos(halfRoll)
        val sr = sin(halfRoll)


        // Quaternion multiplication: q_yaw * q_pitch * q_roll
        // Y-up, Order: Y(yaw) * X(pitch) * Z(roll) - intrinsic rotations
        val qw = cy * cp * cr + sy * sp * sr
        val qx = cy * sp * cr + sy * cp * sr
        val qy = sy * cp * cr - cy * sp * sr
        val qz = cy * cp * sr - sy * sp * cr

        return Quaternion(qx, qy, qz, qw)
    }

    /**
     * Multiply two quaternions: q1 * q2
     * This applies q1 first, then q2
     */
    private fun multiplyQuaternions(q1: Quaternion, q2: Quaternion): Quaternion {
        val w = q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z
        val x = q1.w * q2.x + q1.x * q2.w + q1.y * q2.z - q1.z * q2.y
        val y = q1.w * q2.y - q1.x * q2.z + q1.y * q2.w + q1.z * q2.x
        val z = q1.w * q2.z + q1.x * q2.y - q1.y * q2.x + q1.z * q2.w

        return Quaternion(x, y, z, w)
    }

    fun setEnlarged(enlarged: Boolean) {
        isEnlarged = enlarged
    }

    fun cleanup() {
        wingsuitEntity = null
        canopyEntity = null
        initialized = false
        Log.i(TAG, "WingsuitCanopySystem cleaned up")
    }
}
