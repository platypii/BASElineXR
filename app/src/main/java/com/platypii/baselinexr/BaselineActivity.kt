package com.platypii.baselinexr

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.core.Color4
import com.meta.spatial.core.Entity
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.datamodelinspector.DataModelInspectorFeature
import com.meta.spatial.debugtools.HotReloadFeature
import com.meta.spatial.ovrmetrics.OVRMetricsDataModel
import com.meta.spatial.ovrmetrics.OVRMetricsFeature
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import com.meta.spatial.core.PerformanceLevel
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Transform
import com.platypii.baselinexr.location.LocationStatus

class BaselineActivity : AppSystemActivity() {

  var glxfLoaded = false
  private val activityScope = CoroutineScope(Dispatchers.Main)
  private var gltfxEntity: Entity? = null
  private var terrainSystem: TerrainSystem? = null
  private var directionArrowSystem: DirectionArrowSystem? = null
  private var hudSystem: HudSystem? = null
  private var altimeterSystem: AltimeterSystem? = null
  private var speedometerSystem: SpeedometerSystem? = null
  private var targetPanelSystem: TargetPanel? = null
  private val gpsTransform = GpsToWorldTransform()
  private var locationSubscriber: ((com.platypii.baselinexr.measurements.MLocation) -> Unit)? = null

  override fun registerFeatures(): List<SpatialFeature> {
    val features =
        mutableListOf<SpatialFeature>(
          VRFeature(this),
        )
    if (BuildConfig.DEBUG) {
      features.add(CastInputForwardFeature(this))
      features.add(HotReloadFeature(this))
      features.add(OVRMetricsFeature(this, OVRMetricsDataModel() { numberOfMeshes() }))
      features.add(DataModelInspectorFeature(spatial, this.componentManager))
    }
    return features
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Set CPU/GPU performance to SustainedHigh for better performance
    // This allows CPU level 4-6 and GPU level 3-5
    spatial.setPerformanceLevel(PerformanceLevel.SUSTAINED_HIGH)

    Services.create(this)

    // Load saved adjustments
    Adjustments.loadAdjustments(this)

    // Create systems
    hudSystem = HudSystem(gpsTransform)
    altimeterSystem = AltimeterSystem()
    speedometerSystem = SpeedometerSystem()
    directionArrowSystem = DirectionArrowSystem(gpsTransform)
    targetPanelSystem = TargetPanel(gpsTransform)

    // Register systems
    systemManager.registerSystem(hudSystem!!)
    systemManager.registerSystem(altimeterSystem!!)
    systemManager.registerSystem(speedometerSystem!!)
    systemManager.registerSystem(directionArrowSystem!!)
    systemManager.registerSystem(targetPanelSystem!!)

    // Set up centralized location updates
    setupLocationUpdates()
//    val flightPathSystem = FlightPathTrailSystem(this, gpsTransform)
//    systemManager.registerSystem(flightPathSystem)

    // Enable MR mode
    systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)
    scene.enablePassthrough(true)

    // Create and register terrain rendering system
    terrainSystem = TerrainSystem(gpsTransform, this)
    terrainSystem!!.initialize()
    systemManager.registerSystem(terrainSystem!!)

