package com.platypii.baselinexr

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.core.Entity
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.datamodelinspector.DataModelInspectorFeature
import com.meta.spatial.debugtools.HotReloadFeature
import com.meta.spatial.ovrmetrics.OVRMetricsDataModel
import com.meta.spatial.ovrmetrics.OVRMetricsFeature
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.PanelRegistration
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
import com.platypii.baselinexr.measurements.MLocation
import com.platypii.baselinexr.ui.HudPanelController

class BaselineActivity : AppSystemActivity() {

  var glxfLoaded = false
  private val activityScope = CoroutineScope(Dispatchers.Main)
  private var gltfxEntity: Entity? = null
  var terrainSystem: TerrainSystem? = null
  private var directionArrowSystem: DirectionArrowSystem? = null
  var hudSystem: HudSystem? = null
  private var flightStatsSystem: FlightStatsSystem? = null
  private var targetPanelSystem: TargetPanel? = null
  private var portalSystem: PortalSystem? = null
  private var miniMapPanel: MiniMapPanel? = null
  private val gpsTransform = GpsToWorldTransform()
  private var locationSubscriber: ((MLocation) -> Unit)? = null
  private var hudPanelController: HudPanelController? = null

  override fun registerFeatures(): List<SpatialFeature> {
    val features =
        mutableListOf<SpatialFeature>(
          VRFeature(this),
        )
    if (BuildConfig.DEBUG) {
      features.add(CastInputForwardFeature(this))
      features.add(HotReloadFeature(this))
      features.add(OVRMetricsFeature(this, OVRMetricsDataModel { numberOfMeshes() }))
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

    // Initialize panel controllers
    hudPanelController = HudPanelController(this)

    // Create systems
    hudSystem = HudSystem(gpsTransform)
    flightStatsSystem = FlightStatsSystem()
    directionArrowSystem = DirectionArrowSystem()
    targetPanelSystem = TargetPanel(gpsTransform)
    portalSystem = PortalSystem(gpsTransform, this)
    miniMapPanel = MiniMapPanel()

    // Register systems
    systemManager.registerSystem(hudSystem!!)
    systemManager.registerSystem(flightStatsSystem!!)
    systemManager.registerSystem(directionArrowSystem!!)
    systemManager.registerSystem(targetPanelSystem!!)
    systemManager.registerSystem(portalSystem!!)
    systemManager.registerSystem(miniMapPanel!!)

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
    flightStatsSystem?.cleanup()
    terrainSystem?.cleanup()
    directionArrowSystem?.cleanup()
    targetPanelSystem?.cleanup()
    portalSystem?.cleanup()
    miniMapPanel?.cleanup()

    // Clean up panel controllers to prevent memory leaks
    hudPanelController = null

    super.onDestroy()
  }

  override fun onSceneReady() {
    super.onSceneReady()

    scene.setLightingEnvironment(
      ambientColor = VROptions.AMBIENT_COLOR,
      sunColor     = VROptions.SUN_COLOR,
      sunDirection = VROptions.SUN_DIRECTION,
      environmentIntensity = VROptions.ENVIRONMENT_INTENSITY
    )
    scene.updateIBLEnvironment("environment.env")
  }

  override fun registerPanels(): List<PanelRegistration> {
    return listOf(
        PanelRegistration(R.layout.hud) {
          config {
            themeResourceId = R.style.PanelAppThemeTransparent
            includeGlass = false
            enableTransparent = true
          }
          panel {
            hudPanelController?.setupPanel(rootView)
          }
        },
        PanelRegistration(R.layout.flight_stats) {
          config {
            themeResourceId = R.style.PanelAppThemeTransparent
            includeGlass = false
            enableTransparent = true
          }
          panel {
            // Set up flight stats references
            val altitudeLabel = rootView?.findViewById<TextView>(R.id.altitude)
            val horizontalSpeedLabel = rootView?.findViewById<TextView>(R.id.horizontal_speed)
            val verticalSpeedLabel = rootView?.findViewById<TextView>(R.id.vertical_speed)
            val glideLabel = rootView?.findViewById<TextView>(R.id.glide)
            flightStatsSystem?.setLabels(altitudeLabel, horizontalSpeedLabel, verticalSpeedLabel, glideLabel)
          }
        },
        PanelRegistration(R.layout.target_panel) {
          config {
            themeResourceId = R.style.PanelAppThemeTransparent
            includeGlass = false
            enableTransparent = true
          }
          panel {
            // Set up target panel distance label
            val distanceLabel = rootView?.findViewById<TextView>(R.id.distance_text)
            targetPanelSystem?.setLabel(distanceLabel)
          }
        },
        PanelRegistration(R.layout.minimap) {
          config {
            themeResourceId = R.style.PanelAppThemeTransparent
            includeGlass = false
            enableTransparent = true
          }
          panel {
            // Set up minimap references
            val minimapImage = rootView?.findViewById<android.widget.ImageView>(R.id.minimap_image)
            val redDot = rootView?.findViewById<android.view.View>(R.id.red_dot)
            val blueDot = rootView?.findViewById<android.view.View>(R.id.blue_dot)
            miniMapPanel?.setViews(minimapImage, redDot, blueDot)
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

    fun handleForwardOrientationButton() {
        // Get current head transform to determine yaw
        val head = systemManager
            .tryFindSystem<PlayerBodyAttachmentSystem>()
            ?.tryGetLocalPlayerAvatarBody()
            ?.head
        head?.let {
            val headTransform = it.tryGetComponent<Transform>()
            headTransform?.let { transform ->
                val currentHeadYaw = SpatialUtils.extractYawFromQuaternion(transform.transform.q)

                // Get current location with velocity data
                val currentLocation = Services.location.lastLoc
                currentLocation?.let { loc ->
                    // Calculate velocity bearing (direction of movement)
                    val velocityBearing = loc.bearing() // This returns degrees
                    val velocityBearingRad = Math.toRadians(velocityBearing)
                    val headBearingRad = currentHeadYaw // Already in radians from extractYawFromQuaternion

                    // Set yaw adjustment so that when looking in velocity direction, world is oriented north
                    // We want: head_direction + yaw_adjustment = north (0)
                    // So: yaw_adjustment = -head_direction + velocity_to_north_correction
                    Adjustments.yawAdjustment = headBearingRad - velocityBearingRad
                    Adjustments.saveYawAdjustment(this@BaselineActivity)

//                    Log.d(TAG, "Orient head: " + headBearingRad + " vel: " + velocityBearingRad + " yawAdj: " + Adjustments.yawAdjustment)
                }
            }
        }
    }

    fun handleTailOrientationButton() {
        // Get current head transform to determine yaw
        val head = systemManager
            .tryFindSystem<PlayerBodyAttachmentSystem>()
            ?.tryGetLocalPlayerAvatarBody()
            ?.head
        head?.let {
            val headTransform = it.tryGetComponent<Transform>()
            headTransform?.let { transform ->
                val currentHeadYaw = SpatialUtils.extractYawFromQuaternion(transform.transform.q)

                // Get current location with velocity data
                val currentLocation = Services.location.lastLoc
                currentLocation?.let { loc ->
                    // Calculate velocity bearing (direction of movement)
                    val velocityBearing = loc.bearing() // This returns degrees
                    val velocityBearingRad = Math.toRadians(velocityBearing)
                    val headBearingRad = currentHeadYaw // Already in radians from extractYawFromQuaternion

                    // Set yaw adjustment so that when looking in velocity direction, world is oriented north
                    // We want: head_direction + yaw_adjustment = north (0)
                    // So: yaw_adjustment = -head_direction + velocity_to_north_correction
                    Adjustments.yawAdjustment = Math.PI + headBearingRad - velocityBearingRad
                    Adjustments.saveYawAdjustment(this@BaselineActivity)
                }
            }
        }
    }

  private fun setupLocationUpdates() {
    locationSubscriber = { loc ->
      Log.d(TAG, "Location: $loc")

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
