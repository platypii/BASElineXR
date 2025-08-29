package com.platypii.baselinexr

import android.content.Context
import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.core.Vector4
import com.meta.spatial.runtime.SceneMaterial
import com.meta.spatial.runtime.SceneMaterialAttribute
import com.meta.spatial.runtime.SceneMaterialDataType
import com.meta.spatial.runtime.SceneMesh
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.runtime.TriangleMesh
import com.meta.spatial.toolkit.SceneObjectSystem
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.measurements.MLocation
import com.platypii.baselinexr.tracks.TrackFileReader
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

class FlightPathSystem(
    private val gpsToWorldTransform: GpsToWorldTransform,
    private val activity: BaselineActivity
) : SystemBase() {

    companion object {
        private const val TAG = "FlightPathSystem"
        private const val LINE_WIDTH = 2.0f // Width of the flight path line
    }

    private var flightPathEntity: Entity? = null
    private var initialized = false
    private var isVisible = true
    private var trackData: List<MLocation> = emptyList()
    private var lastOrigin: MLocation? = null

    override fun execute() {
        if (!initialized && VROptions.current.displayTrack != null) {
            initializeFlightPath()
        }
        
        updateFlightPathPosition()
    }

    private fun initializeFlightPath() {
        try {
            // Load track data
            trackData = loadTrackData(activity, VROptions.current.displayTrack!!)
            if (trackData.isEmpty()) {
                Log.w(TAG, "No track data loaded")
                return
            }

            val triangleMesh = createFlightPathMesh()
            val sceneMesh = SceneMesh.fromTriangleMesh(triangleMesh, false)

            flightPathEntity = Entity.create(
                Transform(Pose(Vector3(0f, 0f, 0f))),
                Visible(isVisible)
            )

            // Use SceneObjectSystem to add the mesh
            val sceneObjectSystem = systemManager.findSystem<SceneObjectSystem>()
            val sceneObject = SceneObject(activity.scene, sceneMesh, "flightPath", flightPathEntity!!)
            sceneObjectSystem.addSceneObject(flightPathEntity!!, CompletableFuture<SceneObject>().apply {
                complete(sceneObject)
            })

            initialized = true
            Log.i(TAG, "FlightPathSystem initialized with ${trackData.size} track points")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FlightPathSystem", e)
        }
    }

    private fun loadTrackData(context: Context, filename: String): List<MLocation> {
        return try {
            context.assets.open(filename).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { br ->
                    TrackFileReader.parse(br)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading track data from $filename", e)
            emptyList()
        }
    }

    private fun createFlightPathMesh(): TriangleMesh {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        if (trackData.size < 2) {
            Log.w(TAG, "Not enough track points to create flight path")
            return createEmptyMesh()
        }

        var vertexIndex = 0
        val lineWidth = LINE_WIDTH * 0.01f // Convert to meters

        // Create mesh in local coordinates relative to first track point
        val referencePoint = trackData[0].toLatLngAlt()
        
        // Convert GPS points to local coordinates relative to reference point
        for (i in 0 until trackData.size - 1) {
            val currentPoint = trackData[i]
            val nextPoint = trackData[i + 1]

            // Convert GPS to local coordinates using reference point as origin
            val currentPos = GeoUtils.calculateOffset(referencePoint, currentPoint.toLatLngAlt())
            val nextPos = GeoUtils.calculateOffset(referencePoint, nextPoint.toLatLngAlt())

            // Calculate line direction and perpendicular vectors
            val direction = Vector3(nextPos.x - currentPos.x, nextPos.y - currentPos.y, nextPos.z - currentPos.z)
            val up = Vector3(0f, 1f, 0f)
            val right = direction.cross(up).normalize()
            val actualUp = right.cross(direction).normalize()

            // Create 4 vertices for a rectangular tube segment
            val halfWidth = lineWidth * 0.5f

            // Current position vertices (quad)
            vertices.addAll(listOf(
                currentPos.x + right.x * halfWidth + actualUp.x * halfWidth,
                currentPos.y + right.y * halfWidth + actualUp.y * halfWidth,
                currentPos.z + right.z * halfWidth + actualUp.z * halfWidth
            ))
            vertices.addAll(listOf(
                currentPos.x - right.x * halfWidth + actualUp.x * halfWidth,
                currentPos.y - right.y * halfWidth + actualUp.y * halfWidth,
                currentPos.z - right.z * halfWidth + actualUp.z * halfWidth
            ))
            vertices.addAll(listOf(
                currentPos.x - right.x * halfWidth - actualUp.x * halfWidth,
                currentPos.y - right.y * halfWidth - actualUp.y * halfWidth,
                currentPos.z - right.z * halfWidth - actualUp.z * halfWidth
            ))
            vertices.addAll(listOf(
                currentPos.x + right.x * halfWidth - actualUp.x * halfWidth,
                currentPos.y + right.y * halfWidth - actualUp.y * halfWidth,
                currentPos.z + right.z * halfWidth - actualUp.z * halfWidth
            ))

            // Next position vertices (quad)
            vertices.addAll(listOf(
                nextPos.x + right.x * halfWidth + actualUp.x * halfWidth,
                nextPos.y + right.y * halfWidth + actualUp.y * halfWidth,
                nextPos.z + right.z * halfWidth + actualUp.z * halfWidth
            ))
            vertices.addAll(listOf(
                nextPos.x - right.x * halfWidth + actualUp.x * halfWidth,
                nextPos.y - right.y * halfWidth + actualUp.y * halfWidth,
                nextPos.z - right.z * halfWidth + actualUp.z * halfWidth
            ))
            vertices.addAll(listOf(
                nextPos.x - right.x * halfWidth - actualUp.x * halfWidth,
                nextPos.y - right.y * halfWidth - actualUp.y * halfWidth,
                nextPos.z - right.z * halfWidth - actualUp.z * halfWidth
            ))
            vertices.addAll(listOf(
                nextPos.x + right.x * halfWidth - actualUp.x * halfWidth,
                nextPos.y + right.y * halfWidth - actualUp.y * halfWidth,
                nextPos.z + right.z * halfWidth - actualUp.z * halfWidth
            ))

            // Add normals for all 8 vertices (pointing outward from tube)
            repeat(8) {
                normals.addAll(listOf(actualUp.x, actualUp.y, actualUp.z))
            }

            // Add UVs for all 8 vertices
            repeat(8) {
                uvs.addAll(listOf(0f, 0f))
            }

            // Create triangles for the tube segment (12 triangles = 2 per face * 6 faces of rectangular tube)
            val baseIdx = vertexIndex

            // Side faces (4 rectangular faces around the tube)
            // Face 1: top
            indices.addAll(listOf(baseIdx, baseIdx + 4, baseIdx + 1))
            indices.addAll(listOf(baseIdx + 1, baseIdx + 4, baseIdx + 5))
            // Face 2: right
            indices.addAll(listOf(baseIdx + 1, baseIdx + 5, baseIdx + 2))
            indices.addAll(listOf(baseIdx + 2, baseIdx + 5, baseIdx + 6))
            // Face 3: bottom
            indices.addAll(listOf(baseIdx + 2, baseIdx + 6, baseIdx + 3))
            indices.addAll(listOf(baseIdx + 3, baseIdx + 6, baseIdx + 7))
            // Face 4: left
            indices.addAll(listOf(baseIdx + 3, baseIdx + 7, baseIdx))
            indices.addAll(listOf(baseIdx, baseIdx + 7, baseIdx + 4))

            vertexIndex += 8
        }

        Log.d(TAG, "Flight path mesh created with ${vertices.size / 3} vertices and ${indices.size / 3} triangles")

        // Create material for flight path (bright color for visibility)
        val material = SceneMaterial.custom(
            SceneMaterial.UNLIT_SHADER,
            arrayOf(
                SceneMaterialAttribute("albedoFactor", SceneMaterialDataType.Vector4)
            )
        ).apply {
            // Use bright purple color for flight path - fully opaque
            setAttribute("albedoFactor", Vector4(0.8f, 0.2f, 1.0f, 1.0f))
            // Make double-sided to ensure visibility from all angles
//            setSidedness(MaterialSidedness.DOUBLE_SIDED)
        }

        val triangleMesh = TriangleMesh(
            numberVertices = vertices.size / 3,
            numberOfIndices = indices.size,
            materialRanges = intArrayOf(0, indices.size),
            materials = arrayOf(material)
        )

        // Update geometry data
        triangleMesh.updateGeometry(
            startIndex = 0,
            positions = vertices.toFloatArray(),
            normals = normals.toFloatArray(),
            uvs = uvs.toFloatArray(),
            colors = null
        )

        // Update indices
        triangleMesh.updatePrimitives(
            startIndex = 0,
            indices = indices.toIntArray()
        )

        return triangleMesh
    }

    private fun MLocation.toLatLngAlt() = com.platypii.baselinexr.measurements.LatLngAlt(latitude, longitude, altitude_gps)

    private fun createEmptyMesh(): TriangleMesh {
        val material = SceneMaterial.custom(
            SceneMaterial.UNLIT_SHADER,
            arrayOf(
                SceneMaterialAttribute("albedoColor", SceneMaterialDataType.Vector4)
            )
        ).apply {
            setAttribute("albedoColor", Vector4(1.0f, 1.0f, 1.0f, 1.0f))
        }

        return TriangleMesh(
            numberVertices = 0,
            numberOfIndices = 0,
            materialRanges = intArrayOf(0, 0),
            materials = arrayOf(material)
        )
    }

    private fun updateFlightPathPosition() {
        if (!initialized || flightPathEntity == null || trackData.isEmpty()) return
        if (gpsToWorldTransform.initialOrigin == null) return

        // Get current GPS location for coordinate transformation (like TerrainSystem and PortalSystem)
        val currentLocation = Services.location.lastLoc ?: return

        // Check if we need to update based on location changes
        if (currentLocation == lastOrigin) return

        lastOrigin = currentLocation

        // Calculate offset from destination to reference point like TerrainSystem does
        val referencePoint = trackData[0]
        val referenceLatLngAlt = referencePoint.toLatLngAlt()
        val dest = VROptions.current.destination

        // Calculate offset from destination to track reference point
        val destToTrackOffset = GeoUtils.calculateOffset(dest, referenceLatLngAlt)

        // Apply offset to destination 
        val offsetDest = GeoUtils.applyOffset(dest, destToTrackOffset)

        val currentTime = System.currentTimeMillis()
        val motionEstimator = Services.location.motionEstimator
        val referenceWorldPos = gpsToWorldTransform.toWorldCoordinates(
            offsetDest.lat,
            offsetDest.lng, 
            offsetDest.alt,
            currentTime,
            motionEstimator
        )

        // Update entity transform (like TerrainSystem and PortalSystem)
        val yawDegrees = Adjustments.yawAdjustmentDegrees().toFloat()
        val transform = Transform(Pose(
            referenceWorldPos,
            Quaternion(0f, yawDegrees, 0f)
        ))
        flightPathEntity?.setComponents(listOf(
            transform,
            Visible(isVisible)
        ))
    }

    fun setVisible(visible: Boolean) {
        isVisible = visible
        flightPathEntity?.setComponent(Visible(visible))
    }

    fun cleanup() {
        flightPathEntity?.let { entity ->
            val sceneObjectSystem = systemManager.findSystem<SceneObjectSystem>()
            sceneObjectSystem.removeSceneObject(entity)
            entity.destroy()
        }
        flightPathEntity = null
        initialized = false
        trackData = emptyList()
        lastOrigin = null
        Log.i(TAG, "FlightPathSystem cleaned up")
    }
}