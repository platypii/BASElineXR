#!/usr/bin/env python3
"""
Generate terrain mesh from OpenTopography data for a given bounding box.

Usage:
    python generate_terrain.py --name squaw_peak --lat 40.2577 --lng -111.7612 --radius 2000

This will:
1. Download DEM from OpenTopography (USGS 3DEP 1m data)
2. Download satellite imagery (placeholder - manual step)
3. Generate OBJ mesh using Delatin
4. Output configuration for VROptions

Requirements:
    pip install requests numpy pydelatin gdal
"""

import argparse
import os
import sys
import json
import subprocess
from pathlib import Path

try:
    import requests
    HAS_REQUESTS = True
except ImportError:
    HAS_REQUESTS = False
    print("Note: requests not installed. DEM download disabled.")


def download_dem_opentopography(bounds, output_path, api_key=None):
    """
    Download DEM from OpenTopography API.
    
    Args:
        bounds: dict with south, north, west, east
        output_path: Path to save the DEM GeoTIFF
        api_key: OpenTopography API key (optional but recommended)
    
    Returns:
        True if successful, False otherwise
    """
    # OpenTopography Global Data API
    # https://portal.opentopography.org/apidocs/
    
    url = "https://portal.opentopography.org/API/globaldem"
    
    params = {
        "demtype": "USGS10m",  # Options: SRTMGL3, SRTMGL1, USGS10m, USGS3m, COP30, COP90
        "south": bounds["south"],
        "north": bounds["north"],
        "west": bounds["west"],
        "east": bounds["east"],
        "outputFormat": "GTiff",
    }
    
    if api_key:
        params["API_Key"] = api_key
    
    print(f"Downloading DEM from OpenTopography...")
    print(f"  Bounds: {bounds}")
    print(f"  URL: {url}")
    
    try:
        response = requests.get(url, params=params, stream=True, timeout=120)
        response.raise_for_status()
        
        with open(output_path, "wb") as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
        
        print(f"  Downloaded: {output_path}")
        return True
        
    except requests.exceptions.RequestException as e:
        print(f"  Error downloading DEM: {e}")
        print(f"  You may need an API key from https://opentopography.org/")
        return False


def calculate_bounds(lat, lng, radius_m):
    """
    Calculate bounding box from center point and radius.
    
    Args:
        lat: Center latitude
        lng: Center longitude
        radius_m: Radius in meters
    
    Returns:
        dict with south, north, west, east
    """
    # Approximate degrees per meter at this latitude
    lat_per_m = 1 / 111320  # ~111km per degree latitude
    lng_per_m = 1 / (111320 * abs(cos_deg(lat)))  # Longitude varies with latitude
    
    delta_lat = radius_m * lat_per_m
    delta_lng = radius_m * lng_per_m
    
    return {
        "south": lat - delta_lat,
        "north": lat + delta_lat,
        "west": lng - delta_lng,
        "east": lng + delta_lng,
    }


def cos_deg(degrees):
    """Cosine of angle in degrees."""
    import math
    return math.cos(math.radians(degrees))


def generate_vroptions(name, lat, lng, alt, exit_lat, exit_lng, exit_alt, bounds):
    """
    Generate VROptions Java code snippet.
    """
    # Convert to title case for display name
    display_name = name.replace("_", " ").title()
    
    java_code = f'''
    // {display_name} - exit at {exit_alt:.0f}m, landing at {alt:.0f}m
    public static final VROptions {name.upper()} = new VROptions(
            "{display_name}",
            "{name}",
            new LatLngAlt({lat:.4f}, {lng:.4f}, {alt:.1f}), // landing area
            VROptions.ShaderType.FOG_SHADER,
            true,  // passthrough (set to false after terrain is generated)
            true,
            true,
            new LatLngAlt({exit_lat:.4f}, {exit_lng:.4f}, {exit_alt:.1f}) // exit point
    );
'''
    return java_code


def generate_dropzone_options(name, lat, lng, alt, bounds):
    """
    Generate DropzoneOptions Java code snippet for minimap.
    """
    display_name = name.replace("_", " ").title()
    
    java_code = f'''
    public static final DropzoneOptions {name.upper()} = new DropzoneOptions(
            "{display_name}",
            new LatLngAlt({lat:.4f}, {lng:.4f}, {alt:.1f}),
            {bounds["south"]:.6f},    // latMin
            {bounds["north"]:.6f},    // latMax
            {bounds["west"]:.6f},  // lngMin
            {bounds["east"]:.6f},  // lngMax
            R.drawable.minimap_{name}
    );
'''
    return java_code


