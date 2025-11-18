import pandas as pd
import zipfile
import argparse
import tempfile
import os
import shutil
import sqlite3

def process_gtfs_schedules(zip_path, output_dir):
    db_path = os.path.join(output_dir, 'schedules.db')
    if os.path.exists(db_path):
        print(f"Database '{db_path}' already exists. Deleting it...")
        os.remove(db_path)

    with tempfile.TemporaryDirectory() as temp_dir:
        print(f"Unzipping '{zip_path}' to temporary directory...")
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(temp_dir)
        
        print("Reading GTFS files...")
        id_cols_dtype = {'route_id': str, 'trip_id': str, 'stop_id': str, 'parent_station': str, 'service_id': str}
        routes_df = pd.read_csv(os.path.join(temp_dir, 'routes.txt'), dtype=id_cols_dtype)
        trips_df = pd.read_csv(os.path.join(temp_dir, 'trips.txt'), dtype=id_cols_dtype)
        stop_times_df = pd.read_csv(os.path.join(temp_dir, 'stop_times.txt'), dtype=id_cols_dtype)
        stops_df = pd.read_csv(os.path.join(temp_dir, 'stops.txt'), dtype=id_cols_dtype)
        calendar_df = pd.read_csv(os.path.join(temp_dir, 'calendar.txt'), dtype=id_cols_dtype)

        print("Processing stop hierarchy (parent_station)...")
        stop_id_to_name = stops_df.set_index('stop_id')['stop_name'].to_dict()
        child_to_parent = stops_df.dropna(subset=['parent_station']).set_index('stop_id')['parent_station'].to_dict()

        print("Processing data and joining tables...")
        merged_df = pd.merge(stop_times_df, trips_df, on='trip_id')
        merged_df = pd.merge(merged_df, routes_df, on='route_id')
        
        merged_df['station_id'] = merged_df['stop_id'].map(child_to_parent).fillna(merged_df['stop_id'])
        merged_df['station_name'] = merged_df['station_id'].map(stop_id_to_name)

        # Clean up data before inserting into database
        final_df = merged_df.dropna(subset=['station_name', 'arrival_time', 'service_id', 'direction_id'])
        final_df = final_df[final_df['station_name'].str.strip() != '']
        final_df = final_df[final_df['arrival_time'].str.match(r'^\d+:\d{2}:\d{2}$', na=False)]
        final_df['direction_id'] = final_df['direction_id'].astype(int)

        # --- Database Insertion Logic ---
        print(f"Creating and populating SQLite database at '{db_path}'...")
        conn = sqlite3.connect(db_path)

        # --- Create 'schedules' table ---
        schedules_to_insert = final_df[['route_short_name', 'station_name', 'direction_id', 'arrival_time', 'service_id']].copy()
        schedules_to_insert.rename(columns={'route_short_name': 'route_name'}, inplace=True)
        schedules_to_insert['arrival_time'] = schedules_to_insert['arrival_time'].str[:-3]
        schedules_to_insert.drop_duplicates(inplace=True)
        
        schedules_to_insert.to_sql(
            'schedules', conn, if_exists='replace', index=False,
            dtype={'route_name': 'TEXT', 'station_name': 'TEXT', 'direction_id': 'INTEGER', 'arrival_time': 'TEXT', 'service_id': 'TEXT'}
        )
        print("Creating index on 'schedules' table...")
        conn.execute("CREATE INDEX idx_schedules ON schedules (route_name, station_name, direction_id, service_id)")

        # --- Create 'directions' table ---
        print("Extracting direction headsigns...")
        directions_df = final_df[['route_short_name', 'direction_id', 'trip_headsign']].copy()
        directions_df.rename(columns={'route_short_name': 'route_name'}, inplace=True)
        directions_df.dropna(subset=['trip_headsign'], inplace=True)
        directions_df = directions_df.groupby(['route_name', 'direction_id']).agg(
            trip_headsign=('trip_headsign', lambda x: x.value_counts().index[0] if not x.value_counts().empty else '')
        ).reset_index()

        directions_df.to_sql(
            'directions', conn, if_exists='replace', index=False,
            dtype={'route_name': 'TEXT', 'direction_id': 'INTEGER', 'trip_headsign': 'TEXT'}
        )
        print("Creating index on 'directions' table...")
        conn.execute("CREATE INDEX idx_directions ON directions (route_name)")

        # --- Create 'calendar' table ---
        print("Copying calendar data to database...")
        calendar_df.to_sql(
            'calendar', conn, if_exists='replace', index=False
        )
        conn.execute("CREATE INDEX idx_calendar ON calendar (service_id)")
        
        conn.commit()
        conn.close()
    
    print(f"Done! Database '{db_path}' has been generated with 'schedules', 'directions', and 'calendar' tables.")

def main():
    parser = argparse.ArgumentParser(description="Process a GTFS ZIP file to create a schedules.db SQLite database.")
    parser.add_argument("zip_file", help="Path to the GTFS ZIP file.")
    parser.add_argument("--output_dir", default="app/src/main/assets/databases", help="Output directory for the database file (default: 'app/src/main/assets/databases').")
    
    args = parser.parse_args()
    
    if not os.path.exists(args.zip_file):
        print(f"Error: File '{args.zip_file}' not found.")
        return
        
    os.makedirs(args.output_dir, exist_ok=True)
    process_gtfs_schedules(args.zip_file, args.output_dir)

if __name__ == "__main__":
    main()
