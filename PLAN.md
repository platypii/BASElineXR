BASElineXR is VR wingsuit skydiving app for Meta Quest. Uses the meta spatial sdk.

The problem right now is that location updates are sent every 5hz. But frames re-render ~60times/sec. I need you to update this project to store a velocity in the GpsToWorldTransform. Also store the timestamp of the last update. Then when calling toWorldCoordinates pass the time stamp, and extrapolate the position based on the last location plus velocity vector.
