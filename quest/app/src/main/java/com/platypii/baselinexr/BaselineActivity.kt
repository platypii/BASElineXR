package com.platypii.baselinexr

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
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
import com.meta.spatial.mruk.MRUKSystem
import com.meta.spatial.ovrmetrics.OVRMetricsDataModel
import com.meta.spatial.ovrmetrics.OVRMetricsFeature
import com.meta.spatial.physics.PhysicsFeature
import com.meta.spatial.physics.PhysicsOutOfBoundsSystem
import com.meta.spatial.runtime.LayerConfig
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BaselineActivity : AppSystemActivity() {

  var glxfLoaded = false
  private val activityScope = CoroutineScope(Dispatchers.Main)
  private var gltfxEntity: Entity? = null
  private var ballShooter: BallShooter? = null
  private var gotAllAnchors = false
  private var debug = false
  private lateinit var procMeshSpawner: AnchorProceduralMesh

  override fun registerFeatures(): List<SpatialFeature> {
    val features =
        mutableListOf<SpatialFeature>(
          PhysicsFeature(spatial),
          VRFeature(this),
          MRUKFeature(this, systemManager)
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
    systemManager.registerSystem(UiPanelUpdateSystem())

    val mrukSystem = systemManager.findSystem<MRUKSystem>()

    // NOTE: Here a material could be set as well to visualize the walls, ceiling, etc
    //       It is also possible to spawn procedural meshes for volumes
    procMeshSpawner =
        AnchorProceduralMesh(
            mrukSystem,
            mapOf(
                MRUKLabel.FLOOR to AnchorProceduralMeshConfig(null, true),
//                MRUKLabel.WALL_FACE to AnchorProceduralMeshConfig(null, true),
                MRUKLabel.CEILING to AnchorProceduralMeshConfig(null, true),
//                MRUKLabel.TABLE to AnchorProceduralMeshConfig(null, true),
                MRUKLabel.OTHER to AnchorProceduralMeshConfig(null, true)))

    // Enable MR mode
    systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)
    scene.enablePassthrough(true)

    loadGLXF().invokeOnCompletion {
      glxfLoaded = true
      val composition = glXFManager.getGLXFInfo(GLXF_SCENE)
      val bball = composition.getNodeByName("BasketBall").entity
      val mesh = bball.getComponent<Mesh>()
      ballShooter = BallShooter(mesh)
      systemManager.registerSystem(ballShooter!!)

      mrukSystem.addOnRoomAddedListener { room ->
        // If a room exists, it has a floor. Remove the default floor.
        val floor = composition.tryGetNodeByName("defaultFloor")
        floor!!.entity.destroy()
      }

      if (checkSelfPermission(PERMISSION_USE_SCENE) != PackageManager.PERMISSION_GRANTED) {
        log("Scene permission has not been granted, requesting " + PERMISSION_USE_SCENE)
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
    val mrukSystem = systemManager.findSystem<MRUKSystem>()
    log("Loading scene from device...")
    mrukSystem.loadSceneFromDevice().whenComplete { result: MRUKLoadDeviceResult, _ ->
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
        ambientColor = Vector3(0f),
        sunColor = Vector3(7.0f, 7.0f, 7.0f),
        sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
        environmentIntensity = 0.3f)
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
        PanelRegistration(R.layout.about) {
          config {
            themeResourceId = R.style.PanelAppThemeTransparent
            includeGlass = false
//            layerConfig = LayerConfig()
            enableTransparent = true
          }
          panel {
            val debugButton = rootView?.findViewById<Button>(R.id.toggle_debug)
            debugButton?.setOnClickListener({
              debug = !debug
              spatial.enablePhysicsDebugLines(debug)
            })
            debugButton?.setOnHoverListener(::onHoverButton)

            val exitButton = rootView?.findViewById<Button>(R.id.exit_button)
            exitButton?.setOnClickListener({
              finish()
            })
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
          Uri.parse("apk:///scenes/Composition.glxf"),
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
