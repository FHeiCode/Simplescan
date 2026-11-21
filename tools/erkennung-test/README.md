# Test der Seitenerkennung

Erzeugt künstliche Fotos (A4, Letter, A5, A4 quer, Kassenbon, Visitenkarte, Briefumschlag DL) auf
verschiedenen Untergründen (hellgrau, dunkles Holz, Muster, weißer Tisch, beige), schräg fotografiert,
mit Störungen (Finger am Rand, Eselsohr, runde Ecken, Stift auf dem Tisch, gefalteter Brief,
spiegelnde Tischfläche), und misst, wie oft die Ecken stimmen (Abweichung unter 1,5 % der Bilddiagonale).

- `new.py` entspricht `PageDetector.kt` (Python-Nachbau zum schnellen Ausprobieren),
- `old.py` ist die Erkennung bis Version 2.2.0 zum Vergleich.

```bash
pip install opencv-python-headless numpy
python3 evaluate.py new 280    # Foto, Stand 2.3.0: 230/280 richtig
python3 evaluate.py old 280    # Foto, bis 2.2.0:    71/280 richtig
python3 live_eval.py new 280   # Live (Graustufen 480 px): 208/280 (bis 2.2.0: 67)
```

Änderungen an `PageDetector.kt` zuerst hier ausprobieren und nachmessen.
