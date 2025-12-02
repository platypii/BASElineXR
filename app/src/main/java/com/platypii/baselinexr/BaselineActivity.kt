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
import com.meta.spatial.runtime.StereoMode
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Equirect360ShapeOptions
import com.meta.spatial.toolkit.MediaPanelRenderOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.VideoSurfacePanelRegistration
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import com.platypii.baselinexr.replay.PlaybackTimeline
import com.platypii.baselinexr.replay.ReplayController
import com.platypii.baselinexr.replay.ReplayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import com.meta.spatial.core.PerformanceLevel
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.platypii.baselinexr.location.LocationStatus
import com.platypii.baselinexr.measurements.MLocation
import com.platypii.baselinexr.recording.ScreenRecordController
import com.platypii.baselinexr.ui.HudPanelController
import com.platypii.baselinexr.video.Video360Controller

class BaselineActivity : AppSystemActivity() {

    var glxfLoaded = false
    private val activityScope = CoroutineScope(Dispatchers.Main)
    private var gltfxEntity: Entity? = null
    var terrainSystem: TerrainSystem? = null
    var directionArrowSystem: DirectionArrowSystem? = null
    var wingsuitCanopySystem: WingsuitCanopySystem? = null
    var hudSystem: HudSystem? = null
    private var flightStatsSystem: FlightStatsSystem? = null
    private var atmosphericSystem: AtmosphericSystem? = null
    private var speedChartSystem: SpeedChartSystem? = null
    private var targetPanelSystem: TargetPanel? = null
    private var portalSystem: PortalSystem? = null
    private var miniMapPanel: MiniMapPanel? = null
    private val gpsTransform = GpsToWorldTransform()
    private var locationSubscriber: ((MLocation) -> Unit)? = null
    var hudPanelController: HudPanelController? = null
        private set
    private var video360Controller: Video360Controller? = null
    
    // Screen recording controller
    var screenRecordController: ScreenRecordController? = null
        private set
    
    // Replay controller for coordinated GPS + video playback
    var replayController: ReplayController? = null
        private set

    override fun registerFeatures(): List<SpatialFeature> {
        val features =
            mutableListOf<SpatialFeature>(
                VRFeature(this),
            )
        if (BuildConfig.DEBUG) {
            // Enable CastInputForwardFeature to support forwarding input from your computer into your Meta Quest headset.
            // This makes it possible to iterate on a Spatial SDK app without needing to don/doff the headset.
            // This requires usage of the MQDH Cast feature, which mirrors your headset to your computer.
//            features.add(CastInputForwardFeature(this))
            features.add(HotReloadFeature(this))
            features.add(OVRMetricsFeature(this, OVRMetricsDataModel { numberOfMeshes() }))
            features.add(DataModelInspectorFeature(spatial, this.componentManager))
        }
        return features
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize screen recording controller BEFORE super.onCreate()
        screenRecordController = ScreenRecordController(this)
        screenRecordController?.initialize()
        
        super.onCreate(savedInstanceState)

        // Set CPU/GPU performance to SustainedHigh for better performance
        // This allows CPU level 4-6 and GPU level 3-5
        spatial.setPerformanceLevel(PerformanceLevel.SUSTAINED_HIGH)

        Services.create(this)

        // Load saved adjustments
        Adjustments.loadAdjustments(this)

        // Request storage permissions if needed for 360 video
        if (VROptions.current.has360Video() && !Permissions.hasStoragePermissions(this)) {
            Permissions.requestStoragePermissions(this)
        }

        // Initialize panel controllers
        hudPanelController = HudPanelController(this)

        // Initialize 360 video controller only if configured
        if (VROptions.current.has360Video()) {
            video360Controller = Video360Controller(this)
            video360Controller?.initialize(VROptions.current)
        }
        
        // Initialize replay controller for coordinated GPS + video playback
        replayController = ReplayController(this)
        replayController?.videoController = video360Controller
        hudPanelController?.replayController = replayController
        
        // Connect ReplayManager callbacks to ReplayController
        ReplayManager.onGpsStartedCallback = {
            replayController?.onGpsStarted()
            // Enable replay UI when GPS replay starts
            hudPanelController?.enableReplayModeUI()
        }
        ReplayManager.onPlaybackCompletedCallback = {
            Log.i(TAG, "Playback completed - auto-stopping and resetting to beginning")
            replayController?.stop()
        }

        // Initialize SavedWindSystem and register with WindSystem singleton
        val savedWindSystem = com.platypii.baselinexr.wind.SavedWindSystem(this)
        com.platypii.baselinexr.wind.WindSystem.getInstance().setSavedWindSystem(savedWindSystem)

        // Create systems
        hudSystem = HudSystem()
        flightStatsSystem = FlightStatsSystem()
        atmosphericSystem = AtmosphericSystem()
        speedChartSystem = SpeedChartSystem()
        directionArrowSystem = DirectionArrowSystem()
        wingsuitCanopySystem = WingsuitCanopySystem()

        // Wind vectors now enabled by default - data collection complete
        // wingsuitCanopySystem?.setWindVectorsEnabled(false)

        targetPanelSystem = TargetPanel(gpsTransform)
        portalSystem = PortalSystem(gpsTransform, this)
        miniMapPanel = MiniMapPanel()

        // Register systems
        systemManager.registerSystem(hudSystem!!)
        systemManager.registerSystem(flightStatsSystem!!)
        systemManager.registerSystem(atmosphericSystem!!)
        systemManager.registerSystem(speedChartSystem!!)
        systemManager.registerSystem(directionArrowSystem!!)
        systemManager.registerSystem(wingsuitCanopySystem!!)
        systemManager.registerSystem(targetPanelSystem!!)
        systemManager.registerSystem(portalSystem!!)
        systemManager.registerSystem(miniMapPanel!!)
        
        // Debug: Log all input events to diagnose button click issues
        systemManager.registerSystem(InputDebugSystem())

        // Set up centralized location updates
        setupLocationUpdates()
//    val flightPathSystem = FlightPathTrailSystem(this, gpsTransform)
//    systemManager.registerSystem(flightPathSystem)

        // Enable MR mode
        systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)

