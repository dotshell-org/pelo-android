import argparse
import os
import json
import pandas as pd
import peartree as pt
import networkx as nx
import partridge as ptg
import datetime
from datetime import timedelta
import warnings

# 1. Suppression des warnings inutiles
warnings.simplefilter(action='ignore', category=FutureWarning)
try:
    pd.Series.iteritems = pd.Series.items
except AttributeError:
    pass

# 2. Classe "Façade" pour tromper Peartree (Correction du Crash)
class CustomFeed:
    def __init__(self, original_feed, filtered_stop_times):
        self.routes = original_feed.routes
        self.trips = original_feed.trips
        self.stops = original_feed.stops
        self.stop_times = filtered_stop_times
        # On copie les autres champs au cas où
        self.calendar = getattr(original_feed, 'calendar', pd.DataFrame())
        self.calendar_dates = getattr(original_feed, 'calendar_dates', pd.DataFrame())
        self.agency = getattr(original_feed, 'agency', pd.DataFrame())

def clean_val(val):
    s = str(val).strip()
    if s.endswith(".0"): s = s[:-2]
    return s

def robust_time_to_seconds(val):
    try:
        if isinstance(val, (int, float)): return int(val)
        val_str = str(val).strip()
        parts = val_str.split(':')
        return int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2].split('.')[0])
    except: return -1

def get_stop_name_spatial(node_data, coord_map, id_map, raw_id):
    cid = clean_val(raw_id)
    if cid in id_map: return id_map[cid]
    x = float(node_data.get('x', 0))
    y = float(node_data.get('y', 0))
    return coord_map.get((round(x, 4), round(y, 4)), "Unknown")

