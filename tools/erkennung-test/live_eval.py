import sys, importlib, numpy as np, cv2
from scenes import scene, W, H
diag=np.hypot(W,H)
def live(mod, img):
    g=cv2.cvtColor(img,cv2.COLOR_BGR2GRAY); s=480/max(g.shape)
    small=cv2.resize(g,None,fx=s,fy=s,interpolation=cv2.INTER_AREA)
    q = mod.detect_gray(small) if hasattr(mod,'detect_gray') and not hasattr(mod,'detect_bgr') else mod.detect_bgr(cv2.cvtColor(small,cv2.COLOR_GRAY2BGR))
    return None if q is None else q/s
mod=importlib.import_module(sys.argv[1]); n=int(sys.argv[2]); ok=bad=none=0
for seed in range(n):
    img,gt,_,_=scene(seed); q=live(mod,img)
    if q is None: none+=1; continue
    e=max(np.hypot(*(q[i]-gt[i])) for i in range(4))/diag
    ok+=e<0.015; bad+=e>=0.015
print(f"LIVE {sys.argv[1]}: richtig {ok}/{n}  falsche Ecken {bad}  nichts {none}")
