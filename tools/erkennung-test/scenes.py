"""Künstliche Testfotos: Dokumente verschiedener Formate auf verschiedenen Untergründen, schräg fotografiert."""
import cv2, numpy as np, math, random

W, H = 1200, 1600  # Hochkant-Foto (wie Handy)
DOCS = {  # Name: (Breite mm, Höhe mm, Anteil der Bildhöhe min/max)
    "A4": (210, 297, 0.55, 0.85),
    "Letter": (216, 279, 0.55, 0.85),
    "A5": (148, 210, 0.45, 0.75),
    "Quer-A4": (297, 210, 0.35, 0.55),
    "Kassenbon": (80, 230, 0.45, 0.8),
    "Visitenkarte": (85, 55, 0.18, 0.35),
    "Brief-DL": (220, 110, 0.25, 0.4),
}
BGS = ["hell-grau", "dunkel-holz", "muster", "weiss-tisch", "beige"]

def background(kind, rng):
    if kind == "hell-grau":
        img = np.full((H, W, 3), 175, np.float32)
    elif kind == "dunkel-holz":
        x = np.linspace(0, 40, W)[None, :] + rng.normal(0, 1, (H, 1)) * 3
        img = (70 + 18 * np.sin(x + np.linspace(0, 8, H)[:, None]))[..., None] * np.array([1.0, 0.75, 0.5])
    elif kind == "muster":
        yy, xx = np.mgrid[0:H, 0:W]
        chk = (((xx // 60) + (yy // 60)) % 2).astype(np.float32)
        img = (140 + 30 * chk)[..., None] * np.array([0.8, 0.9, 1.0])
    elif kind == "weiss-tisch":
        img = np.full((H, W, 3), 222, np.float32)
    else:
        img = np.full((H, W, 3), 200, np.float32) * np.array([1.0, 0.93, 0.8])
    img = img + rng.normal(0, 6, img.shape)
    return img.astype(np.float32)

def document(wmm, hmm, rng, px_per_mm=6):
    w, h = int(wmm * px_per_mm), int(hmm * px_per_mm)
    paper = np.full((h, w, 3), 245, np.float32) - rng.uniform(0, 12)
    # Textzeilen
    y = int(h * 0.08)
    while y < h * 0.92:
        lh = max(3, int(h * 0.008))
        x0 = int(w * 0.08); x1 = int(w * rng.uniform(0.5, 0.92))
        if rng.random() < 0.85:
            cv2.rectangle(paper, (x0, y), (x1, y + lh), (60, 60, 60), -1)
        y += int(lh * rng.uniform(2.2, 3.5))
    if rng.random() < 0.4:  # farbiger Kasten / Logo
        cv2.rectangle(paper, (int(w * .6), int(h * .05)), (int(w * .92), int(h * .14)), (40, 90, 200), -1)
    if rng.random() < 0.4:  # gefalteter Brief: zwei Knicklinien quer (kräftige innere Kanten)
        for f in (1 / 3, 2 / 3):
            y0 = int(h * f); cv2.line(paper, (0, y0), (w, y0 + int(rng.uniform(-8, 8))), (190, 190, 190), 3)
            paper[y0:y0 + int(h * 0.3)] *= rng.uniform(0.9, 0.97)
    if rng.random() < 0.3:  # Tabellenrahmen (verführt zu falschen Vierecken)
        cv2.rectangle(paper, (int(w * .08), int(h * .45)), (int(w * .92), int(h * .75)), (30, 30, 30), 3)
    return paper

def project(wmm, hmm, frac, rng):
    """Ecken des Dokuments im Foto: gekippt (nach vorn + seitlich), gedreht, verschoben."""
    f = 0.75 * H
    tilt_x = math.radians(rng.uniform(0, 35)); tilt_y = math.radians(rng.uniform(-12, 12))
    rot = math.radians(rng.uniform(-20, 20))
    pts3 = []
    for (x, y) in [(-wmm / 2, -hmm / 2), (wmm / 2, -hmm / 2), (wmm / 2, hmm / 2), (-wmm / 2, hmm / 2)]:
        x, y = x * math.cos(rot) - y * math.sin(rot), x * math.sin(rot) + y * math.cos(rot)
        y2 = y * math.cos(tilt_x); z = y * math.sin(tilt_x)
        x2 = x * math.cos(tilt_y) + z * math.sin(tilt_y); z = -x * math.sin(tilt_y) + z * math.cos(tilt_y)
        pts3.append((x2, y2, z))
    # Abstand so, dass das Dokument etwa frac der Bildhöhe einnimmt
    span = max(abs(p[1]) for p in pts3) * 2
    dist = f * span / (frac * H)
    cx = W / 2 + rng.uniform(-0.12, 0.12) * W; cy = H / 2 + rng.uniform(-0.1, 0.1) * H
    return np.array([(cx + f * x / (z + dist), cy + f * y / (z + dist)) for x, y, z in pts3], np.float32)

def scene(seed):
    rng = np.random.default_rng(seed)
    name = list(DOCS)[seed % len(DOCS)]
    bgk = BGS[(seed // len(DOCS)) % len(BGS)]
    wmm, hmm, lo, hi = DOCS[name]
    img = background(bgk, rng)
    doc = document(wmm, hmm, rng)
    corners = project(wmm, hmm, rng.uniform(lo, hi), rng)
    # Blatt ganz ins Bild holen (wie beim echten Scannen): verschieben, notfalls verkleinern
    for _ in range(20):
        mn = corners.min(0); mx = corners.max(0)
        if mn[0] >= 0.03 * W and mn[1] >= 0.03 * H and mx[0] <= 0.97 * W and mx[1] <= 0.97 * H: break
        c = corners.mean(0); corners = ((corners - c) * 0.93 + np.array([W / 2, H / 2]) * 0.3 + c * 0.7).astype(np.float32)
    src = np.array([[0, 0], [doc.shape[1], 0], [doc.shape[1], doc.shape[0]], [0, doc.shape[0]]], np.float32)
    M = cv2.getPerspectiveTransform(src, corners)
    warped = cv2.warpPerspective(doc, M, (W, H))
    mask = cv2.warpPerspective(np.ones(doc.shape[:2], np.float32), M, (W, H))[..., None]
    # Schatten am Dokumentrand
    sh = cv2.GaussianBlur(mask, (0, 0), 6)[..., None] if mask.ndim == 2 else cv2.GaussianBlur(mask[..., 0], (0, 0), 6)[..., None]
    img = img * (1 - 0.25 * np.clip(cv2.warpAffine(sh, np.float32([[1, 0, 6], [0, 1, 8]]), (W, H))[..., None] if sh.ndim == 2 else sh, 0, 1))
    # Ecke abrunden (Karten) oder Eselsohr: Maske an einer Ecke beschneiden
    if name == "Visitenkarte" or rng.random() < 0.2:
        k = int(rng.integers(0, 4)); c = corners[k]; nxt = corners[(k + 1) % 4]; prv = corners[(k + 3) % 4]
        r = 0.06 if name == "Visitenkarte" else rng.uniform(0.05, 0.1)
        tri = np.array([c, c + (nxt - c) * r, c + (prv - c) * r], np.int32)
        m2 = mask[..., 0].copy(); cv2.fillConvexPoly(m2, tri, 0.0)
        if name != "Visitenkarte":  # umgeknickt: helleres Dreieck nach innen
            fold = np.array([c + (nxt - c) * r, c + (prv - c) * r, c + ((nxt - c) + (prv - c)) * r], np.int32)
            cv2.fillConvexPoly(warped, fold, (225, 225, 225))
        mask = m2[..., None]
    if rng.random() < 0.4:  # Schlagschatten mit geraden Kanten (Lampe schräg): Blatt verschoben, dunkler Grund
        off = np.array([rng.uniform(30, 120) * rng.choice([-1, 1]), rng.uniform(30, 140)], np.float32)
        sh = np.zeros((H, W), np.float32)
        cv2.fillConvexPoly(sh, cv2.convexHull(np.concatenate([corners, corners + off]).astype(np.int32)), 1.0)
        sh = cv2.GaussianBlur(sh, (0, 0), 2)[..., None]
        img = img * (1 - 0.55 * sh)
    if rng.random() < 0.35:  # spiegelnde Tischfläche: heller Fleck neben dem Blatt
        c = corners[int(rng.integers(0, 4))]
        blob = np.zeros((H, W), np.float32)
        cv2.ellipse(blob, (int(np.clip(c[0], 0, W)), int(np.clip(c[1], 0, H))), (int(W * .35), int(H * .2)), 0, 0, 360, 1.0, -1)
        blob = cv2.GaussianBlur(blob, (0, 0), 60)[..., None]
        img = img + blob * 90
    img = img * (1 - mask) + warped * mask
    # Finger über einer Kante
    if rng.random() < 0.3:
        k = int(rng.integers(0, 4)); a = corners[k]; b = corners[(k + 1) % 4]
        p = a + (b - a) * rng.uniform(0.3, 0.7)
        cv2.ellipse(img, (int(p[0]), int(p[1])), (45, 110), float(rng.uniform(0, 180)), 0, 360, (120, 150, 200), -1)
    # Stift auf dem Tisch
    if rng.random() < 0.3:
        x = int(rng.uniform(50, W - 50))
        cv2.line(img, (x, 40), (x + 200, 500), (30, 30, 160), 14)
    # Licht ungleichmäßig
    yy, xx = np.mgrid[0:H, 0:W]
    light = 0.8 + 0.25 * (xx / W) * rng.uniform(0.5, 1) + 0.1 * (yy / H)
    img = img * light[..., None]
    img = cv2.GaussianBlur(np.clip(img, 0, 255).astype(np.uint8), (3, 3), 0)
    img = np.clip(img.astype(np.float32) + rng.normal(0, 3, img.shape), 0, 255).astype(np.uint8)
    return img, corners, name, bgk
