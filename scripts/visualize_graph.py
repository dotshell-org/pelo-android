import argparse
import os
import pickle
import pandas as pd

pd.Series.iteritems = pd.Series.items
import osmnx as ox
import folium


def create_map(pkl_path, output_path):
    print(f"Loading graph from {pkl_path}...")
    with open(pkl_path, 'rb') as f:
        G = pickle.load(f)

    print("Converting graph to GeoDataFrames...")
    gdf_nodes, gdf_edges = ox.graph_to_gdfs(G)

    # Prepare node data for display
    gdf_nodes['stop_id'] = gdf_nodes.index.astype(str)

    if 'name' not in gdf_nodes.columns:
        gdf_nodes['name'] = "Unknown"
    gdf_nodes['name'] = gdf_nodes['name'].fillna("Unknown")

    # Columns to show in popup/tooltip
    cols = ['name', 'stop_id', 'y', 'x']
    aliases = ['Stop:', 'ID:', 'Lat:', 'Lon:']

    # Center the map
    avg_lat = gdf_nodes['y'].mean()
    avg_lon = gdf_nodes['x'].mean()

    print("Generating Folium map...")
    m = folium.Map(location=[avg_lat, avg_lon], zoom_start=12, tiles='CartoDB positron')

    # 1. Draw Routes (Edges)
    folium.GeoJson(
        data=gdf_edges,
        name='Routes',
        style_function=lambda x: {'color': '#3388ff', 'weight': 1, 'opacity': 0.5},
        tooltip=None
    ).add_to(m)

    # 2. Draw Stops (Nodes)
    folium.GeoJson(
        data=gdf_nodes,
        name='Stops',
        marker=folium.Circle(radius=3, fill_color='red', color='darkred', weight=1, fill_opacity=0.8),
        tooltip=folium.GeoJsonTooltip(fields=cols, aliases=aliases, sticky=True),
        popup=folium.GeoJsonPopup(fields=cols)
    ).add_to(m)

    folium.LayerControl().add_to(m)
    m.save(output_path)
    print(f"Map saved to: {os.path.abspath(output_path)}")


def main():
    parser = argparse.ArgumentParser(description="Generates a full network map from the graph.")
    parser.add_argument("--pkl", default="scripts/output/network.pkl", help="Path to input PKL file")
    parser.add_argument("--outdir", default="scripts/output", help="Directory to save the HTML map")
    args = parser.parse_args()

    if not os.path.exists(args.pkl):
        print(f"Error: File '{args.pkl}' not found.")
        return

    if not os.path.exists(args.outdir):
        os.makedirs(args.outdir)

    output_file = os.path.join(args.outdir, "map.html")
    create_map(args.pkl, output_file)


if __name__ == "__main__":
    main()