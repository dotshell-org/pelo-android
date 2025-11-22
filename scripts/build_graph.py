import argparse
import os
import pickle
import pandas as pd
from itertools import combinations

# Patch Pandas for Peartree compatibility
pd.Series.iteritems = pd.Series.items
import networkx as nx
import peartree as pt


def generate_graph(zip_path, output_folder):
    if not os.path.exists(output_folder):
        os.makedirs(output_folder)

    output_path = os.path.join(output_folder, "network.pkl")

    print(f"1. Reading GTFS from {zip_path}...")
    feed = pt.get_representative_feed(zip_path)

    start = 7 * 60 * 60
    end = 10 * 60 * 60

    print("2. Building graph structure...")
    G = pt.load_feed_as_graph(feed, start, end, impute_walk_transfers=True)

    print("3. Injecting stop names using SPATIAL MAPPING (Lat/Lon)...")
    stops_df = feed.stops.copy()

    if 'stop_name' not in stops_df.columns:
        print("Error: 'stop_name' not found in GTFS.")
        return

    stops_df['stop_name'] = stops_df['stop_name'].fillna("Unknown")

    # --- SPATIAL MAPPING STRATEGY ---
    # Since IDs don't match (Peartree generates internal IDs like 'HHT9W_1'),
    # we map stops based on their GPS coordinates.

    # Create a dictionary: (Latitude, Longitude) -> Stop Name
    # We round coordinates to 4 decimal places (~11 meters precision) to avoid floating point errors
    lat_lon_map = {}
    for _, row in stops_df.iterrows():
        r_lat = round(float(row['stop_lat']), 4)
        r_lon = round(float(row['stop_lon']), 4)
        lat_lon_map[(r_lat, r_lon)] = row['stop_name']

    matches = 0
    failures = 0

    for node in G.nodes():
        # Peartree stores coordinates in 'y' (lat) and 'x' (lon)
        node_data = G.nodes[node]
        if 'y' in node_data and 'x' in node_data:
            n_lat = round(float(node_data['y']), 4)
            n_lon = round(float(node_data['x']), 4)

            if (n_lat, n_lon) in lat_lon_map:
                G.nodes[node]['name'] = lat_lon_map[(n_lat, n_lon)]
                matches += 1
            else:
                # If exact coordinate match fails, we fallback to the ID or Unknown
                G.nodes[node]['name'] = f"Unknown ({node})"
                failures += 1
        else:
            G.nodes[node]['name'] = f"Unknown (No Coords)"
            failures += 1

    print(f"   > Names injected: {matches} nodes.")
    print(f"   > Unmatched: {failures} nodes.")

    # --- CREATE TRANSFERS ---
    print("4. Creating transfers based on Stop Names...")
    stops_by_name = {}
    for node in G.nodes():
        name = G.nodes[node].get('name')
        if name and "Unknown" not in name:
            if name not in stops_by_name:
                stops_by_name[name] = []
            stops_by_name[name].append(node)

    transfer_count = 0
    transfer_cost = 120

    for name, nodes in stops_by_name.items():
        if len(nodes) > 1:
            for u, v in combinations(nodes, 2):
                if not G.has_edge(u, v):
                    G.add_edge(u, v, length=transfer_cost, mode='transfer')
                    transfer_count += 1
                if not G.has_edge(v, u):
                    G.add_edge(v, u, length=transfer_cost, mode='transfer')
                    transfer_count += 1

    print(f"   > Added {transfer_count} transfers.")

    print(f"5. Saving graph to {output_path}...")
    with open(output_path, 'wb') as f:
        pickle.dump(G, f)
    print("Done.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("zip_file")
    parser.add_argument("--outdir", default="scripts/output")
    args = parser.parse_args()

    if not os.path.exists(args.zip_file):
        print(f"Error: File '{args.zip_file}' not found.")
        return

    generate_graph(args.zip_file, args.outdir)


if __name__ == "__main__":
    main()