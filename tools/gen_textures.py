import zlib, struct, math, os, random

def write_png(path, w, h, rgb):
    raw = b''.join(b'\x00' + bytes(rgb[y*w*3:(y+1)*w*3]) for y in range(h))
    def chunk(t, d):
        c = t + d
        return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)

class Buf:
    def __init__(self, size):
        self.s = size
        self.height = [0.0] * (size * size)
        self.albedo = [(0.0, 0.0, 0.0)] * (size * size)
    def hget(self, x, y):
        return self.height[((y % self.s) * self.s + (x % self.s))]
    def hset(self, x, y, v):
        self.height[((y % self.s) * self.s + (x % self.s))] = v
    def aset(self, x, y, c):
        self.albedo[y * self.s + x] = c

def sstep(a, b, t):
    t = max(0.0, min(1.0, (t - a) / (b - a)))
    return t * t * (3 - 2 * t)

def box_h(buf, x0, y0, x1, y1, h, feather):
    for y in range(max(0, y0 - feather), min(buf.s, y1 + feather)):
        for x in range(max(0, x0 - feather), min(buf.s, x1 + feather)):
            dx = min(x - x0, x1 - x) / feather
            dy = min(y - y0, y1 - y) / feather
            f = sstep(0.0, 1.0, min(dx, dy, 1.0))
            buf.hset(x, y, buf.hget(x, y) + h * f)

def rivet(buf, cx, cy, r, h):
    s = buf.s
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                buf.hset(x, y, buf.hget(x, y) + h * math.sqrt(max(0.0, 1.0 - (d / r) ** 2)))

def groove_rect(buf, m, w, depth):
    s = buf.s
    for y in range(s):
        for x in range(s):
            dx = min(x, s - 1 - x)
            dy = min(y, s - 1 - y)
            d = min(dx, dy)
            if m - w <= d <= m + w:
                t = 1.0 - abs(d - m) / w
                buf.hset(x, y, buf.hget(x, y) - depth * t)

def brushed(buf, amount, axis, seed):
    rnd = random.Random(seed)
    s = buf.s
    noise = [rnd.uniform(-1, 1) for _ in range(s)]
    for y in range(s):
        for x in range(s):
            if axis == 0:
                n = noise[y] * 0.6 + noise[(x * 7 + y) % s] * 0.4
            else:
                n = noise[x] * 0.6 + noise[(y * 7 + x) % s] * 0.4
            buf.hset(x, y, buf.hget(x, y) + n * amount)

def scratches(buf, n, seed, depth):
    rnd = random.Random(seed)
    s = buf.s
    for _ in range(n):
        x = rnd.uniform(0, s); y = rnd.uniform(0, s)
        ang = rnd.uniform(0, math.tau)
        L = rnd.uniform(30, 160)
        dx, dy = math.cos(ang), math.sin(ang)
        w = rnd.uniform(0.5, 1.6)
        for t in range(int(L)):
            px, py = x + dx * t, y + dy * t
            ix, iy = int(px) % s, int(py) % s
            fall = 1.0 - t / L
            for oy in (-1, 0, 1):
                for ox in (-1, 0, 1):
                    dd = math.hypot(ox, oy) / max(w, 0.4)
                    if dd <= 1:
                        buf.hset(ix + ox, iy + oy, buf.hget(ix + ox, iy + oy) - depth * fall * (1 - dd))

def grime(buf, seed, n, strength):
    rnd = random.Random(seed)
    s = buf.s
    for _ in range(n):
        cx, cy = rnd.uniform(0, s), rnd.uniform(0, s)
        r = rnd.uniform(20, 90)
        val = rnd.uniform(-strength, strength * 0.6)
        for y in range(int(cy - r), int(cy + r), 2):
            for x in range(int(cx - r), int(cx + r), 2):
                d = math.hypot(x - cx, y - cy)
                if d < r:
                    buf.hset(x % s, y % s, buf.hget(x % s, y % s) + val * (1 - d / r))

