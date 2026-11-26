import zlib, struct, math, os

def write_png(path, w, h, pix):
    raw = b''.join(b'\x00' + bytes(pix[y*w*4:(y+1)*w*4]) for y in range(h))
    def chunk(t, d):
        c = t + d
        return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f: f.write(png)

def render(size, rounded=True):
    W = H = size
    px = bytearray(W*H*4)
    s = size / 512.0
    cx, cy = W/2, H/2 + 14*s
    R = 118*s

    def setp(x, y, r, g, b, a):
        if 0 <= x < W and 0 <= y < H:
            i = (y*W+x)*4
            ia = px[i+3]/255.0
            na = a + ia*(1-a)
            if na > 0:
                px[i]   = int((r*a + px[i]*ia*(1-a))/na)
                px[i+1] = int((g*a + px[i+1]*ia*(1-a))/na)
                px[i+2] = int((b*a + px[i+2]*ia*(1-a))/na)
                px[i+3] = int(na*255)

    for y in range(H):
        for x in range(W):
            bg = (5, 7, 13, 255)
            base = bg
            if rounded:
                bx, by = (x - W/2)/ (W/2-14*s), (y - H/2)/(H/2-14*s)
                d = (bx*bx+by*by)**0.5
                if d > 1.18: base = (0,0,0,0)
                elif d > 1.0:
                    t = 1.0 - (d-1.0)/0.18
                    r0,g0,b0 = base[:3]
                    base = (int(r0), int(g0), int(b0), int(255*t))
            i = (y*W+x)*4
            px[i],px[i+1],px[i+2],px[i+3] = base

    # Penrose triangle: three bars, impossible triangle vertices
    def vsub(a,b): return (a[0]-b[0], a[1]-b[1])
    tri = [(cx, cy-R), (cx-104*s, cy+74*s), (cx+104*s, cy+74*s)]
    bw = 30*s
    def draw_bar(p1, p2, color, width, alpha=1.0, glow=0):
        vx, vy = vsub(p2, p1)
        L = math.hypot(vx, vy) or 1
        vx, vy = vx/L, vy/L
        nx, ny = -vy, vx
        hw = width/2
        for y in range(H):
            for x in range(W):
                px_, py_ = x-p1[0], y-p1[1]
                t = px_*vx + py_*vy
                if -glow <= t <= L+glow:
                    d = abs(px_*nx + py_*ny)
                    if d <= hw + glow:
                        core = max(0.0, 1.0 - max(0.0, d-hw)/max(glow,0.001)) if d > hw else 1.0
                        a = alpha * core
                        if glow > 0 and d > hw:
                            a *= 0.16
                        r,g,b = color
                        setp(x, y, r, g, b, a)
    draw_bar(tri[0], tri[1], (0,230,255), bw, 1.0)          # cyan left bar
    draw_bar(tri[1], tri[2], (190,80,255), bw, 1.0)          # violet bottom bar
    draw_bar(tri[2], tri[0], (235,240,255), bw, 1.0)         # white right bar
    for (c1, c2, col) in ((tri[0], tri[1], (0,230,255)), (tri[1], tri[2], (190,80,255)), (tri[2], tri[0], (235,240,255))):
        draw_bar(c1, c2, col, bw*2.6, 0.10, 0)               # glow pass
    # center dot
    for y in range(H):
        for x in range(W):
            d = math.hypot(x-cx, y-(cy-8*s))
            if d < 16*s:
                setp(x, y, 255,255,255, 0.9*(1-d/(16*s)))
    return px

root = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')
sizes = {'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}
print('rendering 512 master...')
master = render(512)
for d, s in sizes.items():
    k = s/512
    # simple nearest scale (master rendered big)
    for name in ('ic_launcher', 'ic_launcher_round'):
        w = h = s
        out = bytearray(w*h*4)
        for y in range(h):
            src = int(min(511, y*512/h))
            for x in range(w):
                sx = int(min(511, x*512/w))
                i = (src*512+sx)*4
                j = (y*w+x)*4
                out[j:j+4] = master[i:i+4]
        write_png(os.path.join(root, f'mipmap-{d}', name+'.png'), w, h, out)
    print('done', d)
# adaptive icon foreground (transparent bg, glyph bigger)
for d, s in sizes.items():
    out = bytearray(108*108*4*36)  # placeholder handled below
fsize = 432
mask = render(fsize, rounded=False)
# blank background out and keep glyph: rebuild simply with transparent corners off-screen
for name, path in (('ic_launcher_foreground', 'drawable-anydpi'), ):
    pass
write_png(os.path.join(root, 'mipmap-xxxhdpi', 'ic_launcher_foreground.png'), 432, 432, mask)
for d, s in sizes.items():
    k = s/108*1.0
    out = bytearray(int(s)*int(s)*4)
    for y in range(int(s)):
        src = int(min(431, y*432/int(s)))
        for x in range(int(s)):
            sx = int(min(431, x*432/int(s)))
            i = (src*432+sx)*4
            j = (y*int(s)+x)*4
            out[j:j+4] = mask[i:i+4]
    write_png(os.path.join(root, f'mipmap-{d}', 'ic_launcher_foreground.png'), int(s), int(s), list(out))
print('all icons done')
