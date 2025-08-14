package com.platypii.baselinexr

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.core.Entity
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
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
import com.platypii.baselinexr.ui.HudPanelController

class BaselineActivity : AppSystemActivity() {

  var glxfLoaded = false
  private val activityScope = CoroutineScope(Dispatchers.Main)
  private var gltfxEntity: Entity? = null
  var terrainSystem: TerrainSystem? = null
  private var directionArrowSystem: DirectionArrowSystem? = null
  var hudSystem: HudSystem? = null
  private var altimeterSystem: AltimeterSystem? = null
  private var speedometerSystem: SpeedometerSystem? = null
  private var targetPanelSystem: TargetPanel? = null
  private var portalSystem: PortalSystem? = null
  private val gpsTransform = GpsToWorldTransform()
  private var locationSubscriber: ((com.platypii.baselinexr.measurements.MLocation) -> Unit)? = null
  private var hudPanelController: com.platypii.baselinexr.ui.HudPanelController? = null

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

    // Initialize panel controllers
    hudPanelController = HudPanelController(this)

    // Create systems
    hudSystem = HudSystem(gpsTransform)
    altimeterSystem = AltimeterSystem()
    speedometerSystem = SpeedometerSystem()
    directionArrowSystem = DirectionArrowSystem()
    targetPanelSystem = TargetPanel(gpsTransform)
    portalSystem = PortalSystem(gpsTransform, this, this)

    // Register systems
    systemManager.registerSystem(hudSystem!!)
    systemManager.registerSystem(altimeterSystem!!)
    systemManager.registerSystem(speedometerSystem!!)
    systemManager.registerSystem(directionArrowSystem!!)
    systemManager.registerSystem(targetPanelSystem!!)
    systemManager.registerSystem(portalSystem!!)

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
    portalSystem?.cleanup()

    // Clean up panel controllers to prevent memory leaks
    hudPanelController = null

    super.onDestroy()
  }

  override fun onSceneReady() {
    super.onSceneReady()

    scene.setLightingEnvironment(
      ambientColor = Vector3(01.4f),
      sunColor     = Vector3(1f),
      sunDirection = Vector3(-4f,10f,-2f),
      environmentIntensity = 0.01f
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
          panel {
            // Set up target panel distance label
            val distanceLabel = rootView?.findViewById<TextView>(R.id.distance_text)
            targetPanelSystem?.setLabel(distanceLabel)
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
