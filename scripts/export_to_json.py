import argparse
import os
import pickle
import json
import pandas as pd

# Patch Pandas
pd.Series.iteritems = pd.Series.items
import networkx as nx


def export_to_json(pkl_path, json_output):
    print(f"Loading Python graph from {pkl_path}...")
    with open(pkl_path, 'rb') as f:
        G = pickle.load(f)

    # --- CRITICAL FIX: Sanitize Graph Attributes ---
    # The error "TypeError: Object of type CRS is not JSON serializable" happens
    # because the graph metadata contains complex objects. We convert them to strings.
    print("Sanitizing graph data...")

    # Clean global graph attributes (G.graph)
    for key, value in list(G.graph.items()):
        # Check if the value is a basic JSON type. If not, cast to string.
        if not isinstance(value, (str, int, float, bool, list, dict, type(None))):
            G.graph[key] = str(value)

    # Clean node attributes (just in case)
    for node, data in G.nodes(data=True):
        for key, value in list(data.items()):
            if not isinstance(value, (str, int, float, bool, list, dict, type(None))):
                data[key] = str(value)

    print("Converting to Node-Link format...")
    # explicitly set edges="links" to fix the FutureWarning you saw earlier
    data = nx.node_link_data(G, edges="links")

    print(f"Saving JSON to {json_output}...")
    with open(json_output, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print("Done! You can now parse this JSON in Kotlin.")


def main():
    parser = argparse.ArgumentParser(description="Export Pickle graph to JSON for Android/Kotlin.")
    parser.add_argument("--pkl", default="scripts/output/network.pkl")
    parser.add_argument("--out", default="app/src/main/assets/databases/network_graph.json")
    args = parser.parse_args()

    if not os.path.exists(args.pkl):
        print(f"Error: File '{args.pkl}' not found.")
        return

    # Ensure output directory exists
    out_dir = os.path.dirname(args.out)
    if out_dir and not os.path.exists(out_dir):
        os.makedirs(out_dir)

    export_to_json(args.pkl, args.out)


if __name__ == "__main__":
    main()