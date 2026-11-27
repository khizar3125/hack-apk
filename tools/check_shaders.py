import re, os, sys, subprocess, urllib.request, zipfile, shutil

SHADER_FILE = "app/src/main/java/com/paradox/game/engine/GlGameRenderer.kt"
GLSLANG_URL = "https://github.com/KhronosGroup/glslang/releases/download/16.6.0/glslang-16.6.0-linux-x86_64-release.zip"
BIN = "/tmp/glslang-bin/bin/glslang"


def ensure_glslang():
    if os.path.exists(BIN) and os.access(BIN, os.X_OK):
        return
    zpath = "/tmp/glslang.zip"
    if not os.path.exists(zpath):
        urllib.request.urlretrieve(GLSLANG_URL, zpath)
    zipfile.ZipFile(zpath).extractall("/tmp/glslang-bin")
    os.chmod(BIN, 0o755)


def extract_shaders():
    src = open(SHADER_FILE).read()
    out = []
    for name, body in re.findall(r'private val (\w+) = """(.*?)"""', src, re.S):
        kind = "frag" if "_FS" in name else "vert"
        out.append((name, body.strip("\n"), kind))
    return out


def main():
    ensure_glslang()
    os.makedirs("/tmp/shaders", exist_ok=True)
    failed = 0
    for name, body, kind in extract_shaders():
        path = f"/tmp/shaders/{name}.{kind}"
        with open(path, "w") as f:
            f.write("#version 100\n" + body)
        r = subprocess.run([BIN, path], capture_output=True, text=True)
        ok = r.returncode == 0
        print(f"{name}: {'OK' if ok else 'FAIL'}")
        if not ok:
            print(r.stdout)
            print(r.stderr)
            failed += 1
    if failed:
        print(f"{failed} shader(s) FAILED ES 1.00 validation")
        sys.exit(1)
    print("All shaders valid GLSL ES 1.00")


if __name__ == "__main__":
    main()
