#!/usr/bin/env python3
"""
VROption Configuration Creator for BASElineXR

Interactive script that:
1. Prompts for VROption configuration details
2. Auto-detects and converts FlySight 1 files to FlySight 2 format
3. Copies GPS files/folders to assets
4. Automatically modifies VROptionsList.java and VROptions.java
5. Shows a summary with reminders

Usage:
    python vroption_creator.py
"""

import os
import sys
import shutil
import re
import uuid
from pathlib import Path
from datetime import datetime

# Import the FlySight fixer functions
SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR))

try:
    from flysight_fixer import detect_format, convert_file as convert_flysight_file
except ImportError:
    print("Error: flysight_fixer.py must be in the same directory")
    sys.exit(1)

# Project paths
PROJECT_ROOT = SCRIPT_DIR.parent
ASSETS_DIR = PROJECT_ROOT / 'app' / 'src' / 'main' / 'assets'
JAVA_DIR = PROJECT_ROOT / 'app' / 'src' / 'main' / 'java' / 'com' / 'platypii' / 'baselinexr'
VROPTIONS_LIST_PATH = JAVA_DIR / 'VROptionsList.java'
VROPTIONS_PATH = JAVA_DIR / 'VROptions.java'


class VROptionConfig:
    """Configuration for a new VROption."""
    
    def __init__(self):
        self.display_name = ""
        self.constant_name = ""
        
        # GPS data
        self.gps_type = None  # 'file' or 'folder'
        self.gps_source_path = None
        self.asset_name = ""  # Name in assets folder
        
        # Trim points
        self.start_sec = None
        self.end_sec = None
        
        # Video
        self.video_file = None  # Filename only (stored in Movies on Quest)
        self.video_offset_ms = None
        
        # Destination
        self.dest_lat = None
        self.dest_lon = None
        self.dest_alt = None
        
        # Derived
        self.option_type = None  # 'gps_only', 'gps_trimmed', 'gps_video', 'sensor_video', etc.
        
    def determine_option_type(self):
        """Determine which constructor pattern to use."""
        has_sensor = self.gps_type == 'folder'
        has_trim = self.start_sec is not None or self.end_sec is not None
        has_video = self.video_file is not None
        
        if has_sensor and has_video:
            self.option_type = 'sensor_video'  # Full constructor with all params
        elif has_sensor and has_trim:
            self.option_type = 'sensor_trimmed'  # Sensor folder with trim points
        elif has_sensor:
            self.option_type = 'sensor_only'  # Just sensor folder
        elif has_video:
            self.option_type = 'gps_video'  # GPS file with video
        elif has_trim:
            self.option_type = 'gps_trimmed'  # GPS file with trim points
        else:
            self.option_type = 'gps_only'  # Simple GPS file
        
        return self.option_type


def to_constant_name(name):
    """Convert display name to Java constant name."""
    result = re.sub(r'[^a-zA-Z0-9]', '_', name)
    result = re.sub(r'_+', '_', result)
    result = result.strip('_')
    return result.upper()


def detect_gps_format(path):
    """
    Detect if path is a single CSV file or a FlySight 2 sensor folder.
    
    Returns:
        ('file', csv_path) - Single CSV file
        ('folder', folder_path) - Folder with TRACK.CSV and SENSOR.CSV
        (None, error_message) - Error
    """
    path = Path(path)
    
    if path.is_file():
        if path.suffix.lower() == '.csv':
            return ('file', path)
        else:
            return (None, f"File must be a CSV file: {path}")
    
    if path.is_dir():
        track_csv = path / 'TRACK.CSV'
        sensor_csv = path / 'SENSOR.CSV'
        
        if not track_csv.exists():
            track_csv = path / 'track.csv'
        if not sensor_csv.exists():
            sensor_csv = path / 'sensor.csv'
        
        if track_csv.exists() and sensor_csv.exists():
            return ('folder', path)
        elif track_csv.exists():
            return ('file', track_csv)
        else:
            csvs = list(path.glob('*.csv')) + list(path.glob('*.CSV'))
            if csvs:
                return ('file', csvs[0])
            return (None, f"No CSV files found in folder: {path}")
    
    return (None, f"Path does not exist: {path}")


