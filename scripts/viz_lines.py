import json
import argparse
import folium
import statistics
import os

# --- COLOR PALETTE (From your Kotlin code) ---
METRO_A_COLOR = "#EC4899"  # Pink
METRO_B_COLOR = "#3B82F6"  # Blue
METRO_C_COLOR = "#F59E0B"  # Orange
METRO_D_COLOR = "#22C55E"  # Green
TRAM_COLOR    = "#A855F7"  # Purple
FUNICULAR_COLOR = "#84CC16" # Lime
NAVIGONE_COLOR  = "#14b8a6" # Teal
BUS_COLOR       = "#EF4444" # Red
TRAMBUS_COLOR   = "#eab308" # Yellow (Used for C lines in Lyon)
DEFAULT_COLOR   = "#666666" # Grey for walk/unknown

def get_line_color(modes_u, modes_v):
    """
    Determines the edge color based on the intersection of modes
    between two nodes.
    Priority: Metro > Funi > Tram > Trambus (C) > Navigone > Bus
    """
    # Find common modes between the two stops
    common_modes = set(modes_u).intersection(set(modes_v))

    if not common_modes:
        return DEFAULT_COLOR

    # 1. METRO (Highest Priority)
    if "A" in common_modes: return METRO_A_COLOR
    if "B" in common_modes: return METRO_B_COLOR
    if "C" in common_modes: return METRO_C_COLOR
    if "D" in common_modes: return METRO_D_COLOR

    # 2. FUNICULAR (F1, F2)
    for m in common_modes:
        if m.startswith("F") and len(m) < 4:
            return FUNICULAR_COLOR

    # 3. TRAM (T1, T2, etc.)
    for m in common_modes:
        if m.startswith("T") and len(m) < 4:
            return TRAM_COLOR

    # 4. TRAMBUS (C1, C2... lines "Cristalis" or Strong lines)
    for m in common_modes:
        if m.startswith("C") and len(m) < 4:
            return TRAMBUS_COLOR

    # 5. NAVIGONE (N1, N2... River shuttle)
    for m in common_modes:
        if m.startswith("N") and len(m) < 4:
            return NAVIGONE_COLOR

    # 6. BUS (Default for everything else)
    # If there is any other mode remaining, assume it's a bus
    return BUS_COLOR

def create_map(json_path, output_html):
    print(f"Loading file: {json_path}...")

    if not os.path.exists(json_path):
        print("Error: JSON file not found.")
        return

    try:
        with open(json_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except json.JSONDecodeError:
        print("Error: Failed to decode JSON.")
        return

    nodes = data.get("nodes", [])
    edges = data.get("edges", [])

    if not nodes:
        print("No nodes found.")
        return

    # Center Map
    lats = [n["y"] for n in nodes]
    lons = [n["x"] for n in nodes]
    center_lat = statistics.mean(lats)
    center_lon = statistics.mean(lons)

    # Use 'CartoDB dark_matter' to make neon colors pop
    m = folium.Map(location=[center_lat, center_lon], zoom_start=13, tiles="CartoDB dark_matter")

    fg_edges = folium.FeatureGroup(name="Lines (Edges)")
    fg_nodes = folium.FeatureGroup(name="Stops (Nodes)")

    # --- DRAW EDGES ---
    print(f"Drawing {len(edges)} edges...")
    for u_idx, v_idx, weight in edges:
        try:
            node_u = nodes[u_idx]
            node_v = nodes[v_idx]

            coord_u = [node_u["y"], node_u["x"]]
            coord_v = [node_v["y"], node_v["x"]]

            modes_u = node_u.get("modes", [])
            modes_v = node_v.get("modes", [])

            # Determine Color
            color = get_line_color(modes_u, modes_v)

            # Thickness: Metros are thicker
            weight_line = 4
            if color in [METRO_A_COLOR, METRO_B_COLOR, METRO_C_COLOR, METRO_D_COLOR]:
                weight_line = 6

            folium.PolyLine(
                locations=[coord_u, coord_v],
                color=color,
                weight=weight_line,
                opacity=0.8,
                tooltip=f"Time: {weight}s"
            ).add_to(fg_edges)

        except IndexError:
            continue

    # --- DRAW NODES ---
    print(f"Drawing {len(nodes)} nodes...")
    for node in nodes:
        lat = node["y"]
        lon = node["x"]
        name = node["name"]
        modes = ", ".join(node.get("modes", []))

        html_popup = f"""
        <div style="font-family:sans-serif; width:150px; color:black">
            <b>{name}</b><br>
            <hr>
            Lines: {modes}
        </div>
        """

        folium.CircleMarker(
            location=[lat, lon],
            radius=2, # Small nodes to emphasize lines
            color="#ffffff",
            weight=0,
            fill=True,
            fill_color="#ffffff",
            fill_opacity=0.5,
            tooltip=name,
            popup=folium.Popup(html_popup, max_width=200)
        ).add_to(fg_nodes)

    fg_edges.add_to(m)
    fg_nodes.add_to(m)
    folium.LayerControl(collapsed=False).add_to(m)

    # --- CUSTOM LEGEND ---
    legend_html = f'''
     <div style="position: fixed; 
     bottom: 30px; left: 30px; width: 150px; height: auto; 
     border:2px solid grey; z-index:9999; font-size:14px;
     background-color:rgba(0,0,0,0.7); color:white; padding: 10px; border-radius: 5px;">
     <b>Transport Lines</b><br>
     <i style="background:{METRO_A_COLOR};width:10px;height:10px;display:inline-block;margin-right:5px;"></i> Metro A<br>
     <i style="background:{METRO_B_COLOR};width:10px;height:10px;display:inline-block;margin-right:5px;"></i> Metro B<br>
     <i style="background:{METRO_C_COLOR};width:10px;height:10px;display:inline-block;margin-right:5px;"></i> Metro C<br>
     <i style="background:{METRO_D_COLOR};width:10px;height:10px;display:inline-block;margin-right:5px;"></i> Metro D<br>
     <i style="background:{TRAM_COLOR};width:10px;height:10px;display:inline-block;margin-right:5px;"></i> Tram (T)<br>
     <i style="background:{TRAMBUS_COLOR};width:10px;height:10px;display:inline-block;margin-right:5px;"></i> Trambus (C)<br>
     <i style="background:{BUS_COLOR};width:10px;height:10px;display:inline-block;margin-right:5px;"></i> Bus<br>
     <i style="background:{FUNICULAR_COLOR};width:10px;height:10px;display:inline-block;margin-right:5px;"></i> Funicular<br>
     <i style="background:{NAVIGONE_COLOR};width:10px;height:10px;display:inline-block;margin-right:5px;"></i> Navigone<br>
     </div>
     '''
    m.get_root().html.add_child(folium.Element(legend_html))

    print(f"Saving map to: {output_html}")
    m.save(output_html)
    print("Done.")

def main():
    parser = argparse.ArgumentParser(description="Visualize network with specific line colors")
    parser.add_argument("json_file", help="Path to the JSON graph file")
    parser.add_argument("--out", default="scripts/output/lines_viz.html", help="Output HTML path")
    args = parser.parse_args()

    create_map(args.json_file, args.out)

if __name__ == "__main__":
    main()