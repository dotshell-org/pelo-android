import subprocess
import os
import sys

# --- CONFIGURATION ---
GTFS_FILE = "GTFS_TCL.zip"

# Destination folder in your Android project
ANDROID_ASSETS_DIR = os.path.join("app", "src", "main", "assets", "databases")

# The 5 time periods
SCHEDULES = [
    ("morning_peak", 7, 9),   # 07:00 - 09:00
    ("day_offpeak", 9, 16),   # 09:00 - 16:00
    ("evening_peak", 16, 19), # 16:00 - 19:00
    ("evening", 19, 23),      # 19:00 - 23:00
    ("late_night", 23, 26)    # 23:00 - 02:00
]

def main():
    # Check for GTFS file
    if not os.path.exists(GTFS_FILE):
        print(f"CRITICAL ERROR: File '{GTFS_FILE}' not found!")
        print("Please place the GTFS zip file in the same directory as this script.")
        return

    # Locate worker script
    worker_script = "build_graph.py"
    if not os.path.exists(worker_script):
        # Fallback if inside a scripts/ folder
        worker_script = os.path.join("scripts", "build_graph.py")
        if not os.path.exists(worker_script):
            print(f"ERROR: Could not find {worker_script}")
            return

    # Create output directory if it doesn't exist
    if not os.path.exists(ANDROID_ASSETS_DIR):
        try:
            os.makedirs(ANDROID_ASSETS_DIR)
            print(f"Directory created: {ANDROID_ASSETS_DIR}")
        except OSError:
            print(f"Warning: Could not create {ANDROID_ASSETS_DIR}, check paths.")

    print(f"--- STARTING OPTIMIZED GENERATION ({len(SCHEDULES)} files) ---")

    for suffix, start, end in SCHEDULES:
        filename = f"network_{suffix}.json"
        output_path = os.path.join(ANDROID_ASSETS_DIR, filename)

        print(f"\n>> Generating {filename} ({start}h - {end}h)...")

        cmd = [
            sys.executable,
            worker_script,
            GTFS_FILE,
            "--out", output_path,
            "--start", str(start),
            "--end", str(end)
        ]

        try:
            subprocess.run(cmd, check=True)
        except subprocess.CalledProcessError:
            print(f"!!! ERROR generating {filename} !!!")
            # Continue to next file despite error
            continue

    print("\n--- GENERATION COMPLETED ---")

if __name__ == "__main__":
    main()