def generate_tile_config(name, lat, lng, alt):
    """
    Generate terrain tile JSON configuration.
    """
    config = {
        "tiles": [
            {
                "model": f"terrain/{name}_terrain.glb",
                "tileOrigin": {
                    "lat": lat,
                    "lng": lng,
                    "alt": 0
                },
                "rotation": 0
            }
        ],
        "terrainOrigin": {
            "lat": lat,
            "lng": lng,
            "alt": 0
        },
        "pointOfInterest": {
            "lat": lat,
            "lng": lng,
            "alt": alt
        }
    }
    return config


def main():
    parser = argparse.ArgumentParser(description="Generate terrain for VR")
    parser.add_argument("--name", required=True, help="Terrain name (e.g., squaw_peak)")
    parser.add_argument("--lat", type=float, required=True, help="Center latitude")
    parser.add_argument("--lng", type=float, required=True, help="Center longitude")
    parser.add_argument("--radius", type=float, default=2000, help="Radius in meters (default: 2000)")
    parser.add_argument("--exit-lat", type=float, help="Exit point latitude")
    parser.add_argument("--exit-lng", type=float, help="Exit point longitude")
    parser.add_argument("--exit-alt", type=float, help="Exit point altitude")
    parser.add_argument("--landing-alt", type=float, help="Landing altitude")
    parser.add_argument("--api-key", help="OpenTopography API key")
    parser.add_argument("--output-dir", default="build", help="Output directory")
    
    args = parser.parse_args()
    
    # Calculate bounds
    bounds = calculate_bounds(args.lat, args.lng, args.radius)
    
    print(f"\n=== Terrain Generation for {args.name} ===")
    print(f"Center: {args.lat}, {args.lng}")
    print(f"Radius: {args.radius}m")
    print(f"Bounds: S={bounds['south']:.6f} N={bounds['north']:.6f} W={bounds['west']:.6f} E={bounds['east']:.6f}")
    
    # Create output directory
    output_dir = Path(args.output_dir)
    output_dir.mkdir(exist_ok=True)
    
    # Download DEM
    dem_path = output_dir / f"{args.name}_dem.tif"
    if not dem_path.exists():
        if HAS_REQUESTS:
            success = download_dem_opentopography(bounds, dem_path, args.api_key)
            if not success:
                print("\nFailed to download DEM. Try:")
                print("  1. Get an API key from https://opentopography.org/")
                print("  2. Run again with --api-key YOUR_KEY")
                print("\nOr manually download from:")
                print(f"  https://apps.nationalmap.gov/downloader/")
                print(f"  Search for: {args.lat}, {args.lng}")
        else:
            print(f"\nDEM not found: {dem_path}")
            print("Manually download from:")
            print(f"  https://apps.nationalmap.gov/downloader/")
            print(f"  Search for: {args.lat}, {args.lng}")
    else:
        print(f"DEM already exists: {dem_path}")
    
    # Generate config files
    landing_alt = args.landing_alt or 1500
    exit_lat = args.exit_lat or args.lat
    exit_lng = args.exit_lng or args.lng
    exit_alt = args.exit_alt or 2500
    
    # VROptions code
    vroptions = generate_vroptions(
        args.name, args.lat, args.lng, landing_alt,
        exit_lat, exit_lng, exit_alt, bounds
    )
    print("\n=== VROptions Java Code ===")
    print(vroptions)
    
    # DropzoneOptions code for minimap
    dzoptions = generate_dropzone_options(args.name, args.lat, args.lng, landing_alt, bounds)
    print("\n=== DropzoneOptions Java Code (for minimap) ===")
    print(dzoptions)
    
    # Tile config JSON
    tile_config = generate_tile_config(args.name, args.lat, args.lng, landing_alt)
    config_path = output_dir / f"{args.name}_tile.json"
    with open(config_path, "w") as f:
        json.dump(tile_config, f, indent=4)
    print(f"\n=== Terrain Tile Config ===")
    print(f"Saved to: {config_path}")
    print(json.dumps(tile_config, indent=4))
    
    print("\n=== Next Steps ===")
    print("1. Download satellite imagery from Google Earth or USGS")
    print(f"   Bounds: {bounds}")
    print("2. Run the mesh generation:")
    print(f"   python dem2obj.py {dem_path} <texture.jpg> {output_dir}/{args.name}_terrain.obj")
    print("3. Convert to GLB:")
    print(f"   obj2gltf -i {output_dir}/{args.name}_terrain.obj -o {output_dir}/{args.name}_terrain.glb --binary")
    print("4. Copy GLB to app/src/main/assets/terrain/")
    print("5. Copy tile config to app/src/main/assets/terrain/")
    print("6. Update VROptionsList.java and DropzoneOptionsList.java")


if __name__ == "__main__":
    main()