def height_to_normal(buf, strength):
    s = buf.s
    nrm = [(128, 128, 255)] * (s * s)
    for y in range(s):
        for x in range(s):
            hl = buf.hget(x - 1, y); hr = buf.hget(x + 1, y)
            hu = buf.hget(x, y - 1); hd = buf.hget(x, y + 1)
            dx = (hl - hr) * strength
            dy = (hu - hd) * strength
            nx, ny, nz = dx, dy, 1.0
            L = math.sqrt(nx * nx + ny * ny + nz * nz)
            nrm[y * s + x] = (int((nx / L * 0.5 + 0.5) * 255), int((ny / L * 0.5 + 0.5) * 255), int((nz / L * 0.5 + 0.5) * 255))
    return nrm

def albedo_from(buf, base, shade=0.55, warm=(1.0, 0.98, 0.96)):
    s = buf.s
    out = [0] * (s * s * 3)
    hmin = min(buf.height); hmax = max(buf.height)
    span = (hmax - hmin) or 1.0
    for y in range(s):
        for x in range(s):
            h = (buf.hget(x, y) - hmin) / span
            r, g, b = base
            f = 1.0 - shade * (1.0 - h)
            br = 0.9 + 0.2 * h
            out[(y * s + x) * 3] = int(max(0, min(255, r * f * br * warm[0])))
            out[(y * s + x) * 3 + 1] = int(max(0, min(255, g * f * br * warm[1])))
            out[(y * s + x) * 3 + 2] = int(max(0, min(255, b * f * br * warm[2])))
    return out

def make_floor(out_dir, size=512):
    buf = Buf(size)
    brushed(buf, 0.020, 0, 11)
    grime(buf, 5, 10, 0.03)
    scratches(buf, 26, 7, 0.05)
    groove_rect(buf, 26, 9, 0.20)
    box_h(buf, 34, 34, size - 34, size - 34, 0.06, 26)
    inset = 30
    box_h(buf, inset + 40, inset + 40, size - inset - 40, size - inset - 40, -0.04, 30)
    for rx in (52, size - 52):
        for ry in (52, size - 52):
            rivet(buf, rx, ry, 7.5, 0.16)
    rivet(buf, size // 2, size // 2, 11, 0.10)
    d = albedo_from(buf, (44, 52, 68), shade=0.5)
    n = [c for px in height_to_normal(buf, 5.0) for c in px]
    write_png(os.path.join(out_dir, "floor_d.png"), size, size, d)
    write_png(os.path.join(out_dir, "floor_n.png"), size, size, n)

def make_wall(out_dir, size=512):
    buf = Buf(size)
    brushed(buf, 0.018, 1, 23)
    grime(buf, 9, 12, 0.035)
    scratches(buf, 20, 13, 0.04)
    for band in (96, size - 96):
        box_h(buf, 24, band - 7, size - 24, band + 7, -0.16, 8)
    box_h(buf, 40, 40, size - 40, size - 40, 0.05, 34)
    box_h(buf, 120, 170, size - 120, size - 250, -0.10, 22)
    rnd = random.Random(3)
    for i in range(9):
        rx = 60 + i * 49
        rivet(buf, rx, 52, 6, 0.15)
        rivet(buf, rx, size - 52, 6, 0.15)
    d = albedo_from(buf, (52, 58, 78), shade=0.52)
    n = [c for px in height_to_normal(buf, 5.0) for c in px]
    write_png(os.path.join(out_dir, "wall_d.png"), size, size, d)
    write_png(os.path.join(out_dir, "wall_n.png"), size, size, n)

def make_cube(out_dir, size=256):
    buf = Buf(size)
    brushed(buf, 0.02, 0, 31)
    scratches(buf, 14, 17, 0.05)
    groove_rect(buf, 20, 6, 0.18)
    box_h(buf, 26, 26, size - 26, size - 26, 0.05, 20)
    for rx in (34, size - 34):
        for ry in (34, size - 34):
            rivet(buf, rx, ry, 6, 0.14)
    d = albedo_from(buf, (120, 92, 44), shade=0.5)
    n = [c for px in height_to_normal(buf, 5.0) for c in px]
    write_png(os.path.join(out_dir, "cube_d.png"), size, size, d)
    write_png(os.path.join(out_dir, "cube_n.png"), size, size, n)

if __name__ == "__main__":
    out = "app/src/main/assets/tex"
    make_floor(out)
    make_wall(out)
    make_cube(out)
    for f in sorted(os.listdir(out)):
        print(f, os.path.getsize(os.path.join(out, f)))
