import sys, importlib, numpy as np, collections
from scenes import scene, W, H
mod = importlib.import_module(sys.argv[1]); n = int(sys.argv[2]) if len(sys.argv) > 2 else 140
diag = np.hypot(W, H)
stats = collections.defaultdict(lambda: [0, 0, 0])  # ok, falsch, keine
for seed in range(n):
    img, gt, name, bg = scene(seed)
    q = mod.detect_photo(img)
    if q is None: stats[name][2] += 1; stats[bg][2] += 1; continue
    err = max(np.hypot(*(q[i] - gt[i])) for i in range(4)) / diag
    k = 0 if err < 0.015 else 1
    stats[name][k] += 1; stats[bg][k] += 1
tot = [0, 0, 0]
for key, (ok, bad, none) in stats.items():
    if key in ("A4","Letter","A5","Quer-A4","Kassenbon","Visitenkarte","Brief-DL"):
        tot[0] += ok; tot[1] += bad; tot[2] += none
for key, (ok, bad, none) in stats.items():
    print(f"{key:14s} richtig {ok:3d}  falsche Ecken {bad:3d}  nichts {none:3d}")
print(f"GESAMT         richtig {tot[0]}/{sum(tot)}  falsche Ecken {tot[1]}  nichts {tot[2]}")
