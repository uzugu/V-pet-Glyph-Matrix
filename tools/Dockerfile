# Use a lightweight official Python image
FROM python:3.11-slim

# Prevent Python from writing .pyc files and buffer stdout
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# Create an app directory
WORKDIR /app

# Copy the relay server script and HTML frontend into the container
COPY battle_relay_server.py frontend.html /app/

# Expose the raw TCP port used by the relay server
EXPOSE 19792

# Run the relay server script when the container launches
# (Binding to 0.0.0.0 ensures it can accept traffic from external networking interfaces)
CMD ["python", "battle_relay_server.py", "--host", "0.0.0.0", "--port", "19792"]
