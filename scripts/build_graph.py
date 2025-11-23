import argparse
import os
import json
import pandas as pd
import networkx as nx
import peartree as pt

# Patch Pandas for Peartree compatibility
pd.Series.iteritems = pd.Series.items

def get_stop_name_robust(node_id, node_data, stops_map, coord_map):
    """
    Attempts to retrieve the stop name using multiple strategies:
    1. Exact ID Match
    2. Cleaned ID (removing suffixes)
    3. Spatial Match (GPS Coordinates)
    """
    str_id = str(node_id)

    # --- STRATEGY 1: EXACT ID ---
    if str_id in stops_map:
        return stops_map[str_id].get('stop_name', "Unknown")

    # --- STRATEGY 2: CLEANED ID ---
    # Frequent case: "56GHZ_726_0" -> looks for "56GHZ_726" or "726"
    if "_" in str_id:
        # Try without the last suffix (e.g., "_0")
        base = str_id.rsplit('_', 1)[0]
        if base in stops_map:
            return stops_map[base].get('stop_name', "Unknown")

        # Try just the numeric part if it exists (e.g., "726")
        parts = str_id.split('_')
        for p in parts:
            if p in stops_map:
                return stops_map[p].get('stop_name', "Unknown")

    # --- STRATEGY 3: SPATIAL MATCH (GPS) ---
    # Check if a stop exists at these exact coordinates (rounded to 4 decimals ~10m)
    x = float(node_data.get('x', 0))
    y = float(node_data.get('y', 0))

    # Note: coord_map keys are (lon, lat)
    geo_key = (round(x, 4), round(y, 4))
    if geo_key in coord_map:
        return coord_map[geo_key]

    return "Unknown Stop" # Total failure

def generate_graph(zip_path, output_json_path, start_hour, end_hour):
    output_dir = os.path.dirname(output_json_path)
    if output_dir and not os.path.exists(output_dir):
        os.makedirs(output_dir)

    print(f"\n=== PROCESSING: {start_hour}:00 to {end_hour}:00 ===")

    # 1. Load GTFS
    print(f"1. Reading GTFS: {zip_path}...")
    feed = pt.get_representative_feed(zip_path)

    # --- DATA PREPARATION (Lookup Dictionaries) ---
    stops_df = feed.stops.copy()
    stops_df['stop_id'] = stops_df['stop_id'].astype(str)

    # Map by ID: "726" -> {name, lat, lon}
    stops_map = stops_df.set_index('stop_id').to_dict('index')

    # Map by Coordinates: (4.8522, 45.7601) -> "Part-Dieu"
    # Allows finding the name even if the ID is completely different
    coord_map = {}
    for idx, row in stops_df.iterrows():
        try:
            # Rounding to 4 decimals (~10m precision) to tolerate micro-variations
            lat = round(float(row['stop_lat']), 4)
            lon = round(float(row['stop_lon']), 4)
            name = row.get('stop_name', "Unknown")
            coord_map[(lon, lat)] = name # Note: Order is (x, y) i.e., (lon, lat)
        except ValueError:
            continue

    # DEBUG: Show what GTFS IDs look like
    print(f"   [DEBUG] Sample GTFS IDs: {list(stops_map.keys())[:5]}")

    # 2. Load Graph
    start_sec = start_hour * 60 * 60
    end_sec = end_hour * 60 * 60
    print("2. Building graph structure (Peartree)...")
    # impute_walk_transfers=True is critical to link nearby stops
    G = pt.load_feed_as_graph(feed, start_sec, end_sec, impute_walk_transfers=True)

    print("3. Converting and Mapping...")
    # Sorting nodes to ensure deterministic output
    sorted_nodes = sorted(list(G.nodes(data=True)), key=lambda x: str(x[0]))

    # DEBUG: Show what generated Graph IDs look like
    if len(sorted_nodes) > 0:
        print(f"   [DEBUG] Sample Graph ID: {sorted_nodes[0][0]}")

    node_id_to_int = {}
    nodes_list_output = []

    count_found = 0
    count_missed = 0

    for internal_idx, (original_id, data) in enumerate(sorted_nodes):
        node_id_to_int[original_id] = internal_idx

        # --- NAME RETRIEVAL ---
        name = get_stop_name_robust(original_id, data, stops_map, coord_map)

        # Cleanup
        clean_name = str(name).replace('"', '').strip()

        if clean_name == "Unknown Stop":
            count_missed += 1
        else:
            count_found += 1

        # Coordinates
        x_val = float(data.get("x", 0.0))
        y_val = float(data.get("y", 0.0))

        # Modes
        modes = data.get("modes", [])
        if not isinstance(modes, list):
            modes = [str(modes)]

        node_obj = {
            "id": str(original_id),
            "name": clean_name,
            "x": x_val,
            "y": y_val,
            "modes": modes,
            "boarding_cost": float(data.get("boarding_cost", 0.0))
        }
        nodes_list_output.append(node_obj)

    print(f"   > Names found: {count_found} | Missing names: {count_missed}")

    # Edges processing (Converting to Int array format)
    edges_list_output = []
    for u, v, data in G.edges(data=True):
        if u in node_id_to_int and v in node_id_to_int:
            u_idx = node_id_to_int[u]
            v_idx = node_id_to_int[v]
            # Converting weight to Int for Android efficiency
            w_int = int(float(data.get("length", 0.0)))
            edges_list_output.append([u_idx, v_idx, w_int])

    final_data = {
        "metadata": {
            "period": f"{start_hour}-{end_hour}",
            "node_count": len(nodes_list_output),
            "edge_count": len(edges_list_output)
        },
        "nodes": nodes_list_output,
        "edges": edges_list_output
    }

    print(f"   > Saving to: {output_json_path}")
    with open(output_json_path, 'w', encoding='utf-8') as f:
        # separators=(',', ':') compacts JSON (removes whitespaces)
        json.dump(final_data, f, ensure_ascii=False, separators=(',', ':'))

    print("   > Done.")

def main():
    parser = argparse.ArgumentParser(description="Generate optimized JSON graph from GTFS")
    parser.add_argument("zip_file", help="Path to GTFS Zip file")
    parser.add_argument("--out", required=True, help="Output JSON path")
    parser.add_argument("--start", type=int, required=True, help="Start hour")
    parser.add_argument("--end", type=int, required=True, help="End hour")
    args = parser.parse_args()

    generate_graph(args.zip_file, args.out, args.start, args.end)

if __name__ == "__main__":
    main()