#!/usr/bin/env python3
"""
Digimon battle relay server (TCP, line-delimited JSON).

Protocol:
- Client sends join:
  {"op":"join","room":"room1","role":"host|join","name":"device-name","proto":"digimon-battle-v1"}
- Server replies:
  {"op":"joined","room":"room1","role":"host"}
- When both host and join are present:
  {"op":"ready","peer":"other-device-name"}
- Client payload forwarding:
  {"op":"msg","type":"pin_edge","body":"P2,15,1,1234","timestampMs":123456}
- Forwarded to peer as the same "op":"msg" envelope.
- Keepalive:
  {"op":"ping"} -> {"op":"pong"}
"""

from __future__ import annotations

import argparse
import asyncio
import json
import signal
import time
from dataclasses import dataclass
from typing import Optional


MAX_LINE_BYTES = 128 * 1024


@dataclass
class Client:
    reader: asyncio.StreamReader
    writer: asyncio.StreamWriter
    addr: str
    room: Optional[str] = None
    role: Optional[str] = None
    name: str = "peer"


@dataclass
class RoomState:
    host: Optional[Client] = None
    join: Optional[Client] = None

    def peer_for(self, client: Client) -> Optional[Client]:
        if client.role == "host":
            return self.join
        if client.role == "join":
            return self.host
        return None


rooms: dict[str, RoomState] = {}
rooms_lock = asyncio.Lock()


def now_ms() -> int:
    return int(time.time() * 1000)


async def send_json(client: Client, obj: dict) -> bool:
    try:
        payload = json.dumps(obj, separators=(",", ":"))
        client.writer.write(payload.encode("utf-8") + b"\n")
        await client.writer.drain()
        return True
    except Exception:
        return False


async def notify_ready(room: RoomState) -> None:
    if room.host is None or room.join is None:
        return
    await send_json(room.host, {"op": "ready", "peer": room.join.name, "timestampMs": now_ms()})
    await send_json(room.join, {"op": "ready", "peer": room.host.name, "timestampMs": now_ms()})


async def notify_peer_left(peer: Client, reason: str) -> None:
    await send_json(peer, {"op": "peer_left", "reason": reason, "timestampMs": now_ms()})


async def join_room(client: Client, room_name: str, role: str, name: str) -> None:
    async with rooms_lock:
        room = rooms.get(room_name)
        if room is None:
            room = RoomState()
            rooms[room_name] = room

        if role == "host" and room.host is not None and room.host is not client:
            await send_json(client, {"op": "error", "message": "host slot already occupied"})
            return
        if role == "join" and room.join is not None and room.join is not client:
            await send_json(client, {"op": "error", "message": "join slot already occupied"})
            return

        # If client was in another room, remove first.
        await remove_client_locked(client, reason="switched room")

        client.room = room_name
        client.role = role
        client.name = name
        if role == "host":
            room.host = client
        else:
            room.join = client

        await send_json(client, {"op": "joined", "room": room_name, "role": role, "timestampMs": now_ms()})
        await notify_ready(room)


async def remove_client_locked(client: Client, reason: str) -> None:
    room_name = client.room
    role = client.role
    if room_name is None or role is None:
        return
    room = rooms.get(room_name)
    if room is None:
        client.room = None
        client.role = None
        return

    peer: Optional[Client] = None
    if role == "host" and room.host is client:
        room.host = None
        peer = room.join
    elif role == "join" and room.join is client:
        room.join = None
        peer = room.host

    client.room = None
    client.role = None
    if room.host is None and room.join is None:
        rooms.pop(room_name, None)

    if peer is not None:
        await notify_peer_left(peer, reason=reason)


async def remove_client(client: Client, reason: str) -> None:
    async with rooms_lock:
        await remove_client_locked(client, reason)


async def forward_to_peer(client: Client, obj: dict) -> None:
    async with rooms_lock:
        room_name = client.room
        role = client.role
        if room_name is None or role is None:
            await send_json(client, {"op": "error", "message": "join room first"})
            return
        room = rooms.get(room_name)
        if room is None:
            await send_json(client, {"op": "error", "message": "room missing"})
            return
        peer = room.peer_for(client)
        if peer is None:
            await send_json(client, {"op": "error", "message": "peer not connected"})
            return
        out = {
            "op": "msg",
            "type": obj.get("type"),
            "body": obj.get("body"),
            "timestampMs": obj.get("timestampMs", now_ms()),
        }
        await send_json(peer, out)


async def handle_client(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    peer_info = writer.get_extra_info("peername")
    addr = str(peer_info) if peer_info is not None else "unknown"
    client = Client(reader=reader, writer=writer, addr=addr, name=f"peer-{addr}")
    print(f"[connect] {addr}")
    try:
        while True:
            raw = await reader.readline()
            print(f"[debug {addr}] raw line: {raw!r}")
            if not raw:
                break
            if len(raw) > MAX_LINE_BYTES:
                await send_json(client, {"op": "error", "message": "line too large"})
                break
            try:
                msg = json.loads(raw.decode("utf-8", errors="ignore"))
            except json.JSONDecodeError as e:
                print(f"[error {addr}] json parse fail: {e!r} on {raw!r}")
                await send_json(client, {"op": "error", "message": "invalid json"})
                continue
            if not isinstance(msg, dict):
                await send_json(client, {"op": "error", "message": "invalid payload"})
                continue

            op = msg.get("op")
            if op == "join":
                room = str(msg.get("room") or "default").strip() or "default"
                role = str(msg.get("role") or "").strip().lower()
                name = str(msg.get("name") or f"peer-{addr}").strip() or f"peer-{addr}"
                if role not in ("host", "join"):
                    await send_json(client, {"op": "error", "message": "role must be host or join"})
                    continue
                await join_room(client, room_name=room, role=role, name=name)
                continue
            if op == "ping":
                await send_json(client, {"op": "pong", "timestampMs": now_ms()})
                continue
            if op == "pong":
                continue
            if op == "msg":
                await forward_to_peer(client, msg)
                continue
            await send_json(client, {"op": "error", "message": f"unknown op: {op}"})
    except Exception as exc:
        print(f"[error] {addr}: {exc}")
    finally:
        await remove_client(client, reason="peer disconnected")
        try:
            writer.close()
            await writer.wait_closed()
        except Exception:
            pass
        print(f"[disconnect] {addr}")


async def run_server(host: str, port: int) -> None:
    server = await asyncio.start_server(handle_client, host=host, port=port)
    addrs = ", ".join(str(s.getsockname()) for s in server.sockets or [])
    print(f"[start] battle relay listening on {addrs}")
    stop_event = asyncio.Event()

    def _stop() -> None:
        stop_event.set()

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, _stop)
        except NotImplementedError:
            pass

    async with server:
        await stop_event.wait()
    print("[stop] relay shutting down")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Digimon battle relay server")
    parser.add_argument("--host", default="0.0.0.0", help="Bind address (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=19792, help="TCP port (default: 19792)")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    asyncio.run(run_server(args.host, args.port))


if __name__ == "__main__":
    main()
