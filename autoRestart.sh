#!/bin/bash

# Function to check if the process is running.
is_process_running() {
    # Check for a node process with the specific command "ts-node src/index.ts".
    pgrep -f "ts-node src/index.ts" >/dev/null 2>&1
}

# Function to start the Node.js application.
start_griddybot() {
	echo "Starting GriddyBot..."
	# Change the directory to your Node.js project directory.
	cd /home/dylan-sbogar/Desktop/repos/griddybot
	# Start your Node.js application.
	npm start
}

# Check if the process is already running.
if is_process_running; then
	echo "GriddyBot is already running."
else
	echo "GriddyBot is not running. Starting..."
	start_griddybot
fi

# Monitor the application.
while true; do
	# Sleep for a few seconds before trying again.
	sleep 5
	# Check if the process is still running.
	if ! is_process_running; then
		echo "GriddyBot has stopped. Restarting..."
		start_griddybot
	fi
done
