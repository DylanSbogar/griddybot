#!/bin/bash

# Function to check if the process is running.
is_process_running() {
	PROCESS_NAME="node" # Change this to your Node.js process name
	pgrep $PROCESS_NAME >/dev/null 2>&1
}

# Function to start the Node.js application.
start_node_app() {
	echo "Starting GriddyBot..."
	# Change the directory to your Node.js project directory.
	cd /home/dylan-sbogar/Desktop/repos/griddybot
	# Start your Node.js application.
	node .
}

# Check if the process is already running.
if is_process_running; then
	echo "Node.js application is already running."
else
	echo "Node.js application is not running. Starting..."
	start_node_app
fi

# Monitor the application.
while true; do
	# Sleep for a few seconds before trying again.
	sleep 5
	# Check if the process is still running.
	if ! is_process_running; then
		echo "Node.js application has stopped. Restarting..."
		start_node_app
	fi
done