def get_first_gps_point(csv_path):
    """Read the first GPS data point from a CSV file (FlySight 1 or 2 format)."""
    csv_path = Path(csv_path)
    
    try:
        with open(csv_path, 'r', encoding='utf-8-sig') as f:
            lines = f.readlines()
    except Exception as e:
        return None
    
    for line in lines:
        line = line.strip()
        
        # FlySight 2 format: $GNSS,time,lat,lon,hMSL,...
        if line.startswith('$GNSS,'):
            parts = line.split(',')
            if len(parts) >= 5:
                try:
                    lat = float(parts[2])
                    lon = float(parts[3])
                    alt = float(parts[4])
                    return (lat, lon, alt)
                except ValueError:
                    continue
        
        # FlySight 1 format: time,lat,lon,hMSL,...
        # Skip header rows (contain 'time' or start with comma for units)
        elif not line.startswith('time') and not line.startswith(',') and not line.startswith('$'):
            parts = line.split(',')
            if len(parts) >= 4:
                try:
                    # FlySight 1: time is first, then lat, lon, hMSL
                    lat = float(parts[1])
                    lon = float(parts[2])
                    alt = float(parts[3])
                    # Sanity check - valid GPS coordinates
                    if -90 <= lat <= 90 and -180 <= lon <= 180:
                        return (lat, lon, alt)
                except ValueError:
                    continue
    
    return None


def check_and_convert_flysight(csv_path):
    """
    Check if a CSV file needs conversion and convert if necessary.
    
    Returns:
        Path to the ready-to-use file (original or converted)
    """
    csv_path = Path(csv_path)
    
    try:
        with open(csv_path, 'r', encoding='utf-8-sig') as f:
            lines = f.readlines()
    except Exception as e:
        print(f"  Error reading file: {e}")
        return None
    
    format_type = detect_format(lines)
    
    if format_type == 'flysight2':
        print(f"  File is already in FlySight 2 format")
        return csv_path
    elif format_type == 'unknown':
        print(f"  Warning: Unknown format, using as-is")
        return csv_path
    else:
        print(f"  Detected FlySight 1 format ({format_type}), converting...")
        converted_path = convert_flysight_file(csv_path)
        if converted_path:
            return Path(converted_path)
        else:
            print(f"  Warning: Conversion failed, using original")
            return csv_path


def copy_gps_to_assets(config):
    """
    Copy GPS file or folder to assets directory, converting if needed.
    
    Returns:
        (asset_reference, actions_taken) - asset_reference is what to put in VROptions
    """
    actions = []
    
    if config.gps_type == 'file':
        # Check and convert single file
        source_path = Path(config.gps_source_path)
        ready_path = check_and_convert_flysight(source_path)
        
        if ready_path != source_path:
            actions.append(f"Converted {source_path.name} to FlySight 2 format")
        
        dest_name = config.asset_name + '.csv'
        dest_path = ASSETS_DIR / dest_name
        
        if dest_path.exists():
            print(f"  Warning: {dest_path.name} already exists, overwriting")
            actions.append(f"Overwrote existing {dest_name}")
        
        shutil.copy2(ready_path, dest_path)
        actions.append(f"Copied GPS file to assets/{dest_name}")
        
        # Clean up converted file if it was temporary
        if ready_path != source_path and ready_path.exists():
            ready_path.unlink()
        
        return (dest_name, actions)
    
    elif config.gps_type == 'folder':
        source_path = Path(config.gps_source_path)
        dest_path = ASSETS_DIR / config.asset_name
        
        if dest_path.exists():
            print(f"  Warning: {dest_path.name} already exists, removing")
            shutil.rmtree(dest_path)
            actions.append(f"Removed existing assets/{config.asset_name}/")
        
        # Copy folder
        dest_path.mkdir(parents=True)
        
        # Process each file in the folder
        for src_file in source_path.iterdir():
            if src_file.is_file():
                if src_file.suffix.lower() == '.csv':
                    # Check and convert CSV files
                    ready_path = check_and_convert_flysight(src_file)
                    if ready_path != src_file:
                        actions.append(f"Converted {src_file.name} to FlySight 2 format")
                        shutil.copy2(ready_path, dest_path / src_file.name)
                        if ready_path.exists() and ready_path != src_file:
                            ready_path.unlink()
                    else:
                        shutil.copy2(src_file, dest_path / src_file.name)
                else:
                    # Copy non-CSV files as-is
                    shutil.copy2(src_file, dest_path / src_file.name)
        
        actions.append(f"Copied sensor folder to assets/{config.asset_name}/")
        return (config.asset_name, actions)
    
    return (None, actions)


def generate_vroptions_code(config):
    """Generate Java code for VROptions entry based on option type."""
    
    # Determine which constructor to use based on what's configured
    config.determine_option_type()
    
    cn = config.constant_name
    dn = config.display_name
    lat, lon, alt = config.dest_lat, config.dest_lon, config.dest_alt
    
    # Common parameters
    source_model = '"eiger"'
    shader = 'VROptions.ShaderType.LOD_SHADER'
    room_movement = 'false'
    show_direction = 'true'
    show_wingsuit = 'true'
    show_target = 'true'  # Show target reticle on landing zone
    show_speed_chart = 'true'
    portal_location = 'null'
    
    # For GPS files, include .csv extension in the mockTrack reference
    mock_track_ref = f'{config.asset_name}.csv' if config.gps_type == 'file' else config.asset_name
    
    if config.option_type == 'gps_only':
        # Simple GPS file, backward compatible constructor
        code = f'''    public static final VROptions {cn} = new VROptions(
            "{dn}",
            "{mock_track_ref}",
            {source_model},
            new LatLngAlt({lat}, {lon}, {alt}),
            {shader},
            {room_movement},
            {show_direction},
            {show_wingsuit},
            {show_target},
            {show_speed_chart},
            {portal_location}
    );'''
    
    elif config.option_type in ('gps_trimmed', 'sensor_only', 'sensor_trimmed'):
        # GPS/sensor with optional trim points (no video)
        mock_track = f'"{config.asset_name}.csv"' if config.gps_type == 'file' else 'null'
        mock_sensor = f'"{config.asset_name}"' if config.gps_type == 'folder' else 'null'
        start_sec = str(config.start_sec) if config.start_sec is not None else 'null'
        end_sec = str(config.end_sec) if config.end_sec is not None else 'null'
        
        code = f'''    public static final VROptions {cn} = new VROptions(
            "{dn}",
            {mock_track},
            {source_model},
            new LatLngAlt({lat}, {lon}, {alt}),
            {shader},
            {room_movement},
            {show_direction},
            {show_wingsuit},
            {show_target},
            {show_speed_chart},
            {portal_location},
            {mock_sensor},
            {start_sec},
            {end_sec}
    );'''
    
    elif config.option_type in ('gps_video', 'sensor_video'):
        # Full constructor with video
        mock_track = f'"{config.asset_name}.csv"' if config.gps_type == 'file' else 'null'
        mock_sensor = f'"{config.asset_name}"' if config.gps_type == 'folder' else 'null'
        start_sec = str(config.start_sec) if config.start_sec is not None else 'null'
        end_sec = str(config.end_sec) if config.end_sec is not None else 'null'
        video_file = f'"{config.video_file}"'
        video_offset = str(config.video_offset_ms) if config.video_offset_ms is not None else 'null'
        
        code = f'''    public static final VROptions {cn} = new VROptions(
            "{dn}",
            {mock_track},
            {source_model},
            new LatLngAlt({lat}, {lon}, {alt}),
            {shader},
            {room_movement},
            {show_direction},
            {show_wingsuit},
            {show_target},
            {show_speed_chart},
            {portal_location},
            {mock_sensor},
            {start_sec},
            {end_sec},
            {video_file},
            {video_offset}
    );'''
    
    else:
        raise ValueError(f"Unknown option type: {config.option_type}")
    
    return code


