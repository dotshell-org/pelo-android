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

        # Filter format valid
        final_df = final_df[final_df['arrival_time'].str.match(r'^\d+:\d{2}:\d{2}$', na=False)]

        # --- MODIFICATION START: Conversion des heures >= 24h ---
        print("Normalizing times (converting 24h+ to 00h+)...")
        def normalize_gtfs_time(time_str):
            try:
                parts = time_str.split(':')
                hour = int(parts[0])
                if hour >= 24:
                    hour = hour % 24
                    # Reconstruit la chaine en format HH:MM:SS avec zero-padding
                    return f"{hour:02d}:{parts[1]}:{parts[2]}"
                return time_str
            except:
                return time_str

        final_df['arrival_time'] = final_df['arrival_time'].apply(normalize_gtfs_time)
        # --- MODIFICATION END ---

        final_df['direction_id'] = final_df['direction_id'].astype(int)

        # --- Database Insertion Logic ---
        print(f"Creating and populating SQLite database at '{db_path}'...")
        conn = sqlite3.connect(db_path)

        # --- Create 'schedules' table ---
        schedules_to_insert = final_df[['route_short_name', 'station_name', 'direction_id', 'arrival_time', 'service_id']].copy()
        schedules_to_insert.rename(columns={'route_short_name': 'route_name'}, inplace=True)
        # Remove seconds just before insertion, now that hours are normalized
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

        print("Injecting hardcoded NAVI1 schedules...")
        inject_hardcoded_navigone_schedules(conn)

        conn.commit()
        conn.close()

    print(f"Done! Database '{db_path}' has been generated with 'schedules', 'directions', and 'calendar' tables.")

