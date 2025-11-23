import subprocess
import os
import sys
import numpy as np

# --- CONFIGURATION ---
GTFS_FILE = "GTFS_TCL.zip"
ANDROID_ASSETS_DIR = os.path.join("app", "src", "main", "assets", "databases")

# Définition de la journée : de 04h00 à 28h00 (04h00 du matin le lendemain)
START_HOUR_OF_DAY = 0
END_HOUR_OF_DAY = 24
STEP_HOURS = 1  # <--- CHANGEMENT : 1 heure

def format_time_filename(hour_float):
    """
    Convertit 7 -> "0700", 25 -> "2500"
    """
    h = int(hour_float)
    m = int(round((hour_float - h) * 60))
    return f"{h:02d}{m:02d}"

def main():
    # 1. Vérifications
    if not os.path.exists(GTFS_FILE):
        print(f"ERREUR CRITIQUE : Fichier '{GTFS_FILE}' introuvable !")
        return

    # Localisation du script travailleur
    worker_script = "scripts/build_graph.py"
    if not os.path.exists(worker_script):
        worker_script = "build_graph.py"
        if not os.path.exists(worker_script):
            print(f"ERREUR : Impossible de trouver {worker_script}")
            return

    # Création du dossier de sortie
    if not os.path.exists(ANDROID_ASSETS_DIR):
        try:
            os.makedirs(ANDROID_ASSETS_DIR)
        except OSError:
            print(f"Attention : Impossible de créer {ANDROID_ASSETS_DIR}")

    # 2. Génération des créneaux
    time_slots = np.arange(START_HOUR_OF_DAY, END_HOUR_OF_DAY, STEP_HOURS)

    print(f"--- DÉBUT GÉNÉRATION : {len(time_slots)} fichiers (Toutes les heures) ---")
    print(f"--- De {START_HOUR_OF_DAY}h à {END_HOUR_OF_DAY}h ---\n")

    for start_h in time_slots:
        end_h = start_h + STEP_HOURS

        # Nom du fichier : network_0800.json (représente le créneau 08h-09h)
        time_str = format_time_filename(start_h)
        filename = f"network_{time_str}.json"
        output_path = os.path.join(ANDROID_ASSETS_DIR, filename)

        print(f">> Génération de {filename} [{start_h}h - {end_h}h]...")

        cmd = [
            sys.executable,
            worker_script,
            GTFS_FILE,
            "--out", output_path,
            "--start", str(start_h),
            "--end", str(end_h)
        ]

        try:
            # On lance le script build_graph.py pour chaque heure
            result = subprocess.run(cmd, capture_output=False)
            if result.returncode != 0:
                print(f"   !!! ERREUR lors de la génération de {filename} (Code {result.returncode}) !!!")
        except Exception as e:
            print(f"   !!! EXCEPTION pour {filename}: {e}")

    print("\n--- GÉNÉRATION TERMINÉE ---")

if __name__ == "__main__":
    main()