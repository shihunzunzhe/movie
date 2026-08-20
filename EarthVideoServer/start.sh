#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
if [ ! -d ".venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv .venv
fi
source .venv/bin/activate
pip install -q fastapi uvicorn pydantic httpx aiomysql 2>/dev/null
LOCAL_IP=$(ifconfig en0 2>/dev/null | grep 'inet ' | head -1 | awk '{print $2}')
[ -z "$LOCAL_IP" ] && LOCAL_IP=$(ifconfig en1 2>/dev/null | grep 'inet ' | head -1 | awk '{print $2}')
[ -z "$LOCAL_IP" ] && LOCAL_IP="192.168.1.36"
echo "Starting EarthVideo API server..."
echo "API will be available at http://$LOCAL_IP:8808"
nohup python -m uvicorn main:app --host 0.0.0.0 --port 8808 --log-level info > /tmp/earthvideo-server.log 2>&1 &
echo $! > /tmp/earthvideo.pid
echo "Waiting for server to start..."
sleep 5
curl -s http://localhost:8808/api/health 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); print(f'Running. Movies: {d.get(\"movies_count\",\"?\")}')" 2>/dev/null || echo "Server may still be starting..."
echo "Logs: tail -f /tmp/earthvideo-server.log"
echo "Stop: kill \$(cat /tmp/earthvideo.pid)"
