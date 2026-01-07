#!/usr/bin/env python3
"""
Minimap Configuration Creator for BASElineXR

Automatically creates a new minimap based on the current VROption configuration:
1. Reads the current VROption from VROptions.java
2. Finds the GPS track file (mockTrack or TRACK.CSV in mockSensor folder)
3. Parses lat/lon data to find min/max boundaries
4. Downloads a satellite map from Google Static Maps API
5. Saves the map as minimap_current.png in the drawable folder
6. Updates VROptionsList.java with MM_CURRENT MiniMapOptions

Usage:
    python minimap_creator.py
"""

import os
import sys
import re
import math
import urllib.request
import urllib.error
from pathlib import Path

# Project paths
SCRIPT_DIR = Path(__file__).parent
PROJECT_ROOT = SCRIPT_DIR.parent
ASSETS_DIR = PROJECT_ROOT / 'app' / 'src' / 'main' / 'assets'
DRAWABLE_DIR = PROJECT_ROOT / 'app' / 'src' / 'main' / 'res' / 'drawable'
JAVA_DIR = PROJECT_ROOT / 'app' / 'src' / 'main' / 'java' / 'com' / 'platypii' / 'baselinexr'
VROPTIONS_LIST_PATH = JAVA_DIR / 'VROptionsList.java'
VROPTIONS_PATH = JAVA_DIR / 'VROptions.java'

# Google Static Maps API configuration
GOOGLE_API_KEY = "AIzaSyCtky_8e32l0w0OpQljtVA7NEyYwPo5o04"
MAP_WIDTH = 400
MAP_HEIGHT = 272
MAP_TYPE = "satellite"

# Output file names
OUTPUT_MINIMAP = "minimap_current.png"
MM_CONSTANT_NAME = "MM_CURRENT"


