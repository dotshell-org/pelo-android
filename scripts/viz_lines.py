import json
import argparse
import folium
import statistics
import os

# --- COLOR PALETTE ---
STYLES = {
    "METRO":     {"color": "#EC4899", "weight": 6, "opacity": 1.0, "z_index": 100},
    "TRAM":      {"color": "#A855F7", "weight": 4, "opacity": 1.0, "z_index": 90},
    "FUNI":      {"color": "#84CC16", "weight": 4, "opacity": 1.0, "z_index": 80},
    "TRAMBUS":   {"color": "#eab308", "weight": 5, "opacity": 1.0, "z_index": 70}, # Yellow (Tb11)
    "NAVIGONE":  {"color": "#14b8a6", "weight": 4, "opacity": 1.0, "z_index": 60}, # Turquoise
    "CHRONO":    {"color": "#EF4444", "weight": 3, "opacity": 0.6, "z_index": 20}, # Red (thicker than bus)
    "BUS":       {"color": "#EF4444", "weight": 1.5, "opacity": 0.4, "z_index": 10} # Red (thin)
}

METRO_COLORS = {"A": "#EC4899", "B": "#3B82F6", "C": "#F59E0B", "D": "#22C55E"}

# --- STRICT LISTS ---
# Only Tb11 is a Trambus.
# Adding case variants just in case (TB11, tb11)
REAL_TRAMBUS = {"Tb11", "TB11", "tb11"}

REAL_NAVIGONE = {"N1", "N2", "NAVI1"}
REAL_FUNICULAR = {"F1", "F2"}

def analyze_segment(modes_u, modes_v):
    set_u = {str(m).strip() for m in modes_u}
    set_v = {str(m).strip() for m in modes_v}

    # Case-insensitive intersection to be safe
    u_upper = {m.upper(): m for m in set_u}
    v_upper = {m.upper(): m for m in set_v}
    common_keys = set(u_upper.keys()).intersection(set(v_upper.keys()))

    if not common_keys: return None, None

    # Retrieve the real name (original casing) for display
    # Arbitrarily taking the one from node U
    common_modes = [u_upper[k] for k in common_keys]

    # 1. METRO
    for m in common_modes:
        if m in ["A", "B", "C", "D"]:
            s = STYLES["METRO"].copy()
            s["color"] = METRO_COLORS[m]
            return s, f"Métro {m}"

    # 2. FUNICULAR
    for m in common_modes:
        if m in REAL_FUNICULAR or (m.startswith("F") and len(m) < 3):
            return STYLES["FUNI"], m

    # 3. TRAMWAY
    for m in common_modes:
        if (m.startswith("T") and m[1:].isdigit() and m.upper() not in ["TB11"]) or m == "RX":
            return STYLES["TRAM"], m

    # 4. TRAMBUS (Only Tb11)
    for m in common_modes:
        if m in REAL_TRAMBUS or m.upper() == "TB11":
            return STYLES["TRAMBUS"], m

    # 5. NAVIGONE
    for m in common_modes:
        if m in REAL_NAVIGONE or "NAVI" in m.upper():
            return STYLES["NAVIGONE"], m

    # 6. CHRONO (The C lines)
    # Treated like Bus (Red) but with higher Z-Index (20 vs 10)
    for m in common_modes:
        if m.upper().startswith("C") and m[1:].isdigit():
            return STYLES["CHRONO"], m

    # 7. BUS (Everything else)
    return STYLES["BUS"], common_modes[0]

def create_map(json_path, output_html):
    print(f"Loading: {json_path}...")
    if not os.path.exists(json_path): return

    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    nodes = data.get("nodes", [])
    edges = data.get("edges", [])

    if not nodes: return

    # Centering
    lats = [n["y"] for n in nodes]
    lons = [n["x"] for n in nodes]
    m = folium.Map(location=[statistics.mean(lats), statistics.mean(lons)], zoom_start=13, tiles="CartoDB dark_matter")

    fg_edges = folium.FeatureGroup(name="Lignes")
    fg_nodes = folium.FeatureGroup(name="Arrêts")

    drawable_edges = []

    print(f"Analyzing {len(edges)} segments...")
    stats = {"Metro":0, "Tram":0, "Trambus":0, "Chrono":0, "Bus":0}

    for u_idx, v_idx, weight in edges:
        try:
            node_u = nodes[u_idx]
            node_v = nodes[v_idx]

            style, mode_name = analyze_segment(node_u.get("modes", []), node_v.get("modes", []))

            if style is None: continue

            # Quick stats
            if style == STYLES["TRAMBUS"]: stats["Trambus"] += 1
            elif style == STYLES["CHRONO"]: stats["Chrono"] += 1
            elif style == STYLES["BUS"]: stats["Bus"] += 1

            item = {
                "coord_u": [node_u["y"], node_u["x"]],
                "coord_v": [node_v["y"], node_v["x"]],
                "style": style,
                "mode": mode_name,
                "weight": weight
            }
            drawable_edges.append(item)

        except IndexError: continue

    print(f"   > Stats detected: {stats}")

    # Sort by Z-Index (Bus bottom -> Chrono -> Trambus -> Tram -> Metro top)
    drawable_edges.sort(key=lambda x: x["style"]["z_index"])

    for item in drawable_edges:
        s = item["style"]
        folium.PolyLine(
            locations=[item["coord_u"], item["coord_v"]],
            color=s["color"],
            weight=s["weight"],
            opacity=s["opacity"],
            tooltip=f"{item['mode']} ({item['weight']}s)"
        ).add_to(fg_edges)

    # --- NODES (STOPS) RENDERING ---
    for node in nodes:
        folium.CircleMarker(
            location=[node["y"], node["x"]],
            radius=6,              # INCREASED SIZE (was 1)
            color="#ffffff",       # Border color
            weight=2,              # Border thickness (was 0)
            fill=True,
            fill_opacity=0.9,      # Higher opacity (was 0.2)
            tooltip=node["name"],
            popup=node["name"]
        ).add_to(fg_nodes)

    fg_edges.add_to(m)
    fg_nodes.add_to(m)
    folium.LayerControl(collapsed=False).add_to(m)

    # Legend
    legend_html = f'''
     <div style="position: fixed; 
     bottom: 30px; left: 30px; width: 150px; height: auto; 
     border:2px solid #555; z-index:9999; font-size:12px;
     background-color:rgba(0,0,0,0.85); color:white; padding: 10px; border-radius: 8px;">
     <b>Légende</b><br>
     <i style="background:{METRO_COLORS['A']};width:8px;height:8px;display:inline-block;"></i> Métro<br>
     <i style="background:{STYLES['TRAM']['color']};width:8px;height:8px;display:inline-block;"></i> Tram<br>
     <i style="background:{STYLES['TRAMBUS']['color']};width:8px;height:8px;display:inline-block;"></i> Trambus<br>
     <i style="background:{STYLES['NAVIGONE']['color']};width:8px;height:8px;display:inline-block;"></i> Navigone<br>
     <i style="background:{STYLES['BUS']['color']};width:8px;height:8px;display:inline-block;"></i> Bus<br>
     </div>
     '''
    m.get_root().html.add_child(folium.Element(legend_html))

    print(f"Saving: {output_html}")
    m.save(output_html)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("json_file")
    parser.add_argument("--out", default="scripts/output/lines_viz.html")
    args = parser.parse_args()
    create_map(args.json_file, args.out)

if __name__ == "__main__":
    main()