def inject_hardcoded_navigone_schedules(conn):
    """
    Injects hardcoded schedules for the NAVI1 line.
    This is a workaround for missing data in the GTFS file.
    """
    cursor = conn.cursor()

    # --- 1. Define Service IDs and Calendar Entries ---
    # Cas particulier NAVI1: Confluence est desservi à horaires réduits le mercredi (période scolaire).
    # On sépare donc les services: L/M/J/V, Mercredi, et Week-end/Jours fériés.
    navigone_calendar = [
        # Service Lundi/Mardi/Jeudi/Vendredi (mercredi exclu)
        ('NAVI1_WEEKDAY_MTTF', 1, 1, 0, 1, 1, 0, 0, '20250101', '20251231'),
        # Service Mercredi uniquement
        ('NAVI1_WEDNESDAY', 0, 0, 1, 0, 0, 0, 0, '20250101', '20251231'),
        # Service Samedi/Dimanche & Fêtes
        ('NAVI1_WEEKEND', 0, 0, 0, 0, 0, 1, 1, '20250101', '20251231')
    ]

    # --- 2. Define Headsigns for Directions table ---
    navigone_directions = [
        ('NAVI1', 0, 'Confluence'),
        ('NAVI1', 1, 'Vaise - Industrie')
    ]

    # --- 3. Define Schedules ---
    # Direction 0: Vaise -> Confluence
    # Direction 1: Confluence -> Vaise
    schedules = []

    # Small helpers to offset HH:MM by N minutes
    def add_minutes(time_str, minutes):
        try:
            h, m = map(int, time_str.split(":"))
            total = h * 60 + m + minutes
            total %= (24 * 60)
            return f"{total // 60:02d}:{total % 60:02d}"
        except Exception:
            return time_str

    # Service: Lundi, mardi, jeudi et vendredi (Weekday MTTF)
    # Direction 0 (to Confluence)
    vaise_wd = ['07:00', '07:30', '08:00', '08:30', '09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '16:30', '17:00', '17:30', '18:00', '18:30', '19:00', '19:30', '20:00', '21:00']
    subs_wd = ['07:15', '07:45', '08:15', '08:45', '09:15', '10:15', '11:15', '12:15', '13:15', '14:15', '15:15', '16:15', '16:45', '17:15', '17:45', '18:15', '18:45', '19:15', '19:45', '20:15', '21:15']
    terr_wd = ['07:24', '07:54', '08:24', '08:54', '09:24', '10:24', '11:24', '12:24', '13:24', '14:24', '15:24', '16:24', '16:54', '17:24', '17:54', '18:24', '18:54', '19:24', '19:54', '20:24', '21:24']

    # MTTF: horaires complets, y compris Confluence toute la journée
    schedules.extend([('NAVI1', "VAISE - INDUSTRIE", 0, t, 'NAVI1_WEEKDAY_MTTF') for t in vaise_wd])
    schedules.extend([('NAVI1', "SUBSISTANCES", 0, t, 'NAVI1_WEEKDAY_MTTF') for t in subs_wd])
    schedules.extend([('NAVI1', "TERRASSES PRESQU'ÎLE", 0, t, 'NAVI1_WEEKDAY_MTTF') for t in terr_wd])
    confl_mttf_dir0 = [add_minutes(t, 16) for t in terr_wd]
    schedules.extend([('NAVI1', "CONFLUENCE", 0, t, 'NAVI1_WEEKDAY_MTTF') for t in confl_mttf_dir0])
    # Direction 1 (to Vaise)
    terr_wd_dir1 = ['07:01', '07:31', '08:01', '08:31', '09:01', '10:01', '11:01', '12:01', '13:01', '14:01', '15:01', '16:01', '16:31', '17:01', '17:31', '18:01', '18:31', '19:01', '19:31', '20:01', '21:01']
    subs_wd_dir1 = ['07:11', '07:41', '08:11', '08:41', '09:11', '10:11', '11:11', '12:11', '13:11', '14:11', '15:11', '16:11', '16:41', '17:11', '17:41', '18:11', '18:41', '19:11', '19:41', '20:11', '21:11']
    vaise_wd_dir1 = ['07:24', '07:54', '08:24', '08:54', '09:24', '10:24', '11:24', '12:24', '13:24', '14:24', '15:24', '16:24', '16:54', '17:24', '17:54', '18:24', '18:54', '19:24', '19:54', '20:24', '21:24']

    # MTTF: horaires complets, y compris Confluence toute la journée
    schedules.extend([('NAVI1', "TERRASSES PRESQU'ÎLE", 1, t, 'NAVI1_WEEKDAY_MTTF') for t in terr_wd_dir1])
    confl_mttf_dir1 = [add_minutes(t, -18) for t in terr_wd_dir1]
    schedules.extend([('NAVI1', "CONFLUENCE", 1, t, 'NAVI1_WEEKDAY_MTTF') for t in confl_mttf_dir1])
    schedules.extend([('NAVI1', "SUBSISTANCES", 1, t, 'NAVI1_WEEKDAY_MTTF') for t in subs_wd_dir1])
    schedules.extend([('NAVI1', "VAISE - INDUSTRIE", 1, t, 'NAVI1_WEEKDAY_MTTF') for t in vaise_wd_dir1])

    # Service: Mercredi (réduit à Confluence)
    # On conserve les mêmes horaires pour Vaise/Subsistances/Terrasses que MTTF,
    # mais Confluence n'est desservi que de 11:40 à 15:40 (dir 0) et 11:43 à 15:43 (dir 1).
    schedules.extend([('NAVI1', "VAISE - INDUSTRIE", 0, t, 'NAVI1_WEDNESDAY') for t in vaise_wd])
    schedules.extend([('NAVI1', "SUBSISTANCES", 0, t, 'NAVI1_WEDNESDAY') for t in subs_wd])
    schedules.extend([('NAVI1', "TERRASSES PRESQU'ÎLE", 0, t, 'NAVI1_WEDNESDAY') for t in terr_wd])
    # Confluence mercredi (dir 0): 11:40..15:40 toutes les heures
    confl_wed_dir0 = ['11:40', '12:40', '13:40', '14:40', '15:40']
    schedules.extend([('NAVI1', "CONFLUENCE", 0, t, 'NAVI1_WEDNESDAY') for t in confl_wed_dir0])

    schedules.extend([('NAVI1', "TERRASSES PRESQU'ÎLE", 1, t, 'NAVI1_WEDNESDAY') for t in terr_wd_dir1])
    schedules.extend([('NAVI1', "SUBSISTANCES", 1, t, 'NAVI1_WEDNESDAY') for t in subs_wd_dir1])
    schedules.extend([('NAVI1', "VAISE - INDUSTRIE", 1, t, 'NAVI1_WEDNESDAY') for t in vaise_wd_dir1])
    # Confluence mercredi (dir 1): 11:43..15:43 toutes les heures
    confl_wed_dir1 = ['11:43', '12:43', '13:43', '14:43', '15:43']
    schedules.extend([('NAVI1', "CONFLUENCE", 1, t, 'NAVI1_WEDNESDAY') for t in confl_wed_dir1])

    # Service: Samedi, Dimanche et fêtes (Weekend)
    # Direction 0 (to Confluence)
    schedules.extend([('NAVI1', "VAISE - INDUSTRIE", 0, time, 'NAVI1_WEEKEND') for time in ['09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00', '18:00', '19:00', '20:00', '21:00']])
    schedules.extend([('NAVI1', "SUBSISTANCES", 0, time, 'NAVI1_WEEKEND') for time in ['09:15', '10:15', '11:15', '12:15', '13:15', '14:15', '15:15', '16:15', '17:15', '18:15', '19:15', '20:15', '21:15']])
    schedules.extend([('NAVI1', "TERRASSES PRESQU'ÎLE", 0, time, 'NAVI1_WEEKEND') for time in ['09:26', '10:26', '11:26', '12:26', '13:26', '14:26', '15:26', '16:26', '17:26', '18:26', '19:26', '20:26', '21:26']])
    schedules.extend([('NAVI1', "CONFLUENCE", 0, time, 'NAVI1_WEEKEND') for time in ['09:40', '10:40', '11:40', '12:40', '13:40', '14:40', '15:40', '16:40', '17:40', '18:40', '19:40', '20:40', '21:40']])
    # Direction 1 (to Vaise)
    schedules.extend([('NAVI1', "CONFLUENCE", 1, time, 'NAVI1_WEEKEND') for time in ['08:43', '09:43', '10:43', '11:43', '12:43', '13:43', '14:43', '15:43', '16:43', '17:43', '18:43', '19:43', '20:43']])
    schedules.extend([('NAVI1', "TERRASSES PRESQU'ÎLE", 1, time, 'NAVI1_WEEKEND') for time in ['09:01', '10:01', '11:01', '12:01', '13:01', '14:01', '15:01', '16:01', '17:01', '18:01', '19:01', '20:01', '21:01']])
    schedules.extend([('NAVI1', "SUBSISTANCES", 1, time, 'NAVI1_WEEKEND') for time in ['09:11', '10:11', '11:11', '12:11', '13:11', '14:11', '15:11', '16:11', '17:11', '18:11', '19:11', '20:11', '21:11']])
    schedules.extend([('NAVI1', "VAISE - INDUSTRIE", 1, time, 'NAVI1_WEEKEND') for time in ['09:24', '10:24', '11:24', '12:24', '13:24', '14:24', '15:24', '16:24', '17:24', '18:24', '19:24', '20:24', '21:24']])

    # --- 4. Database Operations ---
    print("  - Deleting existing NAVI1 data...")
    cursor.execute("DELETE FROM schedules WHERE route_name = 'NAVI1'")
    cursor.execute("DELETE FROM directions WHERE route_name = 'NAVI1'")

    print("  - Inserting new NAVI1 calendar, directions, and schedules...")
    cursor.executemany("INSERT OR IGNORE INTO calendar (service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday, start_date, end_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", navigone_calendar)
    cursor.executemany("INSERT OR REPLACE INTO directions (route_name, direction_id, trip_headsign) VALUES (?, ?, ?)", navigone_directions)
    cursor.executemany("INSERT INTO schedules (route_name, station_name, direction_id, arrival_time, service_id) VALUES (?, ?, ?, ?, ?)", schedules)

    print(f"  - Injected {len(schedules)} new schedule entries for NAVI1.")

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