import zlib, struct, math, os, random

def write_png(path, w, h, pix):
    raw = b''.join(b'\x00' + bytes(pix[y*w*4:(y+1)*w*4]) for y in range(h))
    def chunk(t, d):
        c = t + d
        return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f: f.write(png)

CYAN = (34, 217, 245)
VIOLET = (176, 92, 255)
ICE = (232, 242, 255)

def point_in_poly(p, poly):
    x, y = p; inside = False; j = len(poly)-1
    for i in range(len(poly)):
        xi, yi = poly[i]; xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi:
            inside = not inside
        j = i
    return inside

def signed_area(poly):
    s = 0.0
    for i in range(len(poly)):
        x0, y0 = poly[i]; x1, y1 = poly[(i+1) % len(poly)]
        s += x0*y1 - x1*y0
    return s / 2.0

def bar_faces(hexagon):
    for i in range(3):
        a, b = i, (i + 3) % 6
        mid = ((hexagon[a][0]+hexagon[b][0])/2, (hexagon[a][1]+hexagon[b][1])/2)
        if not point_in_poly(mid, hexagon):
            continue
        fa = [hexagon[a], hexagon[(a+1) % 6], hexagon[(a+2) % 6], hexagon[b]]
        fb = [hexagon[b], hexagon[(b+1) % 6], hexagon[(b+2) % 6], hexagon[a]]
        ca = (sum(p[0] for p in fa)/4, sum(p[1] for p in fa)/4)
        cb = (sum(p[0] for p in fb)/4, sum(p[1] for p in fb)/4)
        sa = -ca[1]*0.8 - ca[0]*0.2
        sb = -cb[1]*0.8 - cb[0]*0.2
        bright, dark = (fa, fb) if sa > sb else (fb, fa)
        return bright, dark
    return None, None

def fill_convex(buf, W, H, poly, col, alpha=1.0):
    n = len(poly)
    ys = [p[1] for p in poly]
    y0 = max(0, int(min(ys))); y1 = min(H-1, int(max(ys))+1)
    for y in range(y0, y1+1):
        yc = y + 0.5
        xs = []
        j = n - 1
        for i in range(n):
            xi, yi = poly[i]; xj, yj = poly[j]
            if (yi > yc) != (yj > yc):
                xs.append((xj - xi) * (yc - yi) / (yj - yi) + xi)
            j = i
        xs.sort()
        for k in range(0, len(xs) - 1, 2):
            xa = max(0, int(math.ceil(xs[k] - 0.5)))
            xb = min(W-1, int(math.floor(xs[k+1] - 0.5)))
            for x in range(xa, xb+1):
                blend(buf, W, x, y, col, alpha)

def blend(buf, W, x, y, col, a):
    i4 = (y*W+x)*4
    ia = buf[i4+3]/255.0
    na = a + ia*(1-a)
    if na > 0:
        for k in range(3):
            buf[i4+k] = int((col[k]*a + buf[i4+k]*ia*(1-a))/na)
        buf[i4+3] = int(na*255)

def stroke_poly(buf, W, H, poly, col, width, alpha=1.0):
    n = len(poly)
    for i in range(n):
        p, q = poly[i], poly[(i+1) % n]
        dx, dy = q[0]-p[0], q[1]-p[1]
        L = math.hypot(dx, dy) or 1.0
        ux, uy = dx/L, dy/L
        x0 = max(0, int(min(p[0], q[0])-width-1)); x1 = min(W-1, int(max(p[0], q[0])+width+1))
        y0 = max(0, int(min(p[1], q[1])-width-1)); y1 = min(H-1, int(max(p[1], q[1])+width+1))
        for y in range(y0, y1+1):
            for x in range(x0, x1+1):
                px_, py_ = x+0.5-p[0], y+0.5-p[1]
                t = px_*ux + py_*uy
                if -0.5 <= t <= L+0.5:
                    d = abs(px_*-uy + py_*ux)
                    if d <= width:
                        a = alpha * (1.0 - d/width)**0.7
                        if a > 0.01:
                            blend(buf, W, x, y, col, a)

