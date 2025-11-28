package com.platypii.baselinexr

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.HitInfo
import com.meta.spatial.runtime.InputListener
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.Hittable
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.SceneObjectSystem
import com.meta.spatial.toolkit.Transform

/**
 * Debug system that logs all input events and raycast hits
 * Register this system to see what entities are being hit by controller raycasts
 */
class InputDebugSystem : SystemBase() {

    companion object {
        private const val TAG = "BXRINPUT"
    }

    private var initialized = false
    private var lastLogTime = 0L
    private var frameCount = 0

    override fun execute() {
        frameCount++
        
        // Log controller state every second
        val now = System.currentTimeMillis()
        if (now - lastLogTime > 1000) {
            lastLogTime = now
            logControllerState()
        }
        
        if (!initialized) {
            initialized = true
            setupGlobalInputListener()
        }
    }

    private fun logControllerState() {
        // Query for all controllers
        val controllerQuery = Query.where { has(Controller.id) }
        for (entity in controllerQuery.eval()) {
            val controller = entity.getComponent<Controller>()
            val transform = entity.tryGetComponent<Transform>()
            
            if (controller.buttonState != 0) {
                Log.d(TAG, "Controller buttons active: ${controller.buttonState} at pos=${transform?.transform?.t}")
            }
        }
    }

    private fun setupGlobalInputListener() {
        // Query for all entities with Hittable or Panel components
        val hittableQuery = Query.where { has(Hittable.id) }
        val panelQuery = Query.where { has(Panel.id) }
        
        val sceneObjectSystem = systemManager.findSystem<SceneObjectSystem>()
        
        Log.d(TAG, "Setting up input debug listeners...")
        
        // Add listeners to hittable entities
        for (entity in hittableQuery.eval()) {
            addDebugListener(sceneObjectSystem, entity, "Hittable")
        }
        
        // Add listeners to panel entities
        for (entity in panelQuery.eval()) {
            val panel = entity.getComponent<Panel>()
            addDebugListener(sceneObjectSystem, entity, "Panel(${panel.panelRegistrationId})")
        }
        
        Log.d(TAG, "Input debug listeners setup complete")
    }

    private fun addDebugListener(sceneObjectSystem: SceneObjectSystem, entity: Entity, entityType: String) {
        val systemObject = sceneObjectSystem.getSceneObject(entity)
        systemObject?.thenAccept { sceneObject ->
            sceneObject.addInputListener(object : InputListener {
                override fun onInput(
                    receiver: SceneObject,
                    hitInfo: HitInfo,
                    sourceOfInput: Entity,
                    changed: Int,
                    clicked: Int,
                    downTime: Long
                ): Boolean {
                    // Log all input events
                    val triggerPressed = changed and clicked and ButtonBits.ButtonTriggerL != 0 ||
                                        changed and clicked and ButtonBits.ButtonTriggerR != 0
                    val anyPressed = changed and clicked != 0
                    
                    if (anyPressed) {
                        Log.i(TAG, "=== INPUT HIT ===")
                        Log.i(TAG, "  Entity: $entity ($entityType)")
                        Log.i(TAG, "  Hit position: ${hitInfo.point}")
                        Log.i(TAG, "  Hit normal: ${hitInfo.normal}")
                        Log.i(TAG, "  Changed: $changed, Clicked: $clicked")
                        Log.i(TAG, "  Trigger pressed: $triggerPressed")
                        Log.i(TAG, "  Source: $sourceOfInput")
                        Log.i(TAG, "=================")
                    }
                    
                    // Return false to allow event to propagate
                    return false
                }
            })
            Log.d(TAG, "Added debug listener to $entityType entity: $entity")
        }
    }
}
