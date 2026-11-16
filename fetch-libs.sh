#!/usr/bin/env bash
# Downloads the third-party mod jars this project compiles against into ./libs.
#
# They are not committed: they are large, they are other people's builds, and
# they carry their own licenses. Run this once after cloning.
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p libs

CREATEMOD="https://maven.createmod.net"
MODRINTH="https://api.modrinth.com/v2"

fetch() {
    local url="$1" out="libs/$(basename "$1")"
    if [[ -s "$out" ]]; then
        echo "have  $(basename "$out")"
        return
    fi
    echo "fetch $(basename "$out")"
    curl -fsSL --retry 3 --retry-delay 2 -o "$out" "$url"
}

# Create 1.21.1. Ships Flywheel, Ponder and Registrate nested under META-INF/jarjar.
fetch "$CREATEMOD/com/simibubi/create/create-1.21.1/6.0.10-276/create-1.21.1-6.0.10-276.jar"

# Create: Electro Energetics owns the wire classes this mod hooks.
# It is published on Modrinth, not on the Create maven.
EE_URL="$(
    curl -fsSL "$MODRINTH/project/electroenergetics/version" \
    | python3 -c 'import json,sys
target = "1.21.1-1.1.4"
for release in json.load(sys.stdin):
    if release["version_number"] == target:
        print(release["files"][0]["url"])
        break'
)"
if [[ -z "$EE_URL" ]]; then
    echo "Could not resolve Create: Electro Energetics 1.21.1-1.1.4." >&2
    echo "Download it from Modrinth into libs/ and re-run." >&2
    exit 1
fi
fetch "$EE_URL"

# Sable and Create: Aeronautics are optional at runtime but needed to compile
# against their APIs. Both are published on Modrinth. A missing one is not fatal:
# the optional integration simply compiles out of the build.
modrinth_jar() {
    local project="$1" target="$2"
    local url
    url="$(
        curl -fsSL "$MODRINTH/project/$project/version" \
        | python3 -c 'import json,sys
target = sys.argv[1]
for release in json.load(sys.stdin):
    if release["version_number"] == target:
        print(release["files"][0]["url"])
        break' "$target"
    )"
    if [[ -z "$url" ]]; then
        echo "skip  $project $target (not found)"
        return 0
    fi
    fetch "$url"
}

modrinth_jar sable "sable-neoforge-1.21.1-2.0.6"
modrinth_jar create-aeronautics "create-aeronautics-bundled-1.21.1-1.3.2"

# Both jars bundle the libraries that are also compile dependencies.
echo "extracting bundled jars"
python3 - <<'PY'
import os
import zipfile

WANTED = {
    "libs/create-1.21.1-6.0.10-276.jar": [
        # nested entry -> file name in libs/
        ("META-INF/jarjar/flywheel-neoforge-1.21.1-1.0.6.jar",
         "flywheel-neoforge-1.21.1-1.0.6.jar"),
        ("META-INF/jarjar/ponder-neoforge-1.0.82+mc1.21.1.jar",
         "ponder-neoforge-1.0.82+mc1.21.1.jar"),
        ("META-INF/jarjar/Registrate-MC1.21-1.3.0+67.jar",
         "Registrate-MC1.21-1.3.0+67.jar"),
    ],
    "libs/electroenergetics-1.21.1-1.1.4.jar": [
        ("META-INF/jarjar/sable-companion-common-1.21.1-1.6.0.jar",
         "sable-companion-common-1.21.1-1.6.0.jar"),
    ],
}

for jar, entries in WANTED.items():
    if not os.path.exists(jar):
        print(f"skip  {os.path.basename(jar)} (missing)")
        continue
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        for entry, target in entries:
            out = os.path.join("libs", target)
            if os.path.exists(out) and os.path.getsize(out) > 0:
                print(f"have  {target}")
                continue
            if entry not in names:
                print(f"skip  {target} (not bundled)")
                continue
            with archive.open(entry) as source, open(out, "wb") as dest:
                dest.write(source.read())
            print(f"wrote {target}")
PY

echo
echo "libs/ is ready:"
ls -1 libs
