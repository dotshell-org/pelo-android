import argparse
import os
import pickle
import pandas as pd

pd.Series.iteritems = pd.Series.items
import networkx as nx
import folium

# --- Color Configuration ---
COLORS = ['#E74C3C', '#3498DB', '#F1C40F', '#2ECC71', '#9B59B6', '#E67E22', '#1ABC9C']
color_map = {}
color_idx = 0


def get_route_color(route_id, mode):
    """Assigns a unique color per route ID, or dashed gray for walking."""
    global color_idx
    if mode == 'transfer':
        return '#95a5a6', 'dash'

    if route_id in color_map:
        return color_map[route_id], None

    color = COLORS[color_idx % len(COLORS)]
    color_map[route_id] = color
    color_idx += 1
    return color, None


def smart_find_node(G, search_term):
    """Searches for a node by ID (exact/partial) or Name."""
    search_term = str(search_term).strip()
    if search_term in G.nodes: return search_term

    candidates = []
    for node in G.nodes():
        node_str = str(node)
        name_str = str(G.nodes[node].get('name', '')).lower()
        search_lower = search_term.lower()

        if search_term in node_str or node_str in search_term:
            candidates.append((node, G.nodes[node].get('name', 'Unknown')))
            continue
        if search_lower in name_str:
            candidates.append((node, G.nodes[node].get('name', 'Unknown')))

    if not candidates: return None
    # Return the first match found
    print(f"   -> Found match: {candidates[0][1]} (ID: {candidates[0][0]})")
    return candidates[0][0]


def find_shortest_path(pkl_path, start_input, end_input, output_html):
    global color_map, color_idx
    color_map = {}
    color_idx = 0

    print(f"Loading graph from: {pkl_path}...")
    with open(pkl_path, 'rb') as f:
        G = pickle.load(f)

    print(f"\n--- Searching for Start: '{start_input}' ---")
    start_node = smart_find_node(G, start_input)
    print(f"\n--- Searching for Destination: '{end_input}' ---")
    end_node = smart_find_node(G, end_input)

    if not start_node or not end_node:
        print("Error: Start or End node could not be found.")
        return

    print(f"Calculating fastest route...")
    try:
        path = nx.shortest_path(G, source=start_node, target=end_node, weight='length')
        total_seconds = nx.shortest_path_length(G, source=start_node, target=end_node, weight='length')
        print(f"\n--- TOTAL TIME: {int(total_seconds // 60)} min ({int(total_seconds)} sec) ---")

        # --- 1. SEGMENT PATH ---
        segments = []
        current_segment = None

        for i in range(len(path) - 1):
            u = path[i]
            v = path[i + 1]
            edge_data = G.get_edge_data(u, v)[0]
            mode = edge_data.get('mode', 'transit')
            route_id = edge_data.get('route_id', 'walk_transfer')

            segment_signature = (mode, route_id)

            if current_segment is None or current_segment['signature'] != segment_signature:
                current_segment = {
                    'signature': segment_signature,
                    'mode': mode,
                    'route_id': route_id,
                    'coords': [[G.nodes[u]['y'], G.nodes[u]['x']]]
                }
                segments.append(current_segment)

            current_segment['coords'].append([G.nodes[v]['y'], G.nodes[v]['x']])

        # --- 2. GENERATE MAP ---
        print("Generating interactive route map...")

        start_coords = [G.nodes[start_node]['y'], G.nodes[start_node]['x']]
        m = folium.Map(location=start_coords, zoom_start=14, tiles='CartoDB positron')

        folium.Marker(
            location=start_coords,
            popup=f"Start: {G.nodes[start_node].get('name')}",
            icon=folium.Icon(color='green', icon='play', prefix='fa')
        ).add_to(m)

        end_coords = [G.nodes[end_node]['y'], G.nodes[end_node]['x']]
        folium.Marker(
            location=end_coords,
            popup=f"End: {G.nodes[end_node].get('name')}",
            icon=folium.Icon(color='red', icon='stop', prefix='fa')
        ).add_to(m)

        for seg in segments:
            mode = seg['mode']
            rid = seg['route_id']
            color, dash_array = get_route_color(rid, mode)

            tooltip_txt = f"Mode: {mode}"
            if mode != 'transfer':
                tooltip_txt += f" | Line: {rid}"

            folium.PolyLine(
                locations=seg['coords'],
                color=color,
                weight=5 if mode != 'transfer' else 3,
                opacity=0.8,
                dash_array=dash_array,
                tooltip=tooltip_txt
            ).add_to(m)

        m.save(output_html)
        print(f"\nROUTE MAP SAVED: {os.path.abspath(output_html)}")

    except nx.NetworkXNoPath:
        print("NO PATH FOUND between these stops.")


def main():
    parser = argparse.ArgumentParser(description="Find fastest route and plot it.")
    parser.add_argument("start", help="Start ID or Name")
    parser.add_argument("end", help="End ID or Name")
    parser.add_argument("--pkl", default="scripts/output/network.pkl")
    parser.add_argument("--outhtml", default="scripts/output/route.html")
    args = parser.parse_args()

    if not os.path.exists(args.pkl):
        print(f"Error: File '{args.pkl}' not found.")
        return

    out_dir = os.path.dirname(args.outhtml)
    if out_dir and not os.path.exists(out_dir):
        os.makedirs(out_dir)

    find_shortest_path(args.pkl, args.start, args.end, args.outhtml)


if __name__ == "__main__":
    main()