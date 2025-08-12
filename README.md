# BASEline XR

BASEline Mixed Reality.

Uses the Meta Quest 3 headset to overlay a 3D terrain model over the real world.
Location comes from a FlySight2 bluetooth GPS unit.
Orientation comes from the Quest headset.

## Ground Setup

1. Pair FlySight with Quest:
  - Double-tap FlySight button (LED should be pulsing)
  - Quest Settings > Bluetooth > Scan
  - Click your FlySight in the list to pair it with the quest
2. Install app:
  - Install Android Studio
  - Open BASElineXR project in Android Studio
  - Use ▶️ run button to install
3. Configure Quest (IMPORTANT):
  - Travel Mode: DISABLED
  - Settings > Movement Tracking > Headset Tracking: DISABLED

![Quest Movement Tracking Settings](quest-movement-tracking.jpg)
![Quest Quick Settings](quest-quick-settings.jpg)

## Flight Setup

WARNING: The Quest does NOT know which way is north in relation to the direction you are looking.

This means that the headset and the 3D model are by default NOT aligned.
This results in unexpected behavior where you flying forward results in terrain approaching from the side, or backwards.

BASElineXR has a complicated system to fix alignment before flight.
The blue arrow is always below you, and points toward what the headset _thinks_ is the direction of flight.

**The blue arrow MUST point toward the front of the plane before exit.**

1. On final approach, put on headset
2. Click on BASElineXR HUD to show options menu
3. LOOK at the nose or tail of the plane and click Nose or Tail
4. Use +/- 5° to fine tune the orientation
5. Click on BASElineXR HUD to hide options menu

Notes:

 - Left controller hamburger is the ONLY way to force return to home.
 - Meta button + long-press trigger to start/stop recording.

## Imagery

Source: swisstopo
