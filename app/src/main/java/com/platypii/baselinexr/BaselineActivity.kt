package com.platypii.baselinexr

import android.os.Bundle
import android.util.Log
import android.widget.Button
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
import com.meta.spatial.physics.PhysicsFeature

class BaselineActivity : AppSystemActivity() {

  var glxfLoaded = false
  private val activityScope = CoroutineScope(Dispatchers.Main)
  private var gltfxEntity: Entity? = null
  private var sphereEntity: Entity? = null
  private var ballShooter: BallShooter? = null
  private var terrainSystem: TerrainSystem? = null
  private val gpsTransform = GpsToWorldTransform()

  override fun registerFeatures(): List<SpatialFeature> {
    val features =
        mutableListOf<SpatialFeature>(
          PhysicsFeature(spatial),
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

    Services.create(this)

    systemManager.registerSystem(HudSystem(gpsTransform))
    systemManager.registerSystem(AltimeterSystem())
    systemManager.registerSystem(SpeedometerSystem())
//    val flightPathSystem = FlightPathTrailSystem(this, gpsTransform)
//    systemManager.registerSystem(flightPathSystem)

    // Enable MR mode
    systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)
    scene.enablePassthrough(true)

    // Load sphere GLTF directly
    sphereEntity = Entity.create(
      Mesh("sphere.gltf".toUri()),
      Visible(false)
    )

    // Create and register terrain rendering system
    terrainSystem = TerrainSystem(gpsTransform, this)
    terrainSystem!!.initialize()
    systemManager.registerSystem(terrainSystem!!)

    loadGLXF().invokeOnCompletion {
      glxfLoaded = true

      // Get sphere mesh from directly loaded entity
//      val mesh = sphereEntity!!.getComponent<Mesh>()
//      ballShooter = BallShooter(mesh)
//      systemManager.registerSystem(ballShooter!!)

      // Set mesh for flight path trail
//      flightPathSystem.setSphereMesh(mesh)
    }
  }

  override fun onStart() {
    super.onStart()
    Services.start(this)
  }

  override fun onStop() {
    super.onStop()
    Services.stop()
  }

  override fun onSceneReady() {
    super.onSceneReady()

    scene.setLightingEnvironment(
      ambientColor = Vector3(1f),
      sunColor     = Vector3(1f),
      sunDirection = Vector3(0f,1f,0f),
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
//            layerConfig = LayerConfig()
            enableTransparent = true
          }
          panel {
            val exitButton = rootView?.findViewById<Button>(R.id.exit_button)
            exitButton?.setOnClickListener({
              finish()
            })

            // Set up HUD updates via HudSystem
            val latlngLabel = rootView?.findViewById<TextView>(R.id.lat_lng)
            val speedLabel = rootView?.findViewById<TextView>(R.id.speed)
            val hudSystem = systemManager.findSystem<HudSystem>()
            hudSystem?.setupLocationUpdates(this@BaselineActivity, latlngLabel, speedLabel)
          }
        },
        PanelRegistration(R.layout.altimeter) {
          config {
            themeResourceId = R.style.PanelAppThemeTransparent
            includeGlass = false
            enableTransparent = true
          }
          panel {
            // Set up altimeter updates via AltimeterSystem
            val altitudeLabel = rootView?.findViewById<TextView>(R.id.altitude)
            val altimeterSystem = systemManager.findSystem<AltimeterSystem>()
            altimeterSystem?.setupLocationUpdates(this@BaselineActivity, altitudeLabel)
          }
        },
        PanelRegistration(R.layout.speedometer) {
          config {
            themeResourceId = R.style.PanelAppThemeTransparent
            includeGlass = false
            enableTransparent = true
          }
          panel {
            // Set up speedometer updates via SpeedometerSystem
            val speedLabel = rootView?.findViewById<TextView>(R.id.speed)
            val speedometerSystem = systemManager.findSystem<SpeedometerSystem>()
            speedometerSystem?.setupLocationUpdates(this@BaselineActivity, speedLabel)
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

  companion object {
    const val TAG = "BaselineActivityDebug"
    const val GLXF_SCENE = "GLXF_SCENE"
  }
}

fun log(msg: String) {
  Log.d(BaselineActivity.TAG, msg)
}