def generate_graph(zip_path, output_json_path, start_hour, end_hour, target_lines, date_str):
    start_sec = int(start_hour * 3600)
    end_sec = int(end_hour * 3600)

    # Fenêtre nuit GTFS (ex: 02h00 = 26h00)
    start_sec_ext = start_sec + 86400
    end_sec_ext = end_sec + 86400

    print(f"\n=== GÉNÉRATION ROBUSTE ({start_hour}h - {end_hour}h) ===")

    # --- CHARGEMENT ---
    print(f"1. Chargement {date_str}...")
    try:
        target_date = datetime.datetime.strptime(date_str, "%Y-%m-%d").date()
        service_ids_by_date = ptg.read_service_ids_by_date(zip_path)

        services = service_ids_by_date.get(target_date, set())
        if start_hour < 5:
            prev_date = target_date - timedelta(days=1)
            services = services.union(service_ids_by_date.get(prev_date, set()))
            print(f"   > Services : {target_date} + {prev_date} (Veille)")

        if not services:
            print("   /!\\ ERREUR : Pas de services.")
            return

        feed = ptg.load_feed(zip_path, view={'trips.txt': {'service_id': list(services)}})

    except ValueError:
        print("   Erreur date.")
        return

    # --- FILTRAGE ---
    print(f"2. Filtrage Technique (Garages, Métros de nuit)...")

    st = feed.stop_times.copy()

    # A. Suppression des "Haut-le-pied" (Non commerciaux)
    if 'pickup_type' in st.columns:
        st['pickup_type'] = pd.to_numeric(st['pickup_type'], errors='coerce').fillna(0)
        st = st[st['pickup_type'] != 1] # 1 = Pas de montée

    # B. Filtrage Horaire
    st['seconds'] = st['arrival_time'].apply(robust_time_to_seconds)
    mask_std = (st['seconds'] >= start_sec) & (st['seconds'] <= end_sec)
    mask_ext = (st['seconds'] >= start_sec_ext) & (st['seconds'] <= end_sec_ext)

    if mask_ext.sum() > mask_std.sum():
        print("   > Mode Nuit GTFS détecté.")
        st_active = st[mask_ext].copy()
        search_start, search_end = start_sec_ext, end_sec_ext
    else:
        print("   > Mode Matin détecté.")
        st_active = st[mask_std].copy()
        search_start, search_end = start_sec, end_sec

    if st_active.empty:
        print("   /!\\ VIDE : Aucun véhicule.")
        with open(output_json_path, 'w') as f: json.dump({"nodes":[], "edges":[]}, f)
        return

    # C. SUPPRESSION INTELLIGENTE DU MÉTRO (Si nuit profonde)
    # Si on est entre 1h et 4h du matin, le métro ne DOIT PAS être là.
    is_deep_night = (1.0 <= start_hour <= 4.0)

    if is_deep_night:
        print("   > Nuit profonde détectée : Suppression forcée des métros résiduels.")
        # On a besoin des types de route. On merge avec trips et routes.
        feed.routes['route_id'] = feed.routes['route_id'].astype(str)
        feed.trips['route_id'] = feed.trips['route_id'].astype(str)
        st_active['trip_id'] = st_active['trip_id'].astype(str)

        # On récupère les info de lignes pour chaque stop_time
        trips_mini = feed.trips[['trip_id', 'route_id']]
        routes_mini = feed.routes[['route_id', 'route_type', 'route_short_name']]

        # Merge en chaîne
        merged_check = st_active.merge(trips_mini, on='trip_id').merge(routes_mini, on='route_id')

        # Route Type 1 = Métro. On garde tout ce qui N'EST PAS 1.
        # (Bus = 3, Tram = 0, Funiculaire = 7)
        # On garde aussi les bus de nuit (PL)

        valid_indices = merged_check[merged_check['route_type'] != 1].index
        # Attention : valid_indices correspond à merged_check, pas st_active si les index ont changé
        # On filtre via trip_id pour être sûr

        valid_trips = merged_check[merged_check['route_type'] != 1]['trip_id'].unique()

        before_metro = len(st_active)
        st_active = st_active[st_active['trip_id'].isin(valid_trips)]
        after_metro = len(st_active)

        if before_metro > after_metro:
            print(f"     - {before_metro - after_metro} horaires de Métro supprimés.")

    # --- VERIFICATION FINALE ---
    # On ré-vérifie les lignes présentes pour l'affichage console
    check_trips = feed.trips[feed.trips['trip_id'].isin(st_active['trip_id'])]
    check_routes = feed.routes[feed.routes['route_id'].isin(check_trips['route_id'])]
    lines_present = sorted(check_routes['route_short_name'].unique())

    print("\n" + "="*40)
    print(f" LIGNES RÉELLES ({len(lines_present)}) : {', '.join(lines_present)}")
    print("="*40 + "\n")

    # --- CONSTRUCTION GRAPHE ---
    print("3. Mapping & Construction...")

    # On prépare le feed custom pour éviter le crash
    custom_feed = CustomFeed(feed, st_active)

    # Mapping des noms de lignes pour les noeuds JSON
    route_map = feed.routes.set_index('route_id')['route_short_name'].to_dict()
    trips_mini = feed.trips[['trip_id', 'route_id']]
    merged = pd.merge(st_active, trips_mini, on='trip_id', how='inner')
    merged['line_name'] = merged['route_id'].map(route_map)
    merged['clean_stop_id'] = merged['stop_id'].apply(clean_val)

    stops_df = feed.stops.copy()
    stops_df['clean_id'] = stops_df['stop_id'].apply(clean_val)
    id_to_name_map = stops_df.set_index('clean_id')['stop_name'].to_dict()

    id_to_lines = merged.groupby('clean_stop_id')['line_name'].apply(set).to_dict()
    name_to_lines = {}
    for sid, lines in id_to_lines.items():
        sname = id_to_name_map.get(sid, "Unknown")
        clean = str(sname).replace('"', '').strip()
        if clean not in name_to_lines: name_to_lines[clean] = set()
        name_to_lines[clean].update(lines)

    # Spatial Map
    coord_map = {}
    for idx, row in stops_df.iterrows():
        try: coord_map[(round(float(row['stop_lon']), 4), round(float(row['stop_lat']), 4))] = row['stop_name']
        except: continue

    # Peartree Generation (Sans crash grâce à custom_feed)
    try:
        G = pt.load_feed_as_graph(custom_feed, search_start, search_end, impute_walk_transfers=False)
    except Exception as e:
        print(f"Erreur Graphe: {e}")
        G = nx.MultiDiGraph()

    # --- EXPORT JSON ---
    print("4. Export JSON...")
    nodes_list = []
    edges_list = []
    grouped_nodes = {}
    node_name_to_id = {}

    sorted_nodes = sorted(list(G.nodes(data=True)), key=lambda x: str(x[0]))

    for i, (node_id, data) in enumerate(sorted_nodes):
        stop_name = get_stop_name_spatial(data, coord_map, id_to_name_map, node_id)
        clean_name = str(stop_name).replace('"', '').strip()

        real_modes = name_to_lines.get(clean_name, set())
        valid_modes = {m for m in real_modes if m and str(m) != 'nan'}

        if clean_name not in grouped_nodes:
            grouped_nodes[clean_name] = {"x": 0, "y": 0, "count": 0, "modes": set()}

        grouped_nodes[clean_name]["x"] += float(data.get("x", 0))
        grouped_nodes[clean_name]["y"] += float(data.get("y", 0))
        grouped_nodes[clean_name]["count"] += 1
        grouped_nodes[clean_name]["modes"].update(valid_modes)

    for idx, (name, data) in enumerate(grouped_nodes.items()):
        node_obj = {
            "id": str(idx),
            "name": name,
            "x": data["x"] / data["count"],
            "y": data["y"] / data["count"],
            "modes": list(data["modes"]),
            "boarding_cost": 0
        }
        nodes_list.append(node_obj)
        node_name_to_id[name] = idx

    orig_to_new_id = {}
    for node_id, data in sorted_nodes:
        s_name = get_stop_name_spatial(data, coord_map, id_to_name_map, node_id)
        clean = str(s_name).replace('"', '').strip()
        if clean in node_name_to_id: orig_to_new_id[node_id] = node_name_to_id[clean]

    for u, v, data in G.edges(data=True):
        if u in orig_to_new_id and v in orig_to_new_id:
            new_u = orig_to_new_id[u]
            new_v = orig_to_new_id[v]
            if new_u == new_v: continue
            edges_list.append([new_u, new_v, int(float(data.get("length", 0)))])

    final_data = {"nodes": nodes_list, "edges": edges_list}

    with open(output_json_path, 'w', encoding='utf-8') as f:
        json.dump(final_data, f, ensure_ascii=False, separators=(',', ':'))

    print(f"   > Terminé : {output_json_path}")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("zip_file")
    parser.add_argument("--out", required=True)
    parser.add_argument("--start", type=float, required=True)
    parser.add_argument("--end", type=float, required=True)
    parser.add_argument("--lines", default=None)
    parser.add_argument("--date", help="YYYY-MM-DD", default=None)
    args = parser.parse_args()

    generate_graph(args.zip_file, args.out, args.start, args.end, args.lines, args.date)

if __name__ == "__main__":
    main()