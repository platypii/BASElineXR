package com.platypii.baselinexr

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.core.Entity
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.datamodelinspector.DataModelInspectorFeature
import com.meta.spatial.debugtools.HotReloadFeature
import com.meta.spatial.mruk.AnchorProceduralMesh
import com.meta.spatial.mruk.AnchorProceduralMeshConfig
import com.meta.spatial.mruk.MRUKFeature
import com.meta.spatial.mruk.MRUKLabel
import com.meta.spatial.mruk.MRUKLoadDeviceResult
import com.meta.spatial.ovrmetrics.OVRMetricsDataModel
import com.meta.spatial.ovrmetrics.OVRMetricsFeature
import com.meta.spatial.physics.PhysicsFeature
import com.meta.spatial.physics.PhysicsOutOfBoundsSystem
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
import com.platypii.baselinexr.util.Convert

class BaselineActivity : AppSystemActivity() {

  var glxfLoaded = false
  private val activityScope = CoroutineScope(Dispatchers.Main)
  private var gltfxEntity: Entity? = null
  private var sphereEntity: Entity? = null
  private var ballShooter: BallShooter? = null
  private var terrainEntity: Entity? = null
  private var terrainUpdateSystem: TerrainUpdateSystem? = null
  private var debug = false
  private lateinit var procMeshSpawner: AnchorProceduralMesh
  private lateinit var mrukFeature: MRUKFeature
  private val gpsTransform = GpsToWorldTransform()

  override fun registerFeatures(): List<SpatialFeature> {
    mrukFeature = MRUKFeature(this, systemManager)
    val features =
        mutableListOf<SpatialFeature>(
          PhysicsFeature(spatial),
          VRFeature(this),
          mrukFeature
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

    // Add a system to remove objects that fall 100 meters below the floor
    systemManager.registerSystem(
        PhysicsOutOfBoundsSystem(spatial).apply { setBounds(minY = -100.0f) })
    systemManager.registerSystem(HudSystem())
//    val flightPathSystem = FlightPathTrailSystem(this, gpsTransform)
//    systemManager.registerSystem(flightPathSystem)

    // NOTE: Here a material could be set as well to visualize the walls, ceiling, etc
    //       It is also possible to spawn procedural meshes for volumes
    procMeshSpawner =
        AnchorProceduralMesh(
            mrukFeature,
            mapOf(
                MRUKLabel.FLOOR to AnchorProceduralMeshConfig(null, true),
//                MRUKLabel.WALL_FACE to AnchorProceduralMeshConfig(null, true),
                MRUKLabel.CEILING to AnchorProceduralMeshConfig(null, true),
//                MRUKLabel.TABLE to AnchorProceduralMeshConfig(null, true),
                MRUKLabel.OTHER to AnchorProceduralMeshConfig(null, true)))

    // Enable MR mode
    systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)
    scene.enablePassthrough(true)

    // Load sphere GLTF directly
    sphereEntity = Entity.create(
      Mesh("sphere.gltf".toUri()),
      Visible(false)
    )

    // Load terrain GLTF directly
    terrainEntity = Entity.create(
      Mesh("eiger_terrain_0.5m.glb".toUri()),
      Visible(true)
    )

    // Create and register terrain update system for per-frame updates
    terrainEntity?.let {
      terrainUpdateSystem = TerrainUpdateSystem(it, gpsTransform)
      systemManager.registerSystem(terrainUpdateSystem!!)
    }

    loadGLXF().invokeOnCompletion {
      glxfLoaded = true

      // Get sphere mesh from directly loaded entity
      val mesh = sphereEntity!!.getComponent<Mesh>()
      ballShooter = BallShooter(mesh)
      systemManager.registerSystem(ballShooter!!)

      // Set mesh for flight path trail
//      flightPathSystem.setSphereMesh(mesh)

      if (checkSelfPermission(PERMISSION_USE_SCENE) != PackageManager.PERMISSION_GRANTED) {
        log("Scene permission has not been granted, requesting $PERMISSION_USE_SCENE")
        requestPermissions(arrayOf(PERMISSION_USE_SCENE), REQUEST_CODE_PERMISSION_USE_SCENE)
      } else {
        log("Scene permission has already been granted!")
        loadSceneFromDevice()
      }
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

  private fun loadSceneFromDevice() {
    log("Loading scene from device...")
    mrukFeature.loadSceneFromDevice().whenComplete { result: MRUKLoadDeviceResult, _ ->
      if (result != MRUKLoadDeviceResult.SUCCESS) {
        log("Error loading scene from device: ${result}")
      } else {
        log("Scene loaded from device")
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    procMeshSpawner.destroy()
  }

  override fun onSceneReady() {
    super.onSceneReady()

    scene.setLightingEnvironment(
      ambientColor = Vector3(0.9f),
      sunColor     = Vector3(0.9f),
      sunDirection = Vector3(0f,1f,0f),
      environmentIntensity = 0.01f
    )
    scene.updateIBLEnvironment("environment.env")
  }

  override fun onRequestPermissionsResult(
      requestCode: Int,
      permissions: Array<out String>,
      grantResults: IntArray
  ) {
    if (requestCode == REQUEST_CODE_PERMISSION_USE_SCENE &&
        permissions.size == 1 &&
        permissions[0] == PERMISSION_USE_SCENE
    ) {
      val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
      if (granted) {
        log("Use scene permission has been granted")
        loadSceneFromDevice()
      } else {
        log("Use scene permission was DENIED!")
      }
    }
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

            val latlngLabel = rootView?.findViewById<TextView>(R.id.lat_lng)
            latlngLabel?.text = Services.location.dataSource()
            val speedLabel = rootView?.findViewById<TextView>(R.id.speed)
            Services.location.locationUpdates.subscribeMain { loc ->
              val provider = Services.location.dataSource()
              latlngLabel?.text = provider + " " + loc.toStringSimple()
              speedLabel?.text = Convert.speed(loc.groundSpeed()) + "  " + Convert.distance(loc.altitude_gps)

              // Update the origin to the latest GPS location
              gpsTransform.setOrigin(loc)

              // Update the flight path system with the new location
//              val flightPathSystem = systemManager.findSystem<FlightPathTrailSystem>()
//              flightPathSystem?.onLocationUpdate(loc)
            }
            // TODO: unsubscribe later
          }
        })
  }

  fun onHoverButton(v: View, event: MotionEvent): Boolean {
    // don't shoot balls while hovering over the buttons
    when (event.action) {
      MotionEvent.ACTION_HOVER_ENTER -> {
        ballShooter?.enabled = false
      }
      MotionEvent.ACTION_HOVER_EXIT -> {
        ballShooter?.enabled = true
      }
    }
    return true
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
    const val PERMISSION_USE_SCENE: String = "com.oculus.permission.USE_SCENE"
    const val REQUEST_CODE_PERMISSION_USE_SCENE: Int = 1
    const val GLXF_SCENE = "GLXF_SCENE"
  }
}

fun log(msg: String) {
  Log.d(BaselineActivity.TAG, msg)
}
