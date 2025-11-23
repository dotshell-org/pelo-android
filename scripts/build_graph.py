import argparse
import os
import json
import pandas as pd
import peartree as pt
import networkx as nx

# Patch Pandas compatibility
try:
    pd.Series.iteritems = pd.Series.items
except AttributeError:
    pass

def clean_val(val):
    """Cleans ID values to string without decimals."""
    s = str(val).strip()
    if s.endswith(".0"):
        s = s[:-2]
    return s

def get_stop_name_spatial(node_data, coord_map, id_map, raw_id):
    """Finds stop name via ID or GPS."""
    cid = clean_val(raw_id)
    if cid in id_map: return id_map[cid]

    x = float(node_data.get('x', 0))
    y = float(node_data.get('y', 0))
    geo_key = (round(x, 4), round(y, 4))

    if geo_key in coord_map: return coord_map[geo_key]
    return "Unknown"

def generate_graph(zip_path, output_json_path, start_hour, end_hour, target_lines):
    output_dir = os.path.dirname(output_json_path)
    if output_dir and not os.path.exists(output_dir):
        os.makedirs(output_dir)

    print(f"\n=== GENERATING GRAPH (NAME-BASED MODES): {start_hour}h - {end_hour}h ===")

    # 1. Load GTFS
    print(f"1. Reading GTFS file: {zip_path}...")
    feed = pt.get_representative_feed(zip_path)

    # 2. FILTER LINES (Optional)
    if target_lines:
        targets = [t.strip() for t in target_lines.split(',')]
        print(f"   > FILTER ACTIVE: Keeping only lines {targets}")
        feed.routes = feed.routes[feed.routes['route_short_name'].isin(targets)]
        valid_route_ids = feed.routes['route_id'].unique()
        feed.trips = feed.trips[feed.trips['route_id'].isin(valid_route_ids)]

    # 3. BUILD MAPPING: STOP NAME -> LINE NAMES
    print("2. Mapping 'Stop Name' to 'Lines'...")

    # Force types
    feed.routes['route_id'] = feed.routes['route_id'].astype(str)
    feed.trips['route_id'] = feed.trips['route_id'].astype(str)
    feed.trips['trip_id'] = feed.trips['trip_id'].astype(str)
    feed.stop_times['trip_id'] = feed.stop_times['trip_id'].astype(str)
    feed.stop_times['stop_id'] = feed.stop_times['stop_id'].astype(str)
    feed.stops['stop_id'] = feed.stops['stop_id'].astype(str)

    # A. Map trip -> route name
    feed.routes['route_short_name'] = feed.routes['route_short_name'].astype(str).str.strip()
    route_map = feed.routes.set_index('route_id')['route_short_name'].to_dict()

    # B. Link Stops -> Lines (via stop_times & trips)
    st_mini = feed.stop_times[['stop_id', 'trip_id']].copy()
    trips_mini = feed.trips[['trip_id', 'route_id']].copy()

    merged = pd.merge(st_mini, trips_mini, on='trip_id', how='inner')
    merged['line_name'] = merged['route_id'].map(route_map)
    merged['clean_stop_id'] = merged['stop_id'].apply(clean_val)

    # C. Get Stop Names from stops.txt
    stops_df = feed.stops.copy()
    stops_df['clean_id'] = stops_df['stop_id'].apply(clean_val)
    # Map ID -> Name (e.g. "21000" -> "Gare Part-Dieu")
    id_to_name_map = stops_df.set_index('clean_id')['stop_name'].to_dict()

    # D. Create the Master Map: "Gare Part-Dieu" -> {"A", "B", "T1", "T3", "T4"}
    # First, group lines by Stop ID
    id_to_lines = merged.groupby('clean_stop_id')['line_name'].apply(set).to_dict()

    # Then, aggregate by Stop Name
    name_to_lines = {}
    for sid, lines in id_to_lines.items():
        # Find the name of this stop ID
        sname = id_to_name_map.get(sid, "Unknown")
        clean_sname = str(sname).replace('"', '').strip()

        if clean_sname not in name_to_lines:
            name_to_lines[clean_sname] = set()
        name_to_lines[clean_sname].update(lines)

    print(f"   > Associated lines to {len(name_to_lines)} unique stop names.")

    # 4. PREPARE SPATIAL LOOKUP
    id_map = id_to_name_map
    coord_map = {}
    for idx, row in stops_df.iterrows():
        try:
            lat = round(float(row['stop_lat']), 4)
            lon = round(float(row['stop_lon']), 4)
            coord_map[(lon, lat)] = row['stop_name']
        except: continue

    # 5. BUILD GRAPH
    start_sec = start_hour * 3600
    end_sec = end_hour * 3600

    print(f"3. Building graph ({start_sec}s - {end_sec}s)...")
    try:
        G = pt.load_feed_as_graph(feed, start_sec, end_sec, impute_walk_transfers=True)
    except Exception as e:
        print(f"   /!\\ Peartree Error: {e}")
        G = nx.MultiDiGraph()

    # 6. PROCESS NODES
    print(f"4. Processing nodes ({len(G.nodes)} raw nodes)...")

    nodes_list = []
    edges_list = []
    grouped_nodes = {}
    node_name_to_id = {}

    sorted_nodes = sorted(list(G.nodes(data=True)), key=lambda x: str(x[0]))

    debug_limit = 0

    for i, (node_id, data) in enumerate(sorted_nodes):
        # 1. Resolve Name (The Spatial Fix)
        stop_name = get_stop_name_spatial(data, coord_map, id_map, node_id)
        clean_name = str(stop_name).replace('"', '').strip()

        # 2. Retrieve Lines using the NAME
        real_modes = name_to_lines.get(clean_name, set())

        # Remove nan/None
        valid_modes = {m for m in real_modes if m and str(m) != 'nan'}

        # Debug print for verifying lines are found
        if debug_limit < 3 and len(valid_modes) > 0 and clean_name != "Unknown":
            print(f"   [DEBUG] {clean_name} -> {valid_modes}")
            debug_limit += 1

        if clean_name not in grouped_nodes:
            grouped_nodes[clean_name] = {
                "x_sum": 0, "y_sum": 0, "count": 0,
                "modes": set(), "cost": 0
            }

        grouped_nodes[clean_name]["x_sum"] += float(data.get("x", 0))
        grouped_nodes[clean_name]["y_sum"] += float(data.get("y", 0))
        grouped_nodes[clean_name]["count"] += 1
        grouped_nodes[clean_name]["modes"].update(valid_modes)

    # Output Nodes
    for idx, (name, data) in enumerate(grouped_nodes.items()):
        node_obj = {
            "id": str(idx),
            "name": name,
            "x": data["x_sum"] / data["count"],
            "y": data["y_sum"] / data["count"],
            "modes": list(data["modes"]),
            "boarding_cost": 0
        }
        nodes_list.append(node_obj)
        node_name_to_id[name] = idx

    # Output Edges
    orig_to_new_id = {}
    for node_id, data in sorted_nodes:
        s_name = get_stop_name_spatial(data, coord_map, id_map, node_id)
        clean = str(s_name).replace('"', '').strip()
        if clean in node_name_to_id:
            orig_to_new_id[node_id] = node_name_to_id[clean]

    unique_edges = {}
    for u, v, data in G.edges(data=True):
        if u in orig_to_new_id and v in orig_to_new_id:
            new_u = orig_to_new_id[u]
            new_v = orig_to_new_id[v]
            if new_u == new_v: continue

            w = int(float(data.get("length", 0)))
            if (new_u, new_v) not in unique_edges or w < unique_edges[(new_u, new_v)]:
                unique_edges[(new_u, new_v)] = w

    for (u, v), w in unique_edges.items():
        edges_list.append([u, v, w])

    final_data = {
        "metadata": {
            "period": f"{start_hour}-{end_hour}",
            "node_count": len(nodes_list),
            "edge_count": len(edges_list)
        },
        "nodes": nodes_list,
        "edges": edges_list
    }

    print(f"   > Saving to: {output_json_path}")
    print(f"   > Final Graph: {len(nodes_list)} nodes, {len(edges_list)} edges.")

    with open(output_json_path, 'w', encoding='utf-8') as f:
        json.dump(final_data, f, ensure_ascii=False, separators=(',', ':'))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("zip_file")
    parser.add_argument("--out", required=True)
    parser.add_argument("--start", type=float, required=True)
    parser.add_argument("--end", type=float, required=True)
    parser.add_argument("--lines", help="Lines filter", default=None)
    args = parser.parse_args()

    generate_graph(args.zip_file, args.out, args.start, args.end, args.lines)

if __name__ == "__main__":
    main()