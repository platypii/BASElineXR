BASElineXR is VR wingsuit skydiving app for Meta Quest. Uses the meta spatial sdk.

## Implementation Plan

### Overview
Transform GPS coordinates (lat/lon/alt) into 3D positions in the VR world. I have existing GPS track data in a CSV file (eiger.csv) that I want to visualize as a flight path trail in the VR environment.

### Phase 1: Coordinate Transformation System

1. **Create GpsToWorldTransform.java**
   - Define origin point (e.g., starting position of GPS track)
   - Implement WGS84 to local Cartesian conversion
   - Use East-North-Up (ENU) coordinate system
   - Scale factor: 1 unit = 1 meter for realistic scale
   - Handle altitude with proper vertical scaling

2. **Conversion Algorithm**
   - Use first GPS point as origin (0,0,0) in VR world
   - Convert subsequent lat/lon to meters offset from origin
   - Apply Earth radius approximation for local area
   - Transform: X=East, Y=Up(altitude), Z=North

### Flight Path Trail

2. **Flight Path Trail**
   - Load points using MockLocationProvider.loadData(context)
   - Transform each lat lon point to world coordinates
   - Render track data as a visual trail in the VR environment
   - Start the track at the origin point

Do NOT overcomplicate. Keep it simple and efficient. Focus on core functionality first. I don't need live updates. I JUST want to render the track data.


