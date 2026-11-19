#!/usr/bin/env python3
import hashlib
import io
import json
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

OPTIFINE = "OptiFine_1.8.9_HD_U_M5.jar"
OPTIFINE_SHA256 = "2739760a70fca6ae5c7dc817bcc6283f18f55e38fa23778da7f387a6b724b2c4"
OPTIFINE_VERSION = "1.8.9_HD_U_M5"
LAUNCHWRAPPER_VERSION = "2.2"
RESOURCES = Path("/usr/share/brick")
RESULTS = Path("/var/lib/brick/data/results")

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".partial")
    temporary.write_bytes(data.encode() if isinstance(data, str) else data)
    temporary.replace(path)

def initial(path, data):
    if not path.exists():
        write(path, data)

def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(request, timeout=25) as result:
        return result.read(16 * 1024 * 1024)

def install_optifine(instance):
    libs = instance / "libraries" / "optifine"
    optifine = libs / "OptiFine" / OPTIFINE_VERSION / f"OptiFine-{OPTIFINE_VERSION}.jar"
    wrapper = libs / "launchwrapper-of" / LAUNCHWRAPPER_VERSION / f"launchwrapper-of-{LAUNCHWRAPPER_VERSION}.jar"
    if optifine.exists() and wrapper.exists():
        return
    print("Downloading OptiFine HD U M5 from optifine.net…", flush=True)
    page = fetch(f"https://optifine.net/adloadx?f={OPTIFINE}").decode()
    href = re.search(r"""href=['"](downloadx\?f=""" + re.escape(OPTIFINE) + r"""&(?:amp;)?x=[0-9a-f]+)['"]""", page)
    if not href:
        raise RuntimeError("OptiFine's official download page did not provide a matching link.")
    data = fetch("https://optifine.net/" + href.group(1).replace("&amp;", "&"))
    if hashlib.sha256(data).hexdigest() != OPTIFINE_SHA256:
        raise RuntimeError("OptiFine download did not match the released M5 fingerprint.")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        write(wrapper, archive.read(wrapper.name))
    write(optifine, data)

def configure(profile, mode):
    initial(profile / "prismlauncher.cfg", """[General]
Language=en_US
JavaPath=/opt/java/bin/java
AutomaticJavaSwitch=false
AutomaticJavaDownload=false
UserAskedAboutAutomaticJavaDownload=true
IgnoreJavaWizard=true
MinMemAlloc=384
MaxMemAlloc=1024
LaunchMaximized=true
QuitAfterGameStop=true
ShowConsole=false
ShowConsoleOnError=true
UpdatesAllowed=false
""")
    if mode == "--setup":
        return
    instance = profile / "instances" / ("brick-" + mode)
    instance.mkdir(parents=True, exist_ok=True)
    install_optifine(instance)
    title = "Minecraft 1.8.9" if mode == "play" else "Minecraft 1.8.9 Benchmark"
    config = f"[General]\nInstanceType=OneSix\nname={title}\niconKey=default\n"
    if mode == "benchmark":
        config += f"OverrideJavaArgs=true\nJvmArgs=-javaagent:{RESOURCES}/brick-bench.jar={RESULTS}\n"
    initial(instance / "instance.cfg", config)
    component = {
        "formatVersion": 1, "uid": "local.brick.optifine", "name": "OptiFine HD U M5",
        "version": OPTIFINE_VERSION, "order": 10,
        "requires": [{"uid": "net.minecraft", "equals": "1.8.9"}],
        "mainClass": "net.minecraft.launchwrapper.Launch",
        "+tweakers": ["optifine.OptiFineTweaker"],
        "+libraries": [
            {"name": f"optifine:OptiFine:{OPTIFINE_VERSION}", "MMC-hint": "local"},
            {"name": f"optifine:launchwrapper-of:{LAUNCHWRAPPER_VERSION}", "MMC-hint": "local"},
        ],
    }
    write(instance / "patches" / "local.brick.optifine.json", json.dumps(component))
    initial(instance / "mmc-pack.json", json.dumps({"formatVersion": 1, "components": [
        {"uid": "net.minecraft", "version": "1.8.9", "important": True},
        {"uid": "local.brick.optifine", "version": OPTIFINE_VERSION, "important": True},
    ]}))
    for name in ("options.txt", "optionsof.txt"):
        path = instance / ".minecraft" / name
        if mode == "benchmark" or not path.exists():
            write(path, (RESOURCES / name).read_bytes())

def has_account(profile):
    try:
        accounts = json.loads((profile / "accounts.json").read_text())["accounts"]
        eligible = [a for a in accounts if a.get("type") == "MSA"]
        return len(eligible) == len(accounts) and any(
            a.get("entitlement", {}).get("canPlayMinecraft") for a in eligible
        )
    except (OSError, ValueError, KeyError, TypeError):
        return False

def main():
    if sys.argv[1] == "--has-account":
        sys.exit(0 if has_account(Path(sys.argv[2])) else 1)
    if sys.argv[1] == "--setup":
        configure(Path(sys.argv[2]), "--setup")
        return
    profile = Path(sys.argv[1])
    if not has_account(profile):
        sys.exit("Sign in with an eligible Microsoft account via option 2 first. Remove any offline account.")
    try:
        configure(profile, sys.argv[2])
    except (OSError, RuntimeError, zipfile.BadZipFile, KeyError) as error:
        sys.exit(f"Could not prepare Minecraft: {error}")

if __name__ == "__main__":
    main()
