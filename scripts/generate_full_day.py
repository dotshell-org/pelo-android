import subprocess
import os
import sys

# --- CONFIGURATION ---
GTFS_FILE = "GTFS_TCL.zip"  # Make sure this filename is correct!

# Destination folder in your Android project
# Using os.path.join for Windows/Linux/Mac compatibility
ANDROID_ASSETS_DIR = os.path.join("app", "src", "main", "assets", "databases")

# The 5 recommended periods to cover the full day
# Format: (File Suffix, Start Hour, End Hour)
SCHEDULES = [
    ("morning_peak", 7, 9),  # 07:00 - 09:00 : Morning Rush
    ("day_offpeak", 9, 16),  # 09:00 - 16:00 : Standard Day
    ("evening_peak", 16, 19),  # 16:00 - 19:00 : Evening Rush
    ("evening", 19, 23),  # 19:00 - 23:00 : Evening
    ("late_night", 23, 26)  # 23:00 - 02:00 : Night (GTFS often handles > 24h)
]


def main():
    if not os.path.exists(GTFS_FILE):
        print(f"CRITICAL ERROR: File '{GTFS_FILE}' not found!")
        return

    # Path to the worker script
    worker_script = os.path.join("scripts", "build_graph.py")
    if not os.path.exists(worker_script):
        print(f"ERROR: Could not find {worker_script}")
        return

    print(f"--- STARTING GENERATION ({len(SCHEDULES)} files) ---")
    print(f"Destination: {ANDROID_ASSETS_DIR}")

    for suffix, start, end in SCHEDULES:
        # Final filename: network_morning_peak.json
        filename = f"network_{suffix}.json"
        output_path = os.path.join(ANDROID_ASSETS_DIR, filename)

        # Command: python3 scripts/build_graph.py GTFS.zip --out ... --start ... --end ...
        cmd = [
            sys.executable,
            worker_script,
            GTFS_FILE,
            "--out", output_path,
            "--start", str(start),
            "--end", str(end)
        ]

        try:
            # check=True will crash the main script if a step fails
            subprocess.run(cmd, check=True)
        except subprocess.CalledProcessError:
            print(f"\n!!! FAILURE generating {suffix} !!!")
            return

    print("\n" + "=" * 50)
    print("TOTAL SUCCESS! The 5 JSON files are ready in your Android folder.")
    print("=" * 50)


if __name__ == "__main__":
    main()