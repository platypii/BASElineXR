import json
import sys

logfile = r'C:\Users\hartm\Desktop\Oculus-Quest-3-Android-14_2025-12-03_182825.logcat'

with open(logfile, 'r') as f:
    data = json.load(f)

# The file has a structure with "logcatMessages" array
messages = data.get('logcatMessages', [])

tags = ['ReplayController', 'MockLocationProvider', 'LocationService', 'KalmanFilter3D', 'BXRINPUT', 'PlayControlsController']
keywords = ['seek', 'Seek', 'resume', 'Resume', 'start', 'Start', 'GPS', 'emitter', 'index', 'pause', 'Pause', 'stop', 'Stop', 'freeze', 'unfreeze', 'Skipping', 'duplicate', 'playing', 'Playing', 'PLAYING', 'state', 'COMPLETED', 'completed', 'update', 'location', 'delay']

count = 0
for entry in messages:
    header = entry.get('header', {})
    tag = header.get('tag', '')
    if tag in tags:
        msg = entry.get('message', '')
        if any(x in msg for x in keywords):
            print(f"{count}: {tag}: {msg}")
            count += 1

print(f"\n--- Total: {count} messages ---")