    loadGLXF().invokeOnCompletion {
      glxfLoaded = true
    }
  }

  override fun onStart() {
    super.onStart()
    Services.start(this)
  }

  override fun onStop() {
    Log.i(TAG, "Stopping...")
    super.onStop()
    Services.stop()
  }

  override fun onDestroy() {
    // Clean up location subscription
    locationSubscriber?.let { subscriber ->
      Services.location.locationUpdates.unsubscribeMain(subscriber)
      locationSubscriber = null
    }

    // Clean up all systems that have cleanup methods
    hudSystem?.cleanup()
    altimeterSystem?.cleanup()
    speedometerSystem?.cleanup()
    terrainSystem?.cleanup()
    directionArrowSystem?.cleanup()
    targetPanelSystem?.cleanup()

    super.onDestroy()
  }

  override fun onSceneReady() {
    super.onSceneReady()

    scene.setLightingEnvironment(
      ambientColor = Vector3(0.9f),
      sunColor     = Vector3(1f),
      sunDirection = Vector3(1f,10f,1f),
      environmentIntensity = 0.02f
    )
    scene.updateIBLEnvironment("environment.env")
  }

  override fun registerPanels(): List<PanelRegistration> {
    return listOf(
        PanelRegistration(R.layout.hud) {
          config {
            themeResourceId = R.style.PanelAppThemeTransparent
            includeGlass = false
//            layerConfig = LayerConfig()
            enableTransparent = true
          }
          panel {
            val exitButton = rootView?.findViewById<Button>(R.id.exit_button)
            exitButton?.setOnClickListener({
//              Services.stop()
              finish()
            })

            // Add click listener to hudPanel to toggle extraControls visibility
            val hudPanel = rootView?.findViewById<android.widget.LinearLayout>(R.id.hudPanel)
            val extraControls = rootView?.findViewById<android.widget.LinearLayout>(R.id.extraControls)
            hudPanel?.setOnClickListener({
              extraControls?.let { controls ->
                if (controls.visibility == View.VISIBLE) {
                  controls.visibility = View.GONE
                } else {
                  controls.visibility = View.VISIBLE
                }
              }
            })

//            val resetNorthButton = rootView?.findViewById<Button>(R.id.reset_north_button)
//            resetNorthButton?.setOnClickListener({
//              // Get current head transform to determine yaw
//              val head = systemManager
//                  .tryFindSystem<PlayerBodyAttachmentSystem>()
//                  ?.tryGetLocalPlayerAvatarBody()
//                  ?.head
//              head?.let {
//                val headTransform = it.tryGetComponent<Transform>()
//                headTransform?.let { transform ->
//                  val currentYaw = transform.transform.q.toEuler().y
//                  gpsTransform.yawAdjustment = Math.toRadians(currentYaw.toDouble())
//                  gpsTransform.saveYawAdjustment(this@BaselineActivity)
//                }
//              }
//            })

            val yawPlusButton = rootView?.findViewById<Button>(R.id.yaw_plus_button)
            yawPlusButton?.setOnClickListener({
              // Increment yaw adjustment by 5 degrees (convert to radians)
              Adjustments.yawAdjustment += Math.toRadians(5.0)
              Adjustments.saveYawAdjustment(this@BaselineActivity)
            })

            val yawMinusButton = rootView?.findViewById<Button>(R.id.yaw_minus_button)
            yawMinusButton?.setOnClickListener({
              // Decrement yaw adjustment by 5 degrees (convert to radians)
              Adjustments.yawAdjustment -= Math.toRadians(5.0)
              Adjustments.saveYawAdjustment(this@BaselineActivity)
            })

            val fwdButton = rootView?.findViewById<Button>(R.id.fwd_button)
            fwdButton?.setOnClickListener({
              // Get current head transform to determine yaw
              val head = systemManager
                  .tryFindSystem<PlayerBodyAttachmentSystem>()
                  ?.tryGetLocalPlayerAvatarBody()
                  ?.head
              head?.let {
                val headTransform = it.tryGetComponent<Transform>()
                headTransform?.let { transform ->
                  val currentHeadYaw = transform.transform.q.toEuler().y

                  // Get current location with velocity data
                  val currentLocation = Services.location.lastLoc
                  currentLocation?.let { loc ->
                    // Calculate velocity bearing (direction of movement)
                    val velocityBearing = loc.bearing() // This returns degrees
                    val velocityBearingRad = Math.toRadians(velocityBearing)

                    // Calculate the difference between head direction and velocity direction
                    val headToVelocityDiff = velocityBearingRad - Math.toRadians(currentHeadYaw.toDouble())

                    // Set yaw adjustment so that when looking in velocity direction, world is oriented north
                    // We want: head_direction + yaw_adjustment = north (0)
                    // So: yaw_adjustment = -head_direction + velocity_to_north_correction
                    Adjustments.yawAdjustment = Math.toRadians(currentHeadYaw.toDouble()) - velocityBearingRad
                    Adjustments.saveYawAdjustment(this@BaselineActivity)
                  }
                }
              }
            })

            val tailButton = rootView?.findViewById<Button>(R.id.tail_button)
            tailButton?.setOnClickListener({
              // Get current head transform to determine yaw
              val head = systemManager
                  .tryFindSystem<PlayerBodyAttachmentSystem>()
                  ?.tryGetLocalPlayerAvatarBody()
                  ?.head
              head?.let {
                val headTransform = it.tryGetComponent<Transform>()
                headTransform?.let { transform ->
                  val currentHeadYaw = transform.transform.q.toEuler().y

                  // Get current location with velocity data
                  val currentLocation = Services.location.lastLoc
                  currentLocation?.let { loc ->
                    // Calculate velocity bearing (direction of movement)
                    val velocityBearing = loc.bearing() // This returns degrees
                    val velocityBearingRad = Math.toRadians(velocityBearing)

                    // Set yaw adjustment so that when looking opposite to velocity direction, world is oriented north
                    // This is 180 degrees opposite to the forward button
                    // Add π (180 degrees) to the velocity bearing to get the opposite direction
                    val oppositeVelocityBearingRad = velocityBearingRad + Math.PI

                    Adjustments.yawAdjustment = Math.toRadians(currentHeadYaw.toDouble()) - oppositeVelocityBearingRad
                    Adjustments.saveYawAdjustment(this@BaselineActivity)
                  }
                }
              }
            })

            val northButton = rootView?.findViewById<Button>(R.id.north_button)
            northButton?.setOnClickListener({
              Adjustments.northAdjustment += 500.0
              Adjustments.saveAdjustments(this@BaselineActivity)
            })

            val southButton = rootView?.findViewById<Button>(R.id.south_button)
            southButton?.setOnClickListener({
              Adjustments.northAdjustment -= 500.0
              Adjustments.saveAdjustments(this@BaselineActivity)
            })

            val eastButton = rootView?.findViewById<Button>(R.id.east_button)
            eastButton?.setOnClickListener({
              Adjustments.eastAdjustment += 500.0
              Adjustments.saveAdjustments(this@BaselineActivity)
            })

            val westButton = rootView?.findViewById<Button>(R.id.west_button)
            westButton?.setOnClickListener({
              Adjustments.eastAdjustment -= 500.0
              Adjustments.saveAdjustments(this@BaselineActivity)
            })

            val centerButton = rootView?.findViewById<Button>(R.id.center_button)
            centerButton?.setOnClickListener({
              Adjustments.northAdjustment = 0.0
              Adjustments.eastAdjustment = 0.0
              Adjustments.saveAdjustments(this@BaselineActivity)
            })

            // Set up HUD references
            val latlngLabel = rootView?.findViewById<TextView>(R.id.lat_lng)
            val speedLabel = rootView?.findViewById<TextView>(R.id.speed)
            hudSystem?.setLabels(latlngLabel, speedLabel)
          }
        },
        PanelRegistration(R.layout.altimeter) {
          config {
            themeResourceId = R.style.PanelAppThemeTransparent
            includeGlass = false
            enableTransparent = true
          }
          panel {
            // Set up altimeter references
            val altitudeLabel = rootView?.findViewById<TextView>(R.id.altitude)
            altimeterSystem?.setLabel(altitudeLabel)
          }
        },
        PanelRegistration(R.layout.speedometer) {
          config {
            themeResourceId = R.style.PanelAppThemeTransparent
            includeGlass = false
            enableTransparent = true
          }
          panel {
            // Set up speedometer references
            val speedLabel = rootView?.findViewById<TextView>(R.id.speed)
            speedometerSystem?.setLabel(speedLabel)
          }
        },
        PanelRegistration(R.layout.target_panel) {
          config {
            themeResourceId = R.style.PanelAppThemeTransparent
            includeGlass = false
            enableTransparent = true
          }
        })
  }

  private fun loadGLXF(): Job {
    gltfxEntity = Entity.create()
    return activityScope.launch {
      glXFManager.inflateGLXF(
          "apk:///scenes/Composition.glxf".toUri(),
          rootEntity = gltfxEntity!!,
          keyName = GLXF_SCENE
      )
    }
  }

  private fun setupLocationUpdates() {
    locationSubscriber = { loc ->
      log("Location: $loc")

      // Update GPS transform origin
      gpsTransform.setOrigin(loc)

      // Update LocationStatus helper
      LocationStatus.updateStatus(this)

      // Notify direction arrow system of location update (others update automatically)
      directionArrowSystem?.onLocation(loc)
    }
    Services.location.locationUpdates.subscribeMain(locationSubscriber!!)
  }

  companion object {
    const val TAG = "BaselineActivity"
    const val GLXF_SCENE = "GLXF_SCENE"
  }
}

fun log(msg: String) {
  Log.d(BaselineActivity.TAG, msg)
}
