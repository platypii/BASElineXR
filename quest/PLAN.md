BASElineXR is VR wingsuit skydiving app for Meta Quest. Uses the meta spatial sdk.

There are three possible coordinate systems: The device x,y,x. The position of the person in the device space. And the absolute position provided by GPS.

The world tracking of the device cannot be relied on.

Here's what I want:

Then update this implementation, which currently loads and displays a 3d GPS track, and translates it to the user's center

All that needs to change is that on location updates, the lat-lon-to-world transform should change from being centered on the FIRST gps point to being centered on the LATEST gps point.

The user and camera location should not change, the trail location should change by delta(first gps, latest gps).

Main entry point is BaselineActivity.kt. Add conversion utilities to GpsToWorldTransform.