def get_current_vroption():
    """
    Read VROptions.java to find the current VROption configuration name.
    
    Returns:
        str: The constant name (e.g., "TEEWINOTT2", "SQUAW_360_VIDEO")
    """
    try:
        with open(VROPTIONS_PATH, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Look for: public static VROptions current = VROptionsList.XXXX;
        match = re.search(r'public static VROptions current\s*=\s*VROptionsList\.(\w+)\s*;', content)
        if match:
            return match.group(1)
        else:
            print("ERROR: Could not find 'public static VROptions current' in VROptions.java")
            return None
    except Exception as e:
        print(f"ERROR: Failed to read VROptions.java: {e}")
        return None


def get_vroption_details(constant_name):
    """
    Parse VROptionsList.java to get the VROption configuration details.
    
    Returns:
        dict with keys: mockTrack, mockSensor, or None if not found
    """
    try:
        with open(VROPTIONS_LIST_PATH, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Find the VROptions definition for this constant
        # Pattern: public static final VROptions CONSTANT_NAME = new VROptions(
        pattern = rf'public static final VROptions\s+{constant_name}\s*=\s*new VROptions\s*\(([\s\S]*?)\);'
        match = re.search(pattern, content)
        
        if not match:
            print(f"ERROR: Could not find VROptions definition for {constant_name}")
            return None
        
        params_text = match.group(1)
        
        # Parse the parameters - they're comma-separated
        # We need to handle nested parentheses for LatLngAlt
        params = parse_java_params(params_text)
        
        if len(params) < 14:
            print(f"ERROR: VROptions {constant_name} has fewer parameters than expected ({len(params)})")
            return None
        
        # Parameter positions (based on full constructor):
        # 0: name, 1: mockTrack, 2: sourceModel, 3: destination, 4: shader
        # 5: roomMovement, 6: showDirectionArrow, 7: showWingsuitCanopy, 8: showTarget, 9: showSpeedChart
        # 10: portalLocation, 11: mockSensor, 12: mockTrackStartSec, 13: mockTrackEndSec
        # 14: video360File (optional), 15: videoGpsOffsetMs (optional)
        
        mock_track = parse_string_or_null(params[1])
        mock_sensor = parse_string_or_null(params[11]) if len(params) > 11 else None
        
        return {
            'mockTrack': mock_track,
            'mockSensor': mock_sensor
        }
        
    except Exception as e:
        print(f"ERROR: Failed to parse VROptionsList.java: {e}")
        return None


def parse_java_params(params_text):
    """
    Parse Java constructor parameters, handling nested parentheses.
    """
    params = []
    current_param = ""
    paren_depth = 0
    
    for char in params_text:
        if char == '(':
            paren_depth += 1
            current_param += char
        elif char == ')':
            paren_depth -= 1
            current_param += char
        elif char == ',' and paren_depth == 0:
            params.append(current_param.strip())
            current_param = ""
        else:
            current_param += char
    
    if current_param.strip():
        params.append(current_param.strip())
    
    return params


def parse_string_or_null(value):
    """
    Parse a Java string value or null.
    """
    value = value.strip()
    if value == 'null':
        return None
    # Remove quotes
    match = re.match(r'"([^"]*)"', value)
    if match:
        return match.group(1)
    return None


def find_track_file(mock_track, mock_sensor):
    """
    Find the GPS track file based on VROption configuration.
    
    Priority:
    1. If mockSensor is set, look for TRACK.CSV in that folder
    2. If mockTrack is set, use that CSV file
    
    Returns:
        Path to the track file, or None
    """
    if mock_sensor:
        # Look in the sensor folder for TRACK.CSV
        sensor_folder = ASSETS_DIR / mock_sensor
        track_file = sensor_folder / 'TRACK.CSV'
        if track_file.exists():
            print(f"Found track file in sensor folder: {track_file}")
            return track_file
        # Try lowercase
        track_file = sensor_folder / 'track.csv'
        if track_file.exists():
            print(f"Found track file in sensor folder: {track_file}")
            return track_file
        print(f"WARNING: mockSensor folder '{mock_sensor}' does not contain TRACK.CSV")
    
    if mock_track:
        # Use the mockTrack CSV file
        track_file = ASSETS_DIR / mock_track
        if track_file.exists():
            print(f"Found mock track file: {track_file}")
            return track_file
        print(f"WARNING: mockTrack file '{mock_track}' not found in assets")
    
    if not mock_sensor and not mock_track:
        print("ERROR: VROption has neither mockTrack nor mockSensor configured")
        print("       This script requires GPS data to determine map boundaries")
    
    return None


def parse_track_file(track_file):
    """
    Parse a GPS track file to find min/max lat/lon/alt values and the last point.
    
    Supports FlySight 2 format: $GNSS,time,lat,lon,hMSL,...
    And FlySight 1 format: time,lat,lon,hMSL,...
    
    Returns:
        dict with lat_min, lat_max, lon_min, lon_max, alt_min, alt_max,
        and last_lat, last_lon, last_alt for the landing zone
    """
    lat_min = float('inf')
    lat_max = float('-inf')
    lon_min = float('inf')
    lon_max = float('-inf')
    alt_min = float('inf')
    alt_max = float('-inf')
    
    # Track the last valid point (landing zone)
    last_lat, last_lon, last_alt = None, None, None
    
    point_count = 0
    
    try:
        with open(track_file, 'r', encoding='utf-8-sig') as f:
            for line in f:
                line = line.strip()
                
                lat, lon, alt = None, None, None
                
                # FlySight 2 format: $GNSS,time,lat,lon,hMSL,...
                if line.startswith('$GNSS,'):
                    parts = line.split(',')
                    if len(parts) >= 5:
                        try:
                            lat = float(parts[2])
                            lon = float(parts[3])
                            alt = float(parts[4])
                        except ValueError:
                            continue
                
                # FlySight 1 format: time,lat,lon,hMSL,...
                elif not line.startswith('$') and not line.startswith('time') and not line.startswith(','):
                    parts = line.split(',')
                    if len(parts) >= 4:
                        try:
                            lat = float(parts[1])
                            lon = float(parts[2])
                            alt = float(parts[3])
                            # Sanity check
                            if not (-90 <= lat <= 90 and -180 <= lon <= 180):
                                continue
                        except ValueError:
                            continue
                
                if lat is not None and lon is not None and alt is not None:
                    lat_min = min(lat_min, lat)
                    lat_max = max(lat_max, lat)
                    lon_min = min(lon_min, lon)
                    lon_max = max(lon_max, lon)
                    alt_min = min(alt_min, alt)
                    alt_max = max(alt_max, alt)
                    # Keep updating - the last valid point becomes the landing zone
                    last_lat, last_lon, last_alt = lat, lon, alt
                    point_count += 1
        
        if point_count == 0:
            print("ERROR: No valid GPS points found in track file")
            return None
        
        print(f"Parsed {point_count} GPS points")
        print(f"  Latitude:  {lat_min:.6f} to {lat_max:.6f}")
        print(f"  Longitude: {lon_min:.6f} to {lon_max:.6f}")
        print(f"  Altitude:  {alt_min:.1f} to {alt_max:.1f} meters")
        print(f"  Last point (landing zone): {last_lat:.6f}, {last_lon:.6f}, {last_alt:.1f}m")
        
        return {
            'lat_min': lat_min,
            'lat_max': lat_max,
            'lon_min': lon_min,
            'lon_max': lon_max,
            'alt_min': alt_min,
            'alt_max': alt_max,
            'last_lat': last_lat,
            'last_lon': last_lon,
            'last_alt': last_alt
        }
        
    except Exception as e:
        print(f"ERROR: Failed to parse track file: {e}")
        return None


def calculate_center(bounds):
    """
    Calculate the center point of the boundary box.
    """
    center_lat = (bounds['lat_min'] + bounds['lat_max']) / 2
    center_lon = (bounds['lon_min'] + bounds['lon_max']) / 2
    return center_lat, center_lon


def calculate_zoom_level(bounds, map_width, map_height):
    """
    Calculate the appropriate Google Maps zoom level to fit the boundary box.
    
    Google Maps uses Mercator projection. The zoom level determines how many
    pixels represent 1 degree of longitude at the equator:
    - Zoom 0: 256 pixels = 360 degrees (whole world)
    - Zoom 1: 512 pixels = 360 degrees
    - etc.
    
    Returns:
        int: Zoom level (0-21)
    """
    lat_span = bounds['lat_max'] - bounds['lat_min']
    lon_span = bounds['lon_max'] - bounds['lon_min']
    
    # Add padding (20% on each side)
    padding = 0.2
    lat_span *= (1 + 2 * padding)
    lon_span *= (1 + 2 * padding)
    
    # Calculate zoom level needed for longitude
    # At zoom N, the world width in pixels is 256 * 2^N
    # We need: lon_span * (256 * 2^N / 360) <= map_width
    # So: 2^N <= map_width * 360 / (256 * lon_span)
    # N <= log2(map_width * 360 / (256 * lon_span))
    
    if lon_span > 0:
        zoom_lon = math.log2(map_width * 360 / (256 * lon_span))
    else:
        zoom_lon = 21
    
    # Calculate zoom level needed for latitude
    # Mercator projection: lat_span varies with latitude
    # Use the center latitude for correction
    center_lat = (bounds['lat_min'] + bounds['lat_max']) / 2
    lat_rad = math.radians(center_lat)
    
    # Mercator scale factor
    scale = math.cos(lat_rad)
    
    if lat_span > 0 and scale > 0:
        zoom_lat = math.log2(map_height * 360 * scale / (256 * lat_span))
    else:
        zoom_lat = 21
    
    # Use the smaller zoom (to fit both dimensions)
    zoom = int(min(zoom_lon, zoom_lat))
    
    # Clamp to valid range
    zoom = max(0, min(21, zoom))
    
    print(f"Calculated zoom level: {zoom} (lon: {zoom_lon:.1f}, lat: {zoom_lat:.1f})")
    
    return zoom


def calculate_actual_bounds(center_lat, center_lon, zoom, map_width, map_height):
    """
    Calculate the actual geographic bounds of the map image based on
    center, zoom level, and image dimensions.
    
    This reverses the zoom calculation to get the actual boundaries
    that will be covered by the downloaded map.
    
    NOTE: Google Static Maps adds a footer (~22 pixels) at the bottom with
    attribution. This shifts the actual map content upward in the image.
    We account for this by adjusting the bounds - the map content is
    effectively shifted north relative to the requested center.
    
    Returns:
        dict with lat_min, lat_max, lon_min, lon_max
    """
    # Google footer height in pixels (attribution bar at bottom)
    GOOGLE_FOOTER_HEIGHT = 22
    
    # World width in pixels at this zoom level
    world_size = 256 * (2 ** zoom)
    
    # Longitude span covered by the map
    # lon_span = map_width * 360 / world_size
    lon_span = map_width * 360 / world_size
    
    # Latitude span (corrected for Mercator projection)
    lat_rad = math.radians(center_lat)
    scale = math.cos(lat_rad)
    
    if scale > 0:
        lat_span = map_height * 360 / (world_size * scale)
    else:
        lat_span = map_height * 360 / world_size
    
    # Calculate the latitude offset due to Google footer
    # The footer takes up space at the bottom, effectively shifting
    # the map content north (up) by half the footer height worth of latitude
    # Actually, the center stays the same in the image, but the bottom
    # is cut off by the footer, so the effective lat_min is higher (more north)
    footer_lat_offset = (GOOGLE_FOOTER_HEIGHT / map_height) * lat_span
    
    # Calculate bounds with footer adjustment
    # The map content's effective center is shifted north by half the footer
    lat_min = center_lat - lat_span / 2 + footer_lat_offset
    lat_max = center_lat + lat_span / 2 + footer_lat_offset
    lon_min = center_lon - lon_span / 2
    lon_max = center_lon + lon_span / 2
    
    print(f"Actual map bounds (adjusted for {GOOGLE_FOOTER_HEIGHT}px Google footer):")
    print(f"  Latitude:  {lat_min:.6f} to {lat_max:.6f}")
    print(f"  Longitude: {lon_min:.6f} to {lon_max:.6f}")
    
    return {
        'lat_min': lat_min,
        'lat_max': lat_max,
        'lon_min': lon_min,
        'lon_max': lon_max
    }


def download_map(center_lat, center_lon, zoom, output_path):
    """
    Download a satellite map from Google Static Maps API.
    
    Returns:
        True if successful, False otherwise
    """
    # Build the URL
    url = (
        f"https://maps.googleapis.com/maps/api/staticmap?"
        f"center={center_lat},{center_lon}"
        f"&zoom={zoom}"
        f"&size={MAP_WIDTH}x{MAP_HEIGHT}"
        f"&maptype={MAP_TYPE}"
        f"&format=png"
        f"&key={GOOGLE_API_KEY}"
    )
    
    print(f"Downloading map from Google Static Maps API...")
    print(f"  Center: {center_lat:.6f}, {center_lon:.6f}")
    print(f"  Zoom: {zoom}")
    print(f"  Size: {MAP_WIDTH}x{MAP_HEIGHT}")
    
    try:
        # Create a request with a user agent
        request = urllib.request.Request(url, headers={
            'User-Agent': 'BASElineXR/1.0'
        })
        
        with urllib.request.urlopen(request, timeout=30) as response:
            if response.status != 200:
                print(f"ERROR: HTTP status {response.status}")
                return False
            
            content = response.read()
            
            # Check if it's an image (PNG starts with specific bytes)
            if not content.startswith(b'\x89PNG'):
                print("ERROR: Response is not a PNG image")
                print(f"Response preview: {content[:200]}")
                return False
            
            # Save the image
            with open(output_path, 'wb') as f:
                f.write(content)
            
            print(f"Map saved to: {output_path}")
            print(f"  File size: {len(content)} bytes")
            return True
            
    except urllib.error.HTTPError as e:
        print(f"ERROR: HTTP error {e.code}: {e.reason}")
        return False
    except urllib.error.URLError as e:
        print(f"ERROR: URL error: {e.reason}")
        return False
    except Exception as e:
        print(f"ERROR: Failed to download map: {e}")
        return False


def update_vroptions_list(actual_bounds):
    """
    Update VROptionsList.java with MM_CURRENT MiniMapOptions.
    
    If MM_CURRENT already exists, remove it and add the new one.
    """
    try:
        with open(VROPTIONS_LIST_PATH, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Check if MM_CURRENT already exists and remove it
        mm_current_pattern = r'\n\s*public static final MiniMapOptions MM_CURRENT\s*=\s*new MiniMapOptions\s*\([^)]+\);'
        if re.search(mm_current_pattern, content):
            print("Removing existing MM_CURRENT definition...")
            content = re.sub(mm_current_pattern, '', content)
        
        # Generate the new MM_CURRENT definition
        new_definition = f'''
    public static final MiniMapOptions MM_CURRENT = new MiniMapOptions(
            {actual_bounds['lat_min']:.6f},    // latMin
            {actual_bounds['lat_max']:.6f},   // latMax
            {actual_bounds['lon_min']:.6f},  // lngMin
            {actual_bounds['lon_max']:.6f}, // lngMax
            R.drawable.minimap_current
    );'''
        
        # Find the position to insert - before the closing brace of the class
        # We want to add it after the last MiniMapOptions definition
        
        # Find the last MiniMapOptions definition
        last_mm_match = None
        for match in re.finditer(r'public static final MiniMapOptions\s+\w+\s*=\s*new MiniMapOptions\s*\([^)]+\);', content):
            last_mm_match = match
        
        if last_mm_match:
            # Insert after the last MiniMapOptions
            insert_pos = last_mm_match.end()
            content = content[:insert_pos] + new_definition + content[insert_pos:]
        else:
            # No MiniMapOptions found - insert before closing brace
            last_brace = content.rfind('}')
            if last_brace != -1:
                content = content[:last_brace] + new_definition + '\n' + content[last_brace:]
        
        # Write the updated content
        with open(VROPTIONS_LIST_PATH, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print(f"Updated VROptionsList.java with MM_CURRENT")
        return True
        
    except Exception as e:
        print(f"ERROR: Failed to update VROptionsList.java: {e}")
        return False


def update_autoselect_minimap():
    """
    Ensure MM_CURRENT is in the autoSelectMinimap() array in VROptions.java.
    
    If MM_CURRENT is not present, add it to the array.
    """
    try:
        with open(VROPTIONS_PATH, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Check if MM_CURRENT is already in the array
        if 'VROptionsList.MM_CURRENT' in content:
            print("MM_CURRENT already in autoSelectMinimap() array")
            return True
        
        # Find the availableMinimaps array and add MM_CURRENT
        # Pattern: MiniMapOptions[] availableMinimaps = {
        #     VROptionsList.MM_TOOELE,
        #     VROptionsList.MM_OGDEN,
        #     VROptionsList.MM_KPOW
        # };
        
        # Look for the array pattern and add MM_CURRENT at the beginning
        array_pattern = r'(MiniMapOptions\[\]\s+availableMinimaps\s*=\s*\{)'
        match = re.search(array_pattern, content)
        
        if not match:
            print("ERROR: Could not find availableMinimaps array in VROptions.java")
            return False
        
        # Insert MM_CURRENT after the opening brace
        insert_pos = match.end()
        new_content = content[:insert_pos] + '\n                VROptionsList.MM_CURRENT,' + content[insert_pos:]
        
        # Write the updated content
        with open(VROPTIONS_PATH, 'w', encoding='utf-8') as f:
            f.write(new_content)
        
        print("Added MM_CURRENT to autoSelectMinimap() array")
        return True
        
    except Exception as e:
        print(f"ERROR: Failed to update VROptions.java: {e}")
        return False


def update_dropzone(last_lat, last_lon, last_alt):
    """
    Update the dropzone in VROptions.java with the last GPS point from the track.
    
    This sets the target landing zone to the end of the flight track.
    """
    try:
        with open(VROPTIONS_PATH, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Find and replace the dropzone definition
        # Pattern: public static LatLngAlt dropzone = new LatLngAlt(...);
        dropzone_pattern = r'public static LatLngAlt dropzone\s*=\s*new LatLngAlt\s*\([^)]+\)\s*;'
        
        new_dropzone = f'public static LatLngAlt dropzone = new LatLngAlt({last_lat:.6f}, {last_lon:.6f}, {last_alt:.0f});'
        
        if not re.search(dropzone_pattern, content):
            print("ERROR: Could not find dropzone definition in VROptions.java")
            return False
        
        content = re.sub(dropzone_pattern, new_dropzone, content)
        
        # Write the updated content
        with open(VROPTIONS_PATH, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print(f"Updated dropzone to: {last_lat:.6f}, {last_lon:.6f}, {last_alt:.0f}m")
        return True
        
    except Exception as e:
        print(f"ERROR: Failed to update dropzone in VROptions.java: {e}")
        return False


def update_default_minimap():
    """
    Set the default minimap to MM_CURRENT in VROptions.java.
    """
    try:
        with open(VROPTIONS_PATH, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Find and replace the minimap definition
        # Pattern: public static MiniMapOptions minimap = VROptionsList.MM_XXXX;
        minimap_pattern = r'public static MiniMapOptions minimap\s*=\s*VROptionsList\.\w+\s*;'
        
        new_minimap = 'public static MiniMapOptions minimap = VROptionsList.MM_CURRENT;'
        
        if not re.search(minimap_pattern, content):
            print("ERROR: Could not find minimap definition in VROptions.java")
            return False
        
        content = re.sub(minimap_pattern, new_minimap, content)
        
        # Write the updated content
        with open(VROPTIONS_PATH, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print("Set default minimap to MM_CURRENT")
        return True
        
    except Exception as e:
        print(f"ERROR: Failed to update default minimap in VROptions.java: {e}")
        return False


def main():
    print("=" * 60)
    print("BASElineXR Minimap Configuration Creator")
    print("=" * 60)
    print()
    
    # Step 1: Get current VROption
    print("Step 1: Reading current VROption configuration...")
    current_option = get_current_vroption()
    if not current_option:
        return 1
    print(f"  Current VROption: {current_option}")
    print()
    
    # Step 2: Get VROption details
    print("Step 2: Parsing VROption configuration...")
    details = get_vroption_details(current_option)
    if not details:
        return 1
    print(f"  mockTrack: {details['mockTrack']}")
    print(f"  mockSensor: {details['mockSensor']}")
    print()
    
    # Step 3: Find the track file
    print("Step 3: Locating GPS track file...")
    track_file = find_track_file(details['mockTrack'], details['mockSensor'])
    if not track_file:
        return 1
    print()
    
    # Step 4: Parse the track file
    print("Step 4: Parsing GPS track data...")
    bounds = parse_track_file(track_file)
    if not bounds:
        return 1
    print()
    
    # Step 5: Calculate map parameters
    print("Step 5: Calculating map parameters...")
    center_lat, center_lon = calculate_center(bounds)
    print(f"  Center: {center_lat:.6f}, {center_lon:.6f}")
    
    zoom = calculate_zoom_level(bounds, MAP_WIDTH, MAP_HEIGHT)
    
    # Calculate actual bounds based on zoom level
    actual_bounds = calculate_actual_bounds(center_lat, center_lon, zoom, MAP_WIDTH, MAP_HEIGHT)
    print()
    
    # Step 6: Download the map
    print("Step 6: Downloading satellite map...")
    output_path = DRAWABLE_DIR / OUTPUT_MINIMAP
    if not download_map(center_lat, center_lon, zoom, output_path):
        return 1
    print()
    
    # Step 7: Update VROptionsList.java
    print("Step 7: Updating VROptionsList.java...")
    if not update_vroptions_list(actual_bounds):
        return 1
    print()
    
    # Step 8: Update autoSelectMinimap() array in VROptions.java
    print("Step 8: Updating autoSelectMinimap() in VROptions.java...")
    if not update_autoselect_minimap():
        return 1
    print()
    
    # Step 9: Update dropzone with last GPS point (landing zone)
    print("Step 9: Updating dropzone (target landing zone)...")
    if not update_dropzone(bounds['last_lat'], bounds['last_lon'], bounds['last_alt']):
        return 1
    print()
    
    # Step 10: Set default minimap to MM_CURRENT
    print("Step 10: Setting default minimap to MM_CURRENT...")
    if not update_default_minimap():
        return 1
    print()
    
    # Summary
    print("=" * 60)
    print("SUCCESS! Minimap configuration complete.")
    print("=" * 60)
    print()
    print("Created files:")
    print(f"  - {output_path}")
    print()
    print("Updated files:")
    print(f"  - {VROPTIONS_LIST_PATH}")
    print(f"  - {VROPTIONS_PATH}")
    print()
    print("MM_CURRENT is now the default minimap and is in the autoSelectMinimap() array.")
    print()
    print(f"Dropzone target set to: {bounds['last_lat']:.6f}, {bounds['last_lon']:.6f}")
    print()
    
    return 0


if __name__ == '__main__':
    sys.exit(main())