def draw_glow(buf, W, H, cx, cy, radius, col, strength):
    x0 = max(0, int(cx-radius)); x1 = min(W-1, int(cx+radius))
    y0 = max(0, int(cy-radius)); y1 = min(H-1, int(cy+radius))
    for y in range(y0, y1+1):
        for x in range(x0, x1+1):
            d = math.hypot(x+0.5-cx, y+0.5-cy)
            if d < radius:
                a = strength * (1.0 - d/radius)**2.4
                if a > 0.003:
                    blend(buf, W, x, y, col, a)

def draw_bg(buf, W, H):
    cxc, cyc = W/2, H/2
    rnd = random.Random(1337)
    stars = [(rnd.uniform(0, W), rnd.uniform(0, H), rnd.uniform(0.15, 0.7)) for _ in range(90)]
    maxd = math.hypot(cxc, cyc)
    for y in range(H):
        for x in range(W):
            d = math.hypot(x+0.5-cxc, y+0.5-cyc) / maxd
            r = 14 + 26 * (1-d)**1.6
            g = 20 + 40 * (1-d)**1.6
            b = 34 + 70 * (1-d)**1.6
            vig = 1.0 - 0.42 * d**2.2
            i4 = (y*W+x)*4
            buf[i4] = int(r*vig); buf[i4+1] = int(g*vig); buf[i4+2] = int(b*vig); buf[i4+3] = 255
    for sx, sy, sa in stars:
        xi, yi = int(sx), int(sy)
        if 0 <= xi < W and 0 <= yi < H:
            i4 = (yi*W+xi)*4
            v = int(255*sa)
            buf[i4] = min(255, buf[i4]+v); buf[i4+1] = min(255, buf[i4+1]+v); buf[i4+2] = min(255, buf[i4+2]+v)
    step = W / 9.0
    gcol = (70, 110, 160)
    for k in range(1, 9):
        x = int(k*step)
        for y in range(H):
            for xx in (x-1, x):
                if 0 <= xx < W:
                    i4 = (y*W+xx)*4
                    f = 0.09
                    buf[i4] = int(buf[i4]*(1-f)+gcol[0]*f); buf[i4+1] = int(buf[i4+1]*(1-f)+gcol[1]*f); buf[i4+2] = int(buf[i4+2]*(1-f)+gcol[2]*f)
        yy = int(k*step)
        for x in range(W):
            for yyy in (yy-1, yy):
                if 0 <= yyy < H:
                    i4 = (yyy*W+x)*4
                    f = 0.09
                    buf[i4] = int(buf[i4]*(1-f)+gcol[0]*f); buf[i4+1] = int(buf[i4+1]*(1-f)+gcol[1]*f); buf[i4+2] = int(buf[i4+2]*(1-f)+gcol[2]*f)

def emblem_polys(cx, cy, R):
    white = [(193,38),(348,318),(178,318),(148,373),(448,373),(263,38)]
    black = [(448,373),(148,373),(233,223),(198,158),(43,438),(418,438)]
    gray  = [(43,438),(198,158),(283,318),(348,318),(193,38),(8,378)]
    raw = [white, black, gray]
    xs = [p[0] for h in raw for p in h]; ys = [p[1] for h in raw for p in h]
    w0, h0 = max(xs)-min(xs), max(ys)-min(ys)
    k = (R*2.0) / h0
    ox = cx - (min(xs)+w0/2)*k
    oy = cy - (min(ys)+h0/2)*k
    return [[(p[0]*k+ox, p[1]*k+oy) for p in h] for h in raw]

