#!/usr/bin/env python3
"""Create the benchmark's level.dat from an original deterministic recipe."""
import gzip
import struct
import sys
from pathlib import Path

def string(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded

def compound(fields):
    return b"".join(bytes([kind]) + string(name) + value for kind, name, value in fields) + b"\0"

def number(kind, name, value):
    return kind, name, struct.pack({1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f"}[kind], value)

def text(name, value):
    return 8, name, string(value)

def vector(name, kind, values):
    return 9, name, bytes([kind]) + struct.pack(">i", len(values)) + struct.pack(">" + {5: "f", 6: "d"}[kind] * len(values), *values)

def mapping(name, fields):
    return 10, name, compound(fields)

rules = [(name, "false") for name in (
    "doDaylightCycle", "doMobSpawning", "doFireTick", "doMobLoot", "mobGriefing"
)] + [("randomTickSpeed", "0")]
player = [
    vector("Pos", 6, [8.5, 100.0, 8.5]),
    vector("Motion", 6, [0, 0, 0]),
    vector("Rotation", 5, [0, 24]),
    number(3, "Dimension", 0),
    number(3, "playerGameType", 1),
    number(5, "Health", 20),
    number(2, "Air", 300),
    number(3, "foodLevel", 20),
    number(2, "Fire", -20),
    mapping("abilities", [number(1, flag, 1) for flag in ("flying", "mayfly", "instabuild", "invulnerable", "mayBuild")]
            + [number(5, "flySpeed", 0.05), number(5, "walkSpeed", 0.1)]),
]
data = [
    number(4, "RandomSeed", 8675309),
    text("generatorName", "default"),
    number(3, "generatorVersion", 1),
    text("generatorOptions", ""),
    number(3, "GameType", 1),
    number(3, "SpawnX", 8), number(3, "SpawnY", 100), number(3, "SpawnZ", 8),
    number(4, "Time", 6000), number(4, "DayTime", 6000),
    number(4, "LastPlayed", 0), number(4, "SizeOnDisk", 0),
    text("LevelName", "Brick Benchmark v1"), number(3, "version", 19133),
    number(1, "MapFeatures", 1), number(1, "initialized", 1),
    number(1, "hardcore", 0), number(1, "allowCommands", 0),
    number(1, "Difficulty", 0), number(1, "DifficultyLocked", 1),
    number(1, "raining", 0), number(1, "thundering", 0),
    number(3, "rainTime", 999999), number(3, "thunderTime", 999999),
    mapping("GameRules", [text(name, value) for name, value in rules]),
    mapping("Player", player),
]
Path(sys.argv[1]).write_bytes(gzip.compress(b"\x0a\x00\x00" + compound([mapping("Data", data)]), mtime=0))
