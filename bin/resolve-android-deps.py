#!/usr/bin/env python3
"""Ovoz Studio type-check uchun minimal Maven resolver.

AndroidX/Compose bog'liqliklarini (AAR + JAR) yuklab oladi va har bir AAR
ichidagi `classes.jar` ni bitta papkaga yoyadi — kotlinc shu papkani
klass yo'li sifatida ishlatadi. Buni `bin/typecheck-android.sh` chaqiradi.

To'liq Maven resolver emas: parent POM, profil va versiya oraliqlari
qo'llab-quvvatlanmaydi. AndroidX "yassilangan" POM chiqaradi — shu yetadi.

DIQQAT: bu yerdagi ro'yxat `app/build.gradle.kts` va
`gradle/libs.versions.toml` bilan QO'LDA sinxronlanadi. Yangi kutubxona
qo'shilsa, shu faylga ham yozing — aks holda type-check uni ko'rmaydi.
"""
import os
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

REPOS = [
    "https://dl.google.com/dl/android/maven2",
    "https://repo1.maven.org/maven2",
]
# Katta fayllar repoga tushmasligi kerak: hammasi vaqtinchalik keshda.
BASE = os.environ.get("OVOZ_TYPECHECK_CACHE", "/tmp/superlisa/ovoz-typecheck")
OUT = os.path.join(BASE, "libs")
CACHE = os.path.join(BASE, "cache")
NS = "{http://maven.apache.org/POM/4.0.0}"

# Direct dependencies of :app (versions resolved through the Compose BOM below).
DIRECT = [
    ("androidx.core", "core-ktx", "1.15.0"),
    ("androidx.lifecycle", "lifecycle-runtime-ktx", "2.8.7"),
    ("androidx.lifecycle", "lifecycle-viewmodel-compose", "2.8.7"),
    ("androidx.activity", "activity-compose", "1.9.3"),
    ("androidx.compose.ui", "ui", None),
    ("androidx.compose.ui", "ui-graphics", None),
    ("androidx.compose.ui", "ui-tooling-preview", None),
    ("androidx.compose.material3", "material3", None),
    ("androidx.compose.material", "material-icons-core", None),
    ("androidx.navigation", "navigation-compose", "2.8.5"),
    ("androidx.annotation", "annotation-jvm", "1.8.0"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.8.1"),
    # MP3 kodlovchisi (LAME'ning sof Java porti). Oddiy JAR, AAR emas.
    ("de.sciss", "jump3r", "1.0.5"),
]
BOM = "https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2024.12.01/compose-bom-2024.12.01.pom"

SKIP_SCOPES = {"test", "provided", "system"}


def fetch(url, binary=False):
    os.makedirs(CACHE, exist_ok=True)
    name = os.path.join(CACHE, re.sub(r"[^A-Za-z0-9._-]", "_", url.split("/maven2/")[-1]))
    if not os.path.exists(name):
        try:
            with urllib.request.urlopen(url, timeout=120) as r, open(name, "wb") as f:
                f.write(r.read())
        except urllib.error.HTTPError:
            return None
        except Exception as e:  # network hiccup — treat as missing
            print(f"  ! {url}: {e}", file=sys.stderr)
            return None
    return open(name, "rb").read() if binary else open(name, "r", encoding="utf-8", errors="replace").read()


def pom_path(group, artifact, version):
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"


def read_pom(group, artifact, version):
    for repo in REPOS:
        text = fetch(f"{repo}/{pom_path(group, artifact, version)}")
        if text:
            return ET.fromstring(text)
    return None


def bom_versions():
    text = fetch(BOM)
    root = ET.fromstring(text)
    managed = {}
    for dep in root.iter(f"{NS}dependency"):
        g = dep.findtext(f"{NS}groupId")
        a = dep.findtext(f"{NS}artifactId")
        v = dep.findtext(f"{NS}version")
        if g and a and v:
            managed[(g, a)] = v
    return managed


def deps_of(root):
    out = []
    deps = root.find(f"{NS}dependencies")
    if deps is None:
        return out
    for dep in deps.findall(f"{NS}dependency"):
        if (dep.findtext(f"{NS}optional") or "").lower() == "true":
            continue
        if (dep.findtext(f"{NS}scope") or "compile") in SKIP_SCOPES:
            continue
        g = dep.findtext(f"{NS}groupId")
        a = dep.findtext(f"{NS}artifactId")
        v = dep.findtext(f"{NS}version")
        if not (g and a and v) or "${" in v:
            continue
        # "[2.8.7]" — Maven "soft requirement" belgisi; oddiy versiyaga keltiramiz.
        v = v.strip("[]()")
        out.append((g, a, v))
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    managed = bom_versions()
    print(f"BOM manages {len(managed)} artifacts")

    seen = {}
    queue = []
    for g, a, v in DIRECT:
        queue.append((g, a, v or managed.get((g, a))))
    while queue:
        g, a, v = queue.pop(0)
        if not v:
            print(f"  ? no version for {g}:{a}", file=sys.stderr)
            continue
        if (g, a) in seen:
            continue
        seen[(g, a)] = v
        root = read_pom(g, a, v)
        if root is None:
            print(f"  ! no pom {g}:{a}:{v}", file=sys.stderr)
            continue
        queue.extend(deps_of(root))

    print(f"resolved {len(seen)} artifacts")
    count = 0
    for (g, a), v in sorted(seen.items()):
        base = f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}"
        data = None
        for repo in REPOS:
            for ext in (".aar", ".jar"):
                data = fetch(f"{repo}/{base}{ext}", binary=True)
                if data:
                    dest = os.path.join(CACHE, a + ext)
                    with open(dest, "wb") as f:
                        f.write(data)
                    if ext == ".aar":
                        with zipfile.ZipFile(dest) as z:
                            if "classes.jar" in z.namelist():
                                with z.open("classes.jar") as src, \
                                        open(os.path.join(OUT, f"{a}-{v}.jar"), "wb") as dst:
                                    dst.write(src.read())
                                count += 1
                    else:
                        with open(os.path.join(OUT, f"{a}-{v}.jar"), "wb") as dst:
                            dst.write(data)
                        count += 1
                    break
            if data:
                break
    print(f"unpacked {count} classpath entries into {OUT}")


if __name__ == "__main__":
    main()
