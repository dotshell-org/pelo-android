import json
import argparse
import os
from pyvis.network import Network

# --- CONFIGURATION VISUELLE ---
STYLES = {
    # LIGNES FORTES
    "METRO":     {"color": "#EC4899", "width": 8, "z": 50}, # Rose
    "TRAM":      {"color": "#A855F7", "width": 6, "z": 40}, # Violet
    "TRAMBUS":   {"color": "#eab308", "width": 6, "z": 35}, # Jaune
    "FUNI":      {"color": "#84CC16", "width": 5, "z": 30}, # Vert
    "NAVIGONE":  {"color": "#14b8a6", "width": 5, "z": 25}, # Turquoise

    # CHRONO UNIQUEMENT (Pas de bus standards)
    "CHRONO":    {"color": "#EF4444", "width": 4, "z": 20}, # Rouge vif

    "DEFAULT_NODE": "#ffffff"
}

METRO_COLORS = {"A": "#EC4899", "B": "#3B82F6", "C": "#F59E0B", "D": "#22C55E"}

# Listes strictes pour identification
PATTERNS = {
    "METRO": ["A", "B", "C", "D"],
    "TRAM": ["T1", "T2", "T3", "T4", "T5", "T6", "T7", "RX"],
    "TRAMBUS": ["TB11", "Tb11"],
    "FUNI": ["F1", "F2"],
    "NAVIGONE": ["N1", "N2", "NAVI1"]
}

def analyze_connection(modes_u, modes_v):
    """
    Identifie le type de ligne.
    Renvoie le style si c'est une Ligne Forte ou Chrono.
    Renvoie None si c'est un bus standard.
    """
    # 1. Intersection des modes
    set_u = {str(m).strip().upper() for m in modes_u}
    set_v = {str(m).strip().upper() for m in modes_v}
    common = set_u.intersection(set_v)

    if not common: return None

    # 2. Vérification Lignes Fortes
    for m in common:
        # Métro
        if m in PATTERNS["METRO"]:
            col = METRO_COLORS.get(m, STYLES["METRO"]["color"])
            return {"color": col, "width": STYLES["METRO"]["width"], "label": f"Métro {m}"}
        # Tram
        if m in PATTERNS["TRAM"] or (m.startswith("T") and m[1:].isdigit() and "TB" not in m):
            return {"color": STYLES["TRAM"]["color"], "width": STYLES["TRAM"]["width"], "label": m}
        # Trambus
        if m in PATTERNS["TRAMBUS"]:
            return {"color": STYLES["TRAMBUS"]["color"], "width": STYLES["TRAMBUS"]["width"], "label": m}
        # Funi
        if m in PATTERNS["FUNI"]:
            return {"color": STYLES["FUNI"]["color"], "width": STYLES["FUNI"]["width"], "label": m}
        # Navigone
        if any(p in m for p in ["NAVI", "N1", "N2"]):
            return {"color": STYLES["NAVIGONE"]["color"], "width": STYLES["NAVIGONE"]["width"], "label": m}

    # 3. Vérification Chrono (C1, C20...)
    for m in common:
        if m.startswith("C") and m[1:].isdigit():
            return {"color": STYLES["CHRONO"]["color"], "width": STYLES["CHRONO"]["width"], "label": m}

    # 4. Bus Standards -> REJETÉ
    return None


def create_network_viz(json_path, output_html):
    print(f"Chargement : {json_path}")
    if not os.path.exists(json_path):
        print("Fichier JSON introuvable.")
        return

    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # Initialisation PyVis (Plein écran, pas de menu)
    net = Network(
        height="100vh",
        width="100%",
        bgcolor="#222222",
        font_color="white",
        select_menu=False,
        filter_menu=False,
        cdn_resources='in_line'
    )

    nodes = data.get("nodes", [])
    edges = data.get("edges", [])

    valid_node_indices = set()
    edges_to_draw = []

    print("Filtrage des connexions (Fortes + Chrono uniquement)...")

    for u_idx, v_idx, weight in edges:
        try:
            node_u = nodes[int(u_idx)]
            node_v = nodes[int(v_idx)]

            style = analyze_connection(node_u.get("modes", []), node_v.get("modes", []))

            if style:
                valid_node_indices.add(int(u_idx))
                valid_node_indices.add(int(v_idx))

                edges_to_draw.append({
                    "from": int(u_idx),
                    "to": int(v_idx),
                    "width": style["width"],
                    "color": style["color"],
                    "title": f"{style['label']} ({weight}s)"
                })
        except IndexError: continue

    print(f" > {len(valid_node_indices)} arrêts et {len(edges_to_draw)} segments conservés.")

    # Ajout des noeuds
    for idx in valid_node_indices:
        n = nodes[idx]
        modes_clean = ", ".join(n.get("modes", []))
        net.add_node(
            int(n['id']),
            label=n['name'],
            title=f"<b>{n['name']}</b><br>{modes_clean}",
            color=STYLES["DEFAULT_NODE"],
            size=12,
            borderWidth=1
        )

    # Ajout des arêtes
    for e in edges_to_draw:
        net.add_edge(e["from"], e["to"], width=e["width"], color=e["color"], title=e["title"])

    # Configuration Physique
    options = {
        "physics": {
            "enabled": True,
            "stabilization": {
                "enabled": False, # Chargement instantané
                "iterations": 100
            },
            "barnesHut": {
                "gravitationalConstant": -10000,
                "centralGravity": 0.4,
                "springLength": 120,
                "springConstant": 0.05,
                "damping": 0.09,
                "avoidOverlap": 0.1
            }
        },
        "interaction": {
            "navigationButtons": True,
            "keyboard": True,
            "tooltipDelay": 200,
            "hideEdgesOnDrag": True
        }
    }
    net.set_options(json.dumps(options))

    print(f"Génération du fichier HTML : {output_html}")
    net.save_graph(output_html)
    print("Terminé.")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("json_file")
    parser.add_argument("--out", default="scripts/output/interactive_viz.html")
    args = parser.parse_args()

    create_network_viz(args.json_file, args.out)

if __name__ == "__main__":
    main()