#!/usr/bin/env bash
# Build the image for linux/amd64, ship it over SSH (no registry), (re)start compose.
# Usage: ./deploy.sh <ssh-host> [remote-dir]
set -euo pipefail

HOST="${1:?usage: ./deploy.sh <ssh-host> [remote-dir]}"
REMOTE_DIR="${2:-~/bike-tracker-web}"
IMAGE=bike-tracker-web:latest

echo "==> building $IMAGE (linux/amd64)"
docker buildx build --platform linux/amd64 -t "$IMAGE" --load .

echo "==> shipping image to $HOST"
docker save "$IMAGE" | gzip | ssh "$HOST" 'gunzip | docker load'

echo "==> syncing compose file"
ssh "$HOST" "mkdir -p $REMOTE_DIR"
scp docker-compose.yml "$HOST:$REMOTE_DIR/"

echo "==> starting"
ssh "$HOST" "cd $REMOTE_DIR && \
  if [ ! -f .env ]; then \
    echo '!! $REMOTE_DIR/.env is missing on the server — create it (see .env.example), then rerun: docker compose up -d'; exit 1; \
  fi && \
  docker compose up -d && docker compose ps"

echo "==> done"
