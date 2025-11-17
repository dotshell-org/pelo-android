import pandas as pd
import zipfile
import argparse
import tempfile
import os
import shutil

def sanitize_filename(name):
    """
    Sanitizes a string to be used as a valid filename.
    """
    return "".join(c for c in name if c.isalnum() or c in (' ', '.', '_')).rstrip()

def create_service_pattern_map(calendar_df):
    """
    Analyzes calendar_df to create a map from service_id to a readable and
    distinct pattern name, using heuristics for holiday services.
    """
    service_id_to_pattern = {}
    
    patterns = {
        "weekday": (calendar_df['monday'] == 1) & (calendar_df['tuesday'] == 1) & 
                   (calendar_df['wednesday'] == 1) & (calendar_df['thursday'] == 1) & 
                   (calendar_df['friday'] == 1) & (calendar_df['saturday'] == 0) & 
                   (calendar_df['sunday'] == 0),
        "saturday": (calendar_df['saturday'] == 1) & (calendar_df['sunday'] == 0),
        "sunday": (calendar_df['sunday'] == 1) & (calendar_df['saturday'] == 0),
    }

    processed_ids = set()

    for pattern_name, pattern_filter in patterns.items():
        service_ids = sorted(list(calendar_df[pattern_filter]['service_id'].unique()))
        
        # Heuristic for "weekday" vs "weekday_holiday"
        if pattern_name == "weekday" and len(service_ids) > 1:
            holiday_ids = [sid for sid in service_ids if "AV" in sid]
            regular_ids = [sid for sid in service_ids if "AV" not in sid]

            # If the heuristic successfully separates the IDs, use semantic names
            if holiday_ids and regular_ids:
                for sid in holiday_ids:
                    service_id_to_pattern[sid] = "weekday_holiday"
                for sid in regular_ids:
                    service_id_to_pattern[sid] = "weekday"
                processed_ids.update(service_ids)
                continue # Skip to next pattern

        # Fallback logic for all other cases (including saturday, sunday, and weekday if heuristic fails)
        if len(service_ids) == 1 and service_ids:
            service_id_to_pattern[service_ids[0]] = pattern_name
        else:
            for i, service_id in enumerate(service_ids):
                service_id_to_pattern[service_id] = f"{pattern_name}_{i+1}"
        processed_ids.update(service_ids)

    # Handle 'other' services
    other_service_ids = set(calendar_df['service_id'].unique()) - processed_ids
    for i, service_id in enumerate(sorted(list(other_service_ids))):
        service_id_to_pattern[service_id] = f"other_{i+1}"
        
    return service_id_to_pattern

def process_gtfs_schedules(zip_path, output_dir):
    if os.path.exists(output_dir):
        print(f"Output directory '{output_dir}' already exists. Cleaning up...")
        shutil.rmtree(output_dir)
    os.makedirs(output_dir)
    print(f"Output directory '{output_dir}' created.")

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

        print("Analyzing service types (weekday, holidays, etc.)...")
        service_id_to_pattern = create_service_pattern_map(calendar_df)

        print("Processing stop hierarchy (parent_station)...")
        stop_id_to_name = stops_df.set_index('stop_id')['stop_name'].to_dict()
        child_to_parent = stops_df.dropna(subset=['parent_station']).set_index('stop_id')['parent_station'].to_dict()

        print("Processing data and joining tables...")
        merged_df = pd.merge(stop_times_df, trips_df, on='trip_id')
        merged_df = pd.merge(merged_df, routes_df, on='route_id')
        
        merged_df['station_id'] = merged_df['stop_id'].map(child_to_parent).fillna(merged_df['stop_id'])
        merged_df['station_name'] = merged_df['station_id'].map(stop_id_to_name)
        merged_df['service_pattern'] = merged_df['service_id'].map(service_id_to_pattern)

        schedule_data = merged_df.dropna(subset=['station_name', 'arrival_time', 'service_pattern', 'direction_id'])
        schedule_data = schedule_data[schedule_data['station_name'].str.strip() != '']
        schedule_data = schedule_data[schedule_data['arrival_time'].str.match(r'^\d+:\d{2}:\d{2}$', na=False)]
        schedule_data['direction_id'] = schedule_data['direction_id'].astype(int)

        print("Creating schedule files by service type and direction...")
        for (route_name, station_name), station_group in schedule_data.groupby(['route_short_name', 'station_name']):
            
            sanitized_route_name = sanitize_filename(str(route_name))
            sanitized_station_name = sanitize_filename(station_name)
            
            station_dir = os.path.join(output_dir, sanitized_route_name, sanitized_station_name)
            os.makedirs(station_dir, exist_ok=True)

            for (service_pattern, direction_id), final_group in station_group.groupby(['service_pattern', 'direction_id']):
                file_name = f"{service_pattern}_direction_{direction_id}.txt"
                file_path = os.path.join(station_dir, file_name)
                
                sorted_times = final_group['arrival_time'].apply(lambda x: x[:-3]).sort_values().unique()
                
                with open(file_path, 'w') as f:
                    f.write('\n'.join(sorted_times))
    
    print(f"Done! Schedule files have been generated in '{output_dir}'.")

def main():
    parser = argparse.ArgumentParser(description="Process a GTFS ZIP file to create schedule files per stop and per line.")
    parser.add_argument("zip_file", help="Path to the GTFS ZIP file.")
    parser.add_argument("--output", default="gtfs_processed", help="Output directory for the generated files (default: 'gtfs_processed').")
    
    args = parser.parse_args()
    
    if not os.path.exists(args.zip_file):
        print(f"Error: File '{args.zip_file}' not found.")
        return
        
    process_gtfs_schedules(args.zip_file, args.output)

if __name__ == "__main__":
    main()