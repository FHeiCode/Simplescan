"""Bisheriger Erkenner (wie PageDetector.kt v2.2.0) zum Vergleich."""
import cv2, numpy as np

def order(p):
    """Nach Winkel um die Mitte sortieren, mit oben-links beginnen (robust bei doppelten Punkten, wie QuadMath.order)."""
    p = np.asarray(p, np.float64).reshape(4, 2); c = p.mean(0)
    s = p[np.argsort(np.arctan2(p[:, 1] - c[1], p[:, 0] - c[0]))]
    k = int(np.argmin(s[:, 0] + s[:, 1]))
    return np.roll(s, -k, axis=0)

def area(q): return abs(cv2.contourArea(q.astype(np.float32)))

def convex(q):
    s = 0
    for i in range(4):
        a, b, c = q[i], q[(i + 1) % 4], q[(i + 2) % 4]
        cr = (b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0])
        sg = 1 if cr > 0 else -1 if cr < 0 else 0
        if sg == 0: return False
        if s == 0: s = sg
        elif sg != s: return False
    return True

def plausible(q, w, h, min_area=0.15):
    if not convex(q) or area(q) < min_area * w * h: return False
    sides = [np.hypot(*(q[i] - q[(i + 1) % 4])) for i in range(4)]
    return min(sides) > 0.15 * max(sides)

def best(binary, w, h):
    cs, _ = cv2.findContours(binary, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    for c in sorted(cs, key=cv2.contourArea, reverse=True)[:8]:
        ap = cv2.approxPolyDP(c, 0.02 * cv2.arcLength(c, True), True)
        if len(ap) == 4:
            q = order(ap.reshape(4, 2).astype(np.float64))
            if plausible(q, w, h): return q
    return None

def detect_gray(g):
    b = cv2.GaussianBlur(g, (5, 5), 0)
    e = cv2.Canny(b, 40, 120)
    e = cv2.morphologyEx(e, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5)))
    q = best(e, g.shape[1], g.shape[0])
    if q is not None: return q
    _, t = cv2.threshold(b, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    return best(t, g.shape[1], g.shape[0])

def detect(img, work=900):
    s = work / max(img.shape[:2])
    small = cv2.resize(img, None, fx=s, fy=s, interpolation=cv2.INTER_AREA)
    q = detect_gray(cv2.cvtColor(small, cv2.COLOR_BGR2GRAY))
    return None if q is None else q / s

def detect_photo(img):
    return detect(img, 900) if detect(img, 900) is not None else detect(img, 480)