def update_vroptions_list(config, code):
    """
    Add the new VROption to VROptionsList.java.
    
    Returns:
        True if successful
    """
    try:
        with open(VROPTIONS_LIST_PATH, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading VROptionsList.java: {e}")
        return False
    
    # Find the last VROptions definition (before MiniMapOptions)
    # Look for the pattern of closing bracket before MiniMapOptions
    insert_pattern = r'(\n\s*public static final MiniMapOptions)'
    match = re.search(insert_pattern, content)
    
    if match:
        insert_pos = match.start()
        new_content = content[:insert_pos] + '\n\n' + code + content[insert_pos:]
    else:
        # Fallback: insert before the closing brace of the class
        insert_pos = content.rfind('}')
        if insert_pos == -1:
            print("Error: Could not find insertion point in VROptionsList.java")
            return False
        new_content = content[:insert_pos] + '\n' + code + '\n\n' + content[insert_pos:]
    
    try:
        with open(VROPTIONS_LIST_PATH, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    except Exception as e:
        print(f"Error writing VROptionsList.java: {e}")
        return False


def update_vroptions_current(config):
    """
    Update VROptions.java to set the new option as current.
    
    Returns:
        True if successful
    """
    try:
        with open(VROPTIONS_PATH, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading VROptions.java: {e}")
        return False
    
    # Find and replace the current line
    pattern = r'(public static VROptions current = VROptionsList\.)\w+;'
    replacement = f'\\1{config.constant_name};'
    
    new_content, count = re.subn(pattern, replacement, content)
    
    if count == 0:
        print("Warning: Could not find 'current' assignment in VROptions.java")
        return False
    
    try:
        with open(VROPTIONS_PATH, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    except Exception as e:
        print(f"Error writing VROptions.java: {e}")
        return False


def prompt_int(prompt, allow_none=True):
    """Prompt for an integer value."""
    while True:
        value = input(prompt).strip()
        if not value and allow_none:
            return None
        try:
            return int(value)
        except ValueError:
            print("  Please enter a valid integer")


def prompt_float(prompt, allow_none=True):
    """Prompt for a float value."""
    while True:
        value = input(prompt).strip()
        if not value and allow_none:
            return None
        try:
            return float(value)
        except ValueError:
            print("  Please enter a valid number")


def show_notification(config, actions):
    """Display a summary notification."""
    
    width = 70
    
    print()
    print("=" * width)
    print("  VROption Created Successfully!".center(width))
    print("=" * width)
    print()
    
    # Actions taken
    print("  ACTIONS TAKEN:")
    print("  " + "-" * (width - 4))
    for action in actions:
        print(f"  ✓ {action}")
    print()
    
    # Configuration summary
    print("  NEW CONFIGURATION:")
    print("  " + "-" * (width - 4))
    print(f"  Name:        {config.display_name}")
    print(f"  Constant:    VROptionsList.{config.constant_name}")
    print(f"  Type:        {config.option_type.replace('_', ' ').title()}")
    
    if config.gps_type == 'file':
        print(f"  GPS File:    assets/{config.asset_name}")
    else:
        print(f"  Sensor Dir:  assets/{config.asset_name}/")
    
    if config.start_sec is not None or config.end_sec is not None:
        print(f"  Trim:        {config.start_sec or 'start'} - {config.end_sec or 'end'} sec")
    
    if config.video_file:
        print(f"  Video:       {config.video_file}")
        print(f"  Offset:      {config.video_offset_ms or 0} ms")
    
    print(f"  Destination: {config.dest_lat:.6f}, {config.dest_lon:.6f}, {config.dest_alt:.1f}m")
    print()
    
    # Reminders
    print("  REMINDERS:")
    print("  " + "-" * (width - 4))
    print("  ⚠ Test the new configuration before using in flight!")
    print("  ⚠ Build and deploy to Quest to verify GPS playback")
    
    if config.video_file:
        print()
        print("  📹 VIDEO UPLOAD REQUIRED:")
        print(f"     Copy '{config.video_file}' to the Quest's Movies folder:")
        print("     1. Connect Quest to PC via USB")
        print("     2. Open Quest files in File Explorer")
        print("     3. Navigate to Movies folder")
        print(f"     4. Copy {config.video_file} to Movies/")
    
    print()
    print("=" * width)
    print()


def main():
    print()
    print("=" * 60)
    print("  VROption Configuration Creator for BASElineXR")
    print("=" * 60)
    print()
    print(f"Assets: {ASSETS_DIR}")
    print()
    
    config = VROptionConfig()
    actions = []
    
    # 1. Get VROption name
    print("1. VROption name (e.g., 'Ogden Full', 'My Cool Jump'):")
    config.display_name = input("   > ").strip()
    if not config.display_name:
        print("Error: Name is required")
        return
    
    config.constant_name = to_constant_name(config.display_name)
    print(f"   Constant: {config.constant_name}")
    print()
    
    # 2. Get GPS file/folder
    print("2. GPS file or sensor folder path:")
    print("   (Drag and drop, or type path)")
    gps_path = input("   > ").strip()
    # Clean up path - remove quotes, PowerShell prefixes, etc.
    gps_path = gps_path.strip('"').strip("'")
    gps_path = re.sub(r"^& '", "", gps_path)  # PowerShell drag-drop prefix
    gps_path = re.sub(r"'$", "", gps_path)    # Trailing quote
    gps_path = gps_path.strip()
    
    if not gps_path:
        print("Error: GPS path is required")
        return
    
    gps_type, gps_data = detect_gps_format(gps_path)
    if gps_type is None:
        print(f"Error: {gps_data}")
        return
    
    config.gps_type = gps_type
    config.gps_source_path = gps_data
    config.asset_name = config.constant_name.lower()
    
    print(f"   Type: {gps_type}")
    print(f"   Asset name: {config.asset_name}")
    print()
    
    # Get first GPS point for default destination
    if gps_type == 'folder':
        track_csv = Path(gps_data) / 'TRACK.CSV'
        if not track_csv.exists():
            track_csv = Path(gps_data) / 'track.csv'
        first_point = get_first_gps_point(track_csv)
    else:
        first_point = get_first_gps_point(gps_data)
    
    if first_point:
        print(f"   First GPS point: {first_point[0]:.6f}, {first_point[1]:.6f}, {first_point[2]:.1f}m")
    print()
    
    # 3. GPS trim points (optional)
    print("3. GPS start time in seconds (Enter to skip):")
    config.start_sec = prompt_int("   > ")
    
    print("4. GPS end time in seconds (Enter to skip):")
    config.end_sec = prompt_int("   > ")
    print()
    
    # 5. Video file (optional)
    print("5. 360° video filename (Enter to skip):")
    print("   (Just the filename, e.g., 'myjump360.mp4')")
    print("   (Video must be uploaded to Quest Movies folder)")
    video_input = input("   > ").strip().strip('"').strip("'")
    
    if video_input:
        # Extract just filename if full path given
        config.video_file = Path(video_input).name
        
        print()
        print("6. Video/GPS time offset in milliseconds:")
        print("   (Positive = delay video, Negative = advance video)")
        config.video_offset_ms = prompt_int("   > ")
    print()
    
    # 7. Destination coordinates
    print("7. Destination coordinates (where to place in VR world):")
    if first_point:
        # Default: use first GPS point with altitude - 250m
        default_alt = first_point[2] - 250.0
        print(f"   Press Enter to use: {first_point[0]:.6f}, {first_point[1]:.6f}, {default_alt:.1f}m")
        print(f"   (First GPS alt {first_point[2]:.1f}m - 250m)")
        lat_input = input("   Latitude > ").strip()
        if lat_input:
            config.dest_lat = float(lat_input)
            config.dest_lon = prompt_float("   Longitude > ", allow_none=False)
            config.dest_alt = prompt_float("   Altitude > ", allow_none=False)
        else:
            config.dest_lat = first_point[0]
            config.dest_lon = first_point[1]
            config.dest_alt = default_alt
    else:
        config.dest_lat = prompt_float("   Latitude > ", allow_none=False)
        config.dest_lon = prompt_float("   Longitude > ", allow_none=False)
        config.dest_alt = prompt_float("   Altitude (m) > ", allow_none=False)
    print()
    
    # Summary before proceeding
    config.determine_option_type()
    
    print("-" * 60)
    print("Configuration Summary:")
    print(f"  Name: {config.display_name} ({config.constant_name})")
    print(f"  Type: {config.option_type.replace('_', ' ').title()}")
    print(f"  GPS: {config.gps_type} - {Path(config.gps_source_path).name}")
    if config.start_sec or config.end_sec:
        print(f"  Trim: {config.start_sec} - {config.end_sec} sec")
    if config.video_file:
        print(f"  Video: {config.video_file} (offset: {config.video_offset_ms}ms)")
    print(f"  Dest: {config.dest_lat:.6f}, {config.dest_lon:.6f}, {config.dest_alt:.1f}m")
    print("-" * 60)
    print()
    
    proceed = input("Proceed? (y/n) [y]: ").strip().lower()
    if proceed == 'n':
        print("Cancelled")
        return
    print()
    
    # Execute!
    print("Processing...")
    print()
    
    # 1. Copy GPS files to assets
    print("Copying GPS data to assets...")
    asset_ref, copy_actions = copy_gps_to_assets(config)
    actions.extend(copy_actions)
    print()
    
    # 2. Generate and add VROptions code
    print("Generating VROptions code...")
    code = generate_vroptions_code(config)
    
    print("Updating VROptionsList.java...")
    if update_vroptions_list(config, code):
        actions.append(f"Added {config.constant_name} to VROptionsList.java")
    else:
        print("Warning: Failed to update VROptionsList.java")
    
    # 3. Update VROptions.current
    print("Updating VROptions.java (setting as current)...")
    if update_vroptions_current(config):
        actions.append(f"Set VROptions.current = VROptionsList.{config.constant_name}")
    else:
        print("Warning: Failed to update VROptions.java")
    
    # Show notification
    show_notification(config, actions)


if __name__ == '__main__':
    main()
