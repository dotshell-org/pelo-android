import argparse
import os
import json
import pandas as pd
from itertools import combinations
import networkx as nx
import peartree as pt

# Patch Pandas for Peartree compatibility
pd.Series.iteritems = pd.Series.items


def generate_graph(zip_path, output_json_path, start_hour, end_hour):
    # Create parent directory if necessary
    output_dir = os.path.dirname(output_json_path)
    if output_dir and not os.path.exists(output_dir):
        os.makedirs(output_dir)

    print(f"\n=== PROCESSING: {start_hour}:00 to {end_hour}:00 ===")
    print(f"1. Reading GTFS: {zip_path}...")
    feed = pt.get_representative_feed(zip_path)

    # Convert hours -> seconds
    start_sec = start_hour * 60 * 60
    end_sec = end_hour * 60 * 60

    print("2. Building graph structure (Peartree)...")
    # impute_walk_transfers=True is important to link geographically close stops
    G = pt.load_feed_as_graph(feed, start_sec, end_sec, impute_walk_transfers=True)

    print("3. Injecting names using SPATIAL MAPPING (Lat/Lon)...")
    stops_df = feed.stops.copy()

    if 'stop_name' not in stops_df.columns:
        print("   Warning: 'stop_name' not found, names will be set to 'Unknown'")
        stops_df['stop_name'] = "Unknown"
    else:
        stops_df['stop_name'] = stops_df['stop_name'].fillna("Unknown")

    # Dictionary: (Lat, Lon) -> Stop Name
    # We round to 4 decimal places (~11m) to avoid floating point errors
    lat_lon_map = {}
    for _, row in stops_df.iterrows():
        r_lat = round(float(row['stop_lat']), 4)
        r_lon = round(float(row['stop_lon']), 4)
        lat_lon_map[(r_lat, r_lon)] = row['stop_name']

    matches = 0
    failures = 0

    for node in G.nodes():
        node_data = G.nodes[node]
        if 'y' in node_data and 'x' in node_data:
            n_lat = round(float(node_data['y']), 4)
            n_lon = round(float(node_data['x']), 4)

            if (n_lat, n_lon) in lat_lon_map:
                G.nodes[node]['name'] = lat_lon_map[(n_lat, n_lon)]
                matches += 1
            else:
                G.nodes[node]['name'] = f"Unknown ({node})"
                failures += 1
        else:
            G.nodes[node]['name'] = "Unknown (No Coords)"
            failures += 1

    print(f"   > Names found: {matches} | Not found: {failures}")

    print("4. Creating transfers...")
    stops_by_name = {}
    for node in G.nodes():
        name = G.nodes[node].get('name')
        if name and "Unknown" not in name:
            if name not in stops_by_name:
                stops_by_name[name] = []
            stops_by_name[name].append(node)

    transfer_count = 0
    transfer_cost = 120  # 2 minutes penalty for changing lines/platforms

    for name, nodes in stops_by_name.items():
        if len(nodes) > 1:
            for u, v in combinations(nodes, 2):
                if not G.has_edge(u, v):
                    G.add_edge(u, v, length=transfer_cost, mode='transfer')
                    transfer_count += 1
                if not G.has_edge(v, u):
                    G.add_edge(v, u, length=transfer_cost, mode='transfer')
                    transfer_count += 1

    print(f"   > {transfer_count} walking connections added.")

    print("5. Cleaning and Exporting JSON...")

    # --- SANITIZATION (Fixing the CRS bug) ---
    # We convert all complex objects to strings to avoid JSON crashes
    for key, value in list(G.graph.items()):
        if not isinstance(value, (str, int, float, bool, list, dict, type(None))):
            G.graph[key] = str(value)

    for node, data in G.nodes(data=True):
        for key, value in list(data.items()):
            if not isinstance(value, (str, int, float, bool, list, dict, type(None))):
                data[key] = str(value)

    # Convert to Node-Link format compatible with Android
    data = nx.node_link_data(G, edges="links")

    print(f"   > Saving to: {output_json_path}")
    with open(output_json_path, 'w', encoding='utf-8') as f:
        # ensure_ascii=False keeps accents (e.g., Hôtel de Ville)
        # indent=None minifies the file to save space on Android
        json.dump(data, f, ensure_ascii=False, indent=None)

    print("   > Done.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("zip_file")
    parser.add_argument("--out", required=True, help="Full path to the output JSON file")
    parser.add_argument("--start", type=int, required=True, help="Start hour (0-26)")
    parser.add_argument("--end", type=int, required=True, help="End hour (0-26)")
    args = parser.parse_args()

    if not os.path.exists(args.zip_file):
        print(f"Error: File '{args.zip_file}' not found.")
        return

    generate_graph(args.zip_file, args.out, args.start, args.end)


if __name__ == "__main__":
    main()