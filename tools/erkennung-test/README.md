# Test der Seitenerkennung

Erzeugt künstliche Fotos (A4, Letter, A5, A4 quer, Kassenbon, Visitenkarte, Briefumschlag DL) auf
verschiedenen Untergründen (hellgrau, dunkles Holz, Muster, weißer Tisch, beige), schräg fotografiert,
mit Störungen (Finger am Rand, Eselsohr, runde Ecken, Stift auf dem Tisch), und misst, wie oft die
Ecken stimmen (Abweichung unter 1,5 % der Bilddiagonale).

- `new.py` entspricht `PageDetector.kt` (Python-Nachbau zum schnellen Ausprobieren),
- `old.py` ist die Erkennung bis Version 2.2.0 zum Vergleich.

```bash
pip install opencv-python-headless numpy
python3 evaluate.py new 140   # Stand 2.3.0: 135/140 richtig
python3 evaluate.py old 140   # bis 2.2.0:   57/140 richtig
```

Änderungen an `PageDetector.kt` zuerst hier ausprobieren und nachmessen.
