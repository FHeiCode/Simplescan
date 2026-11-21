"""Neuer Erkenner: mehrere Kanten-/Flächenkarten, Kandidaten sammeln, nach Kantentreue bewerten,
Ecken über Geraden durch die Seiten verfeinern (robust gegen Eselsohren, runde Ecken, Finger)."""
import cv2, numpy as np
from old import order, convex, area

def plausible(q, w, h, min_area=0.04):
    if not convex(q) or area(q) < min_area * w * h: return False
    sides = [np.hypot(*(q[i] - q[(i + 1) % 4])) for i in range(4)]
    if min(sides) < 0.12 * max(sides): return False
    # Innenwinkel 35°..145° (perspektivisch verzerrtes Rechteck)
    for i in range(4):
        a, b, c = q[i - 1], q[i], q[(i + 1) % 4]
        v1, v2 = a - b, c - b
        cosang = np.dot(v1, v2) / (np.linalg.norm(v1) * np.linalg.norm(v2) + 1e-9)
        if not (-0.82 < cosang < 0.82): return False
    # Bildrand: an mehr als 2 Seiten am Rand → eher der ganze Ausschnitt/Tisch
    m = 0.01 * max(w, h)
    touch = sum([q[:, 0].min() < m, q[:, 1].min() < m, q[:, 0].max() > w - m, q[:, 1].max() > h - m])
    return touch <= 2

def quad_from_contour(c):
    hull = cv2.convexHull(c)
    peri = cv2.arcLength(hull, True)
    for eps in (0.01, 0.015, 0.02, 0.03, 0.04, 0.05, 0.065, 0.08):
        ap = cv2.approxPolyDP(hull, eps * peri, True)
        if len(ap) == 4: return order(ap.reshape(4, 2).astype(np.float64)), hull.reshape(-1, 2).astype(np.float64)
        if len(ap) < 4: break
    return None, None

def refine(q, pts):
    """Gerade durch die Konturpunkte jeder Seite (ohne die Eckbereiche), Ecken = Schnittpunkte."""
    if pts is None or len(pts) < 8: return q
    # Konturpunkte dicht machen
    dense = []
    for i in range(len(pts)):
        a, b = pts[i], pts[(i + 1) % len(pts)]
        n = max(2, int(np.hypot(*(b - a)) / 3))
        dense.extend(a + (b - a) * t for t in np.linspace(0, 1, n, endpoint=False))
    dense = np.array(dense)
    lines = []
    for i in range(4):
        a, b = q[i], q[(i + 1) % 4]
        d = b - a; L = np.linalg.norm(d); u = d / L; nrm = np.array([-u[1], u[0]])
        rel = dense - a
        t = rel @ u; off = np.abs(rel @ nrm)
        sel = dense[(t > 0.12 * L) & (t < 0.88 * L) & (off < 0.03 * L + 3)]
        if len(sel) < 5: return q
        vx, vy, x0, y0 = cv2.fitLine(sel.astype(np.float32), cv2.DIST_HUBER, 0, 0.01, 0.01).ravel()
        lines.append((np.array([x0, y0]), np.array([vx, vy])))
    out = []
    for i in range(4):
        (p1, d1), (p2, d2) = lines[i - 1], lines[i]
        A = np.array([d1, -d2]).T
        if abs(np.linalg.det(A)) < 1e-6: return q
        s = np.linalg.solve(A, p2 - p1)
        out.append(p1 + d1 * s[0])
    r = order(np.array(out))
    # Verfeinerung darf nicht weit wegspringen
    if max(np.hypot(*(r[i] - q[i])) for i in range(4)) > 0.12 * np.sqrt(area(q)): return q
    return r

def support(q, edges):
    """Anteil der Punkte auf den vier Seiten, die auf einer Kante liegen."""
    h, w = edges.shape; hit = tot = 0
    for i in range(4):
        a, b = q[i], q[(i + 1) % 4]
        n = max(10, int(np.hypot(*(b - a)) / 4))
        for t in np.linspace(0.05, 0.95, n):
            x, y = (a + (b - a) * t).astype(int)
            tot += 1
            if 0 <= x < w and 0 <= y < h and edges[y, x]: hit += 1
    return hit / max(tot, 1)

def candidates(binary):
    cs, _ = cv2.findContours(binary, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    for c in sorted(cs, key=cv2.contourArea, reverse=True)[:12]:
        yield c

def detect_bgr(small):
    h, w = small.shape[:2]
    gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
    hsv = cv2.cvtColor(small, cv2.COLOR_BGR2HSV)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    med = float(np.median(blur))
    maps = []
    # 1) Kanten im Grau (Schwellen aus dem Bild)
    e1 = cv2.Canny(blur, max(10, 0.4 * med), max(30, 1.0 * med))
    # 2) Kanten in der Sättigung (weißes Papier vor farbigem Grund)
    e2 = cv2.Canny(cv2.GaussianBlur(hsv[..., 1], (5, 5), 0), 15, 45)
    # 3) schwache Kanten (weißes Blatt auf hellem Tisch): örtlich Kontrast anheben, niedrige Schwellen
    cl = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8)).apply(cv2.GaussianBlur(gray, (7, 7), 0))
    e3 = cv2.Canny(cl, 12, 36)
    edges = cv2.dilate(e1 | e2 | e3, np.ones((3, 3), np.uint8))
    k = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))
    maps.append(cv2.morphologyEx(e1 | e2, cv2.MORPH_CLOSE, k))
    maps.append(cv2.morphologyEx(e3, cv2.MORPH_CLOSE, k))
    # 3) Fläche „Papier“: hell und ungesättigt, Schrift-Löcher schließen
    paper = cv2.subtract(hsv[..., 2], hsv[..., 1])
    paper = cv2.GaussianBlur(paper, (7, 7), 0)
    _, pm = cv2.threshold(paper, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    pm = cv2.morphologyEx(pm, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_RECT, (15, 15)))
    pm = cv2.morphologyEx(pm, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_RECT, (9, 9)))
    maps.append(pm)
    # 4) Otsu im Grau
    _, om = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    om = cv2.morphologyEx(om, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_RECT, (15, 15)))
    maps.append(om)
    found = []  # (Viereck, Kantentreue)
    for m in maps:
        for c in candidates(m):
            q, hull = quad_from_contour(c)
            if q is None or not plausible(q, w, h): continue
            q = refine(q, hull)
            if not plausible(q, w, h): continue
            sup = support(q, edges)
            if sup >= 0.35: found.append((q, sup))
    good = [(q, s) for q, s in found if s >= 0.45]
    if not good: return None
    best = max(good, key=lambda t: t[1] * np.sqrt(area(t[0]) / (w * h)))[0]
    # Liegt das beste Viereck in einem größeren, das auch passt (Kasten/Tabelle im Blatt)? Dann das Blatt nehmen.
    outer = [q for q, s in found if area(q) > 1.25 * area(best) and inside(best, q)]
    if outer: best = max(outer, key=area)
    return best

def inside(small, big, tol=4.0):
    poly = big.astype(np.float32).reshape(-1, 1, 2)
    return all(cv2.pointPolygonTest(poly, (float(x), float(y)), True) >= -tol for x, y in small)

def detect(img, work=800):
    s = work / max(img.shape[:2])
    small = cv2.resize(img, None, fx=s, fy=s, interpolation=cv2.INTER_AREA)
    q = detect_bgr(small)
    return None if q is None else q / s

def detect_photo(img):
    return detect(img, 800)