        // Enable passthrough or 360 video based on configuration
        if (VROptions.current.has360Video()) {
            scene.enablePassthrough(false) // Disable passthrough when using 360 video
            Log.i(TAG, "360 video mode enabled, passthrough disabled")
            // Video panel entity will be created in onSceneReady()
        } else {
            scene.enablePassthrough(true)
            Log.i(TAG, "Passthrough mode enabled")
        }

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
        
        // Check if we should restart replay (both video and GPS have completed)
        if (ReplayManager.isReadyToRestart()) {
            Log.i(TAG, "Replay completed - restarting on headset wake")
            ReplayManager.prepareForRestart()
            // Video will restart when the panel is re-created
            // GPS will restart with Services.start below
        }
        
        // Notify replay controller of wake (handles coordinated GPS+video state)
        replayController?.onWake()
        
        // Resume video from sleep (prevents renderer errors)
        video360Controller?.onWake()
        
        Services.start(this)
    }

    override fun onStop() {
        Log.i(TAG, "Stopping...")
        
        // Notify replay controller of sleep (pauses GPS+video together)
        replayController?.onSleep()
        
        // Pause video for sleep (stops frame updates to surface)
        video360Controller?.onSleep()
        
        super.onStop()
        Services.stop()
    }

    override fun onDestroy() {
        // Clean up location subscription
        locationSubscriber?.let { subscriber ->
            Services.location.locationUpdates.unsubscribeMain(subscriber)
            locationSubscriber = null
        }

        // Reset replay state
        ReplayManager.reset()

        // Clean up all systems that have cleanup methods
        hudSystem?.cleanup()
        flightStatsSystem?.cleanup()
        atmosphericSystem?.cleanup()
        speedChartSystem?.cleanup()
        terrainSystem?.cleanup()
        directionArrowSystem?.cleanup()
        wingsuitCanopySystem?.cleanup()
        targetPanelSystem?.cleanup()
        portalSystem?.cleanup()
        miniMapPanel?.cleanup()

        // Clean up video controller
        video360Controller?.release()
        video360Controller = null

        // Clean up screen record controller
        screenRecordController?.release()
        screenRecordController = null

        // Clean up panel controllers to prevent memory leaks
        hudPanelController = null

        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Forward to screen record controller for MediaProjection permission handling
        screenRecordController?.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSceneReady() {
        super.onSceneReady()

        scene.setLightingEnvironment(
            ambientColor = VROptions.AMBIENT_COLOR,
            sunColor = VROptions.SUN_COLOR,
            sunDirection = VROptions.SUN_DIRECTION,
            environmentIntensity = VROptions.ENVIRONMENT_INTENSITY
        )
        scene.updateIBLEnvironment("environment.env")

        // Create 360 video panel entity if configured
        // The VideoSurfacePanelRegistration only defines how to create the panel,
        // but we need to create the entity with the Panel component to trigger it
        if (VROptions.current.has360Video()) {
            Log.d(TAG, "Creating 360 video panel entity")
            Entity.create(
                Panel(R.id.video_360_panel),
                Transform(),
                Visible(true)
            )
        }
    }

    override fun registerPanels(): List<PanelRegistration> {
        val panels = mutableListOf<PanelRegistration>(
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
                    val otherStatsLabel = rootView?.findViewById<TextView>(R.id.other_stats)
                    flightStatsSystem?.setLabels(altitudeLabel, otherStatsLabel)
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
                    val minimapImage =
                        rootView?.findViewById<android.widget.ImageView>(R.id.minimap_image)
                    val redDot = rootView?.findViewById<android.view.View>(R.id.red_dot)
                    val blueDot = rootView?.findViewById<android.view.View>(R.id.blue_dot)
                    val greenDot = rootView?.findViewById<android.view.View>(R.id.green_dot)
                    miniMapPanel?.setViews(minimapImage, redDot, blueDot, greenDot)
                }
            },
            PanelRegistration(R.layout.speed_chart) {
                config {
                    themeResourceId = R.style.PanelAppThemeTransparent
                    includeGlass = false
                    enableTransparent = true
                }
                panel {
                    // Set up speed chart references
                    val speedChartLive = rootView?.findViewById<com.platypii.baselinexr.charts.SpeedChartLive>(R.id.speed_chart_live)
                    speedChartSystem?.setSpeedChart(speedChartLive)
                }
            },
            PanelRegistration(R.layout.atmospheric) {
                config {
                    themeResourceId = R.style.PanelAppThemeTransparent
                    includeGlass = false
                    enableTransparent = true
                }
                panel {
                    // Set up atmospheric display references
                    val altitudeValue = rootView?.findViewById<TextView>(R.id.atmo_altitude_value)
                    val densityAltValue = rootView?.findViewById<TextView>(R.id.atmo_density_alt_value)
                    val densityAltOffset = rootView?.findViewById<TextView>(R.id.atmo_density_alt_offset)
                    val densityValue = rootView?.findViewById<TextView>(R.id.atmo_density_value)
                    val tempValue = rootView?.findViewById<TextView>(R.id.atmo_temp_value)
                    val tempOffset = rootView?.findViewById<TextView>(R.id.atmo_temp_offset)
                    val pressureValue = rootView?.findViewById<TextView>(R.id.atmo_pressure_value)
                    val pressureOffset = rootView?.findViewById<TextView>(R.id.atmo_pressure_offset)
                    val humidityValue = rootView?.findViewById<TextView>(R.id.atmo_humidity_value)
                    atmosphericSystem?.setLabels(
                        altitudeValue, densityAltValue, densityAltOffset,
                        densityValue, tempValue, tempOffset,
                        pressureValue, pressureOffset, humidityValue
                    )
                }
            })

        // Add 360 video panel if configured
        if (VROptions.current.has360Video()) {
            panels.add(
                VideoSurfacePanelRegistration(
                    R.id.video_360_panel,
                    surfaceConsumer = { panelEntity, surface ->
                        Log.d(TAG, "360 video surfaceConsumer called - panelEntity=$panelEntity, surface=$surface")
                        video360Controller?.setupVideoSurface(surface)
                    },
                    settingsCreator = {
                        MediaPanelSettings(
                            shape = Equirect360ShapeOptions(radius = 500f),  // Large radius to encompass user, UI panels use compositor layers to render on top
                            display = PixelDisplayOptions(width = 7680, height = 3840),
                            rendering = MediaPanelRenderOptions(
                                stereoMode = StereoMode.None,
                                zIndex = -100  // Render as background skybox behind all UI
                            ),
                        )
                    },
                )
            )
            Log.d(TAG, "360 video panel registered")
        }

        return panels
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

    // Handles clicking nose or tail
    fun handleOrientationButton(isForward: Boolean) {
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

                    // Set yaw adjustment so that when looking in velocity direction, world is oriented north
                    // We want: head_direction + yaw_adjustment = north (0)
                    // So: yaw_adjustment = -head_direction + velocity_to_north_correction
                    val orientationOffset = if (isForward) 0.0 else Math.PI
                    Adjustments.yawAdjustment = (orientationOffset + currentHeadYaw - velocityBearingRad).toFloat()
                    Adjustments.saveYawAdjustment(this@BaselineActivity)

//                    Log.d(TAG, "Orient head: " + currentHeadYaw + " vel: " + velocityBearingRad + " yawAdj: " + Adjustments.yawAdjustment)
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

            // Update 360 video sync if active
            if (VROptions.current.has360Video()) {
                updateVideo360Sync(loc)
            }
        }
        Services.location.locationUpdates.subscribeMain(locationSubscriber!!)
    }

    private fun updateVideo360Sync(loc: MLocation) {
        // Use PlaybackTimeline for accurate GPS → Video time conversion
        if (!PlaybackTimeline.isInitialized) {
            Log.w(TAG, "PlaybackTimeline not initialized, skipping video sync")
            return
        }
        
        // Get elapsed time from GPS provider (ms from track start)
        // This is the true track position, independent of wall-clock adjustments
        val mockProvider = Services.location.getMockLocationProvider() ?: return
        val gpsElapsedMs = mockProvider.getCurrentElapsedMs()
        
        // Convert elapsed time to original GPS timestamp for PlaybackTimeline
        val gpsTimeMs = PlaybackTimeline.gpsStartMs + gpsElapsedMs
        
        // Update timeline position
        PlaybackTimeline.updatePosition(gpsTimeMs)
        
        // Get the corresponding video time
        val videoTimeMs = PlaybackTimeline.getCurrentVideoTimeMs()
        if (videoTimeMs != null) {
            video360Controller?.updateSync(videoTimeMs)
        }
    }

    /**
     * Reset video sync state when starting fresh playback.
     * Called by ReplayController.startFresh() to prepare for new playback.
     */
    fun resetVideoSyncState() {
        PlaybackTimeline.reset()
        Log.d(TAG, "Video sync state reset via PlaybackTimeline")
    }

    companion object {
        const val TAG = "BaselineActivity"
        const val GLXF_SCENE = "GLXF_SCENE"
    }

}
