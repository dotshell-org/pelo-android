import argparse
import partridge as ptg
import pandas as pd
from datetime import datetime, timedelta

# Fonction pour trier les horaires proprement
def time_to_seconds(t_str):
    try:
        h, m, s = map(int, t_str.split(':'))
        return h * 3600 + m * 60 + s
    except: return 999999

def scan_day(zip_path, date_str):
    print(f"--- SCAN GLOBAL DU : {date_str} ---")
    dt = datetime.strptime(date_str, "%Y-%m-%d").date()

    # On charge le jour même uniquement pour voir ce qui existe
    try:
        service_ids_by_date = ptg.read_service_ids_by_date(zip_path)
        service_ids = service_ids_by_date.get(dt, set())
    except:
        print("Erreur lecture calendrier.")
        return

    print(f"Services actifs : {len(service_ids)}")
    if not service_ids:
        print("Aucun service ce jour là.")
        return

    print("Chargement des données (cela peut prendre quelques secondes)...")
    feed = ptg.load_feed(zip_path, view={'trips.txt': {'service_id': list(service_ids)}})

    # On fusionne tout pour avoir une vue d'ensemble
    trips = feed.trips
    routes = feed.routes
    stop_times = feed.stop_times[['trip_id', 'arrival_time', 'departure_time']]

    if trips.empty:
        print("Services trouvés mais AUCUN trip associé (fichier vide ?).")
        return

    full_data = trips.merge(routes[['route_id', 'route_short_name']], on='route_id')
    full_data = full_data.merge(stop_times, on='trip_id')

    # Analyse des horaires
    full_data['seconds'] = full_data['arrival_time'].apply(time_to_seconds)

    min_time = full_data['arrival_time'].min()
    max_time = full_data['arrival_time'].max()

    print(f"\n=== AMPLITUDE HORAIRE DU {date_str} ===")
    print(f"Premier Bus : {min_time}")
    print(f"Dernier Bus : {max_time}")
    print(f"(Note : Si > 24:00:00, c'est la nuit suivante)")

    # Liste des lignes
    lines = full_data['route_short_name'].unique()
    pl_lines = [l for l in lines if 'PL' in str(l).upper() or 'N' in str(l).upper()]

    print(f"\n=== LIGNES ACTIVES ({len(lines)}) ===")
    print(f"Lignes trouvées (extrait) : {sorted(list(lines))[:20]} ...")

    print("\n=== RECHERCHE SPÉCIFIQUE LIGNES DE NUIT ===")
    if pl_lines:
        print(f"✅ LIGNES DE NUIT PRÉSENTES : {pl_lines}")
        # Zoom sur les horaires des PL
        print("Horaires des PL :")
        zoom = full_data[full_data['route_short_name'].isin(pl_lines)]
        print(zoom.groupby('route_short_name')['arrival_time'].agg(['min', 'max']))
    else:
        print("❌ AUCUNE ligne 'PL' (Pleine Lune) ou 'N' trouvée ce jour-là.")
        print("Hypothèse : Elles ne sont pas programmées ce week-end là ou absentes du fichier.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("zip_file")
    parser.add_argument("date", help="YYYY-MM-DD")
    args = parser.parse_args()
    scan_day(args.zip_file, args.date)