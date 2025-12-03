# BASElineXR Scripts

This folder contains utility scripts for configuring and managing the BASElineXR project.

## Prerequisites

All Python scripts require Python 3.x and should be run using the project's virtual environment:

```powershell
# From the scripts folder, use the virtual environment Python
C:/Users/hartm/StudioProjects/BASElineXR/.venv/Scripts/python.exe <script_name>.py
```

Or activate the virtual environment first:
```powershell
cd C:\Users\hartm\StudioProjects\BASElineXR
.\.venv\Scripts\Activate.ps1
cd scripts
python <script_name>.py
```

---

## Configuration Scripts

### `vroption_creator.py`

**Interactive VROption Configuration Creator**

Creates a new VROption configuration entry by:
1. Prompting for configuration details (name, GPS source, destination coordinates, etc.)
2. Auto-detecting and converting FlySight 1 files to FlySight 2 format
3. Copying GPS files/folders to the assets directory
4. Automatically modifying `VROptionsList.java` and `VROptions.java`

**Usage:**
```powershell
python vroption_creator.py
```

The script will interactively prompt for all required information.

---

### `minimap_creator.py`

**Automatic Minimap Generator**

Generates a satellite minimap based on the **current VROption** configuration. It:
1. Reads the current VROption from `VROptions.java`
2. Finds the GPS track file (either `mockTrack` CSV or `TRACK.CSV` in `mockSensor` folder)
3. Parses latitude/longitude data to find min/max boundaries
4. Calculates the optimal zoom level and center point
5. Downloads a satellite map from Google Static Maps API
6. Saves the map as `minimap_current.png` in the drawable folder
7. Updates `VROptionsList.java` with `MM_CURRENT` MiniMapOptions
8. Adds `MM_CURRENT` to the `autoSelectMinimap()` array in `VROptions.java`

**Usage:**
```powershell
python minimap_creator.py
```

**Note:** Before running, make sure `VROptions.current` in `VROptions.java` is set to the VROption you want to generate a minimap for.

---

### `flysight_fixer.py`

**FlySight CSV Format Converter**

Converts FlySight 1 format and FlySight Viewer exports to FlySight 2 format compatible with BASElineXR.

**Input formats:**
- FlySight 1: `time,lat,lon,hMSL,velN,velE,velD,hAcc,vAcc,sAcc,numSV`
- FlySight Viewer exports

**Output format:**
- FlySight 2 with `$GNSS` prefixed lines

**Usage:**
```powershell
python flysight_fixer.py [file_or_folder]
```

If no argument is provided, the script will prompt for a file or folder path.

---

## Terrain/GIS Scripts

### `dem2obj.py`

**DEM to OBJ Converter**

Converts a Digital Elevation Model (DEM) + texture into a simplified Wavefront OBJ file using Delatin (adaptive Delaunay triangulation).

**Requirements:**
- GDAL (`osgeo`)
- NumPy
- pydelatin

**Usage:**
```powershell
python dem2obj.py dem.tif texture.jpg terrain.obj [max_error]
```

- `dem.tif` - Input DEM file (GeoTIFF)
- `texture.jpg` - Texture image
- `terrain.obj` - Output OBJ file
- `max_error` - Optional, maximum error tolerance in meters (default: 7.5)

---

### `gdal.sh` / `gdal-tiny.sh`

**GDAL Processing Scripts** (Bash/Linux)

Shell scripts for processing GeoTIFF terrain data using GDAL tools.

---

### `pdal.sh`

**PDAL Processing Script** (Bash/Linux)

Shell script for processing point cloud data using PDAL.

---

## Utility Scripts

### `html_to_clean_md.py`

**HTML to Markdown Converter**

Converts HTML documentation to clean Markdown by removing CSS classes, styling, and other HTML artifacts.

**Requirements:**
- BeautifulSoup4 (`bs4`)
- html2text

**Usage:**
```powershell
python html_to_clean_md.py <input.html> [output.md]
```

---

## Typical Workflow

### Adding a new flight recording:

1. **Convert GPS data** (if needed):
   ```powershell
   python flysight_fixer.py path/to/flysight1_data.csv
   ```

2. **Create the VROption**:
   ```powershell
   python vroption_creator.py
   ```
   Follow the prompts to configure the new option.

3. **Generate the minimap**:
   - Edit `VROptions.java` and set `current` to your new VROption
   - Run:
   ```powershell
   python minimap_creator.py
   ```

4. **Build and deploy** the app to your Quest headset.