def render_emblem(W, H, R, bg=True, ss=3):
    S = ss
    Ws, Hs = W*S, H*S
    cxc, cyc = Ws/2, Hs/2 - Hs*0.012
    Rs = R * S
    buf = bytearray(Ws*Hs*4)
    if bg:
        draw_bg(buf, Ws, Hs)
        draw_glow(buf, Ws, Hs, cxc, cyc + Rs*0.18, Rs*1.55, (30, 150, 200), 0.5)
        draw_glow(buf, Ws, Hs, cxc - Rs*0.3, cyc - Rs*0.25, Rs*0.9, (90, 40, 160), 0.35)
    hexes = emblem_polys(cxc, cyc, Rs)
    cols = [CYAN, VIOLET, ICE]
    for idx in (0, 1, 2):
        bright, dark = bar_faces(hexes[idx])
        bc = tuple(min(255, int(c*1.18)) for c in cols[idx])
        dc = tuple(int(c*0.55) for c in cols[idx])
        fill_convex(buf, Ws, Hs, bright, bc)
        fill_convex(buf, Ws, Hs, dark, dc)
    for idx in (0, 1, 2):
        oc = tuple(min(255, int(c*1.35+30)) for c in cols[idx])
        stroke_poly(buf, Ws, Hs, hexes[idx], oc, 1.6*S, 0.9)
    if bg:
        draw_glow(buf, Ws, Hs, cxc, cyc, Rs*1.25, (120, 220, 255), 0.10)
    out = bytearray(W*H*4)
    area = S*S
    for y in range(H):
        for x in range(W):
            r = g = b = a = 0
            for sy in range(S):
                row = (y*S+sy)*Ws
                for sx in range(S):
                    i4 = (row + x*S+sx)*4
                    r += buf[i4]; g += buf[i4+1]; b += buf[i4+2]; a += buf[i4+3]
            o4 = (y*W+x)*4
            out[o4] = r//area; out[o4+1] = g//area; out[o4+2] = b//area; out[o4+3] = a//area
    return out

def round_mask(buf, W):
    H = len(buf)//(W*4)
    cx, cy = W/2, H/2
    rad = W/2 - 2
    for y in range(H):
        for x in range(W):
            d = math.hypot(x+0.5-cx, y+0.5-cy)
            i4 = (y*W+x)*4
            if d > rad + 1:
                buf[i4] = buf[i4+1] = buf[i4+2] = buf[i4+3] = 0
            elif d > rad - 1:
                t = (rad + 1 - d) / 2.0
                buf[i4+3] = int(buf[i4+3] * max(0.0, min(1.0, t)))

def scale_box(src, sw, sh, dw, dh):
    out = bytearray(dw*dh*4)
    for y in range(dh):
        sy0 = y*sh//dh; sy1 = max(sy0+1, (y+1)*sh//dh)
        for x in range(dw):
            sx0 = x*sw//dw; sx1 = max(sx0+1, (x+1)*sw//dw)
            r = g = b = a = n = 0
            for yy in range(sy0, sy1):
                base = yy*sw
                for xx in range(sx0, sx1):
                    i4 = (base+xx)*4
                    r += src[i4]; g += src[i4+1]; b += src[i4+2]; a += src[i4+3]; n += 1
            o4 = (y*dw+x)*4
            out[o4] = r//n; out[o4+1] = g//n; out[o4+2] = b//n; out[o4+3] = a//n
    return out

if __name__ == '__main__':
    root = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')
    sizes = {'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}
    print('rendering master (512, x3 supersample)...')
    master = render_emblem(512, 512, 168, bg=True)
    write_png('/tmp/icon_master.png', 512, 512, master)
    for d, s in sizes.items():
        for name, circ in (('ic_launcher', False), ('ic_launcher_round', True)):
            img = scale_box(master, 512, 512, s, s)
            if circ: round_mask(img, s)
            write_png(os.path.join(root, f'mipmap-{d}', name+'.png'), s, s, img)
        print('done', d)
    fsizes = {'mdpi':108,'hdpi':162,'xhdpi':216,'xxhdpi':324,'xxxhdpi':432}
    print('rendering foreground master (432, x3 supersample)...')
    fg = render_emblem(432, 432, 118, bg=False)
    for d, s in fsizes.items():
        img = scale_box(fg, 432, 432, s, s)
        write_png(os.path.join(root, f'mipmap-{d}', 'ic_launcher_foreground.png'), s, s, img)
        print('fg done', d)
    print('all icons done')
