import json
import argparse
import folium
import statistics
import os
import matplotlib.colors as mcolors
import matplotlib.cm as cm
import numpy as np

def format_duration(seconds):
    """Formats seconds into 'X min Y s' string."""
    m = int(seconds // 60)
    s = int(seconds % 60)
    if m > 0:
        return f"{m} min {s} s"
    return f"{s} s"

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

    # 1. Center Map
    lats = [n["y"] for n in nodes]
    lons = [n["x"] for n in nodes]
    center_lat = statistics.mean(lats)
    center_lon = statistics.mean(lons)

    # Using 'CartoDB dark_matter' makes the full spectrum colors pop vividly
    # But sticking to 'positron' for readability of labels, as preferred usually.
    m = folium.Map(location=[center_lat, center_lon], zoom_start=13, tiles="CartoDB positron")

    fg_edges = folium.FeatureGroup(name="Edges (Links)")
    fg_nodes = folium.FeatureGroup(name="Nodes (Stops)")

    # --- FULL SPECTRUM & LOGARITHMIC SENSITIVITY ---
    print(f"Processing {len(edges)} edges...")

    mapper = None
    if edges:
        weights = [e[2] for e in edges]

        # LogNorm requires values > 0.
        min_val = max(min(weights), 1)
        max_val = max(weights)

        print(f"   > Time range: {min_val}s to {max_val}s")
        print(f"   > Applying Logarithmic Scaling for maximum sensitivity.")
        print(f"   > Palette: Blue -> Cyan -> Green -> Yellow -> Orange -> Red -> Violet")

        # --- RAINBOW PALETTE (Cool to Hot) ---
        # 1. Blue (Fastest)
        # 2. Cyan
        # 3. Green
        # 4. Yellow
        # 5. Orange
        # 6. Red
        # 7. Violet/Magenta (Slowest)
        colors_list = [
            '#0000FF', # Blue
            '#00FFFF', # Cyan
            '#00FF00', # Green
            '#FFFF00', # Yellow
            '#FFA500', # Orange
            '#FF0000', # Red
            '#EE82EE'  # Violet
        ]

        # Create the Full Spectrum Colormap
        cmap = mcolors.LinearSegmentedColormap.from_list("full_spectrum", colors_list)

        # USE LOGNORM: This increases sensitivity for short durations
        norm = mcolors.LogNorm(vmin=min_val, vmax=max_val)

        mapper = cm.ScalarMappable(norm=norm, cmap=cmap)

    # --- DRAW EDGES ---
    for u_idx, v_idx, weight_sec in edges:
        try:
            node_u = nodes[u_idx]
            node_v = nodes[v_idx]
            coord_u = [node_u["y"], node_u["x"]]
            coord_v = [node_v["y"], node_v["x"]]

            # Calculate Color
            if mapper:
                # Ensure value is at least 1 for log scale
                safe_weight = max(weight_sec, 1)
                rgba = mapper.to_rgba(safe_weight)
                hex_color = mcolors.to_hex(rgba)
            else:
                hex_color = "#3388ff"

            # Format Tooltip
            time_str = format_duration(weight_sec)
            tooltip_txt = f"Duration: {time_str}"

            folium.PolyLine(
                locations=[coord_u, coord_v],
                color=hex_color,
                weight=3,
                opacity=0.8,
                tooltip=tooltip_txt
            ).add_to(fg_edges)

        except IndexError:
            continue

    # --- DRAW NODES ---
    print(f"Processing {len(nodes)} nodes...")
    for node in nodes:
        lat = node["y"]
        lon = node["x"]
        name = node["name"]
        modes = ", ".join(node.get("modes", []))

        html_popup = f"""
        <div style="font-family:sans-serif; width:150px">
            <b>{name}</b><br>
            <hr>
            Modes: {modes}<br>
        </div>
        """

        folium.CircleMarker(
            location=[lat, lon],
            radius=3,
            color="#222",
            weight=1,
            fill=True,
            fill_color="#eee",
            fill_opacity=1,
            tooltip=name,
            popup=folium.Popup(html_popup, max_width=200)
        ).add_to(fg_nodes)

    fg_edges.add_to(m)
    fg_nodes.add_to(m)
    folium.LayerControl(collapsed=False).add_to(m)

    # Legend reflecting the Full Spectrum
    legend_html = f'''
     <div style="position: fixed; 
     bottom: 50px; left: 50px; width: 150px;
     border:2px solid grey; z-index:9999; font-size:14px;
     background-color:white; opacity:0.9; padding: 10px;">
     <b>Travel Time</b><br>
     <small>(Log Scale)</small><br>
     <i style="background:#0000FF;width:10px;height:10px;display:inline-block;"></i> Fast (Blue)<br>
     <i style="background:#00FFFF;width:10px;height:10px;display:inline-block;"></i> ...<br>
     <i style="background:#00FF00;width:10px;height:10px;display:inline-block;"></i> Medium (Green)<br>
     <i style="background:#FFFF00;width:10px;height:10px;display:inline-block;"></i> ...<br>
     <i style="background:#FFA500;width:10px;height:10px;display:inline-block;"></i> Slow (Orange)<br>
     <i style="background:#FF0000;width:10px;height:10px;display:inline-block;"></i> ...<br>
     <i style="background:#EE82EE;width:10px;height:10px;display:inline-block;"></i> Longest (Violet)
     </div>
     '''
    m.get_root().html.add_child(folium.Element(legend_html))

    print(f"Saving map to: {output_html}")
    m.save(output_html)
    print("Done.")

def main():
    parser = argparse.ArgumentParser(description="Visualize JSON graph with Full Spectrum Log-Scale")
    parser.add_argument("json_file", help="Path to the generated JSON graph file")
    parser.add_argument("--out", default="scripts/output/graph_viz.html", help="Output HTML file path")
    args = parser.parse_args()

    create_map(args.json_file, args.out)

if __name__ == "__main__":
    main()