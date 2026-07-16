#!/usr/bin/env python3
"""Prozeduraler Audio-Generator für Puzzlewerk (Phase 4 'Juice-Update').

Erzeugt lizenzfreie, selbst-synthetisierte Assets:
- 4 loopbare Musik-Stems (gleiche Länge/Tempo) — 'urig' -> 'modern'
- 1 Demo-Mix, der die Steigerung vorführt
- 12 Sound-Effekte (Drehen, Laser, Kristall, Combo, Explosion, Sterne, UI)
Alle deterministisch (fester Seed).
"""
import numpy as np
from scipy.signal import butter, lfilter
import os, subprocess

SR = 44100
RNG = np.random.default_rng(42)
OUT = "/home/claude/audio/out"
os.makedirs(OUT, exist_ok=True)

BPM = 112.0
BEAT = 60.0 / BPM
BAR = 4 * BEAT
LOOP_BEATS = 32                      # 8 Takte
LOOP_LEN = int(round(LOOP_BEATS * BEAT * SR))

def t_axis(n): return np.arange(n) / SR

def env_adsr(n, a=0.01, d=0.1, s=0.6, r=0.2):
    t = np.zeros(n); na, nd, nr = int(a*SR), int(d*SR), int(r*SR)
    na, nd, nr = min(na,n), min(nd, max(0,n-na)), min(nr, n)
    t[:na] = np.linspace(0, 1, na, endpoint=False)
    t[na:na+nd] = np.linspace(1, s, nd, endpoint=False)
    t[na+nd:] = s
    t[n-nr:] *= np.linspace(1, 0, nr)
    return t

def lowpass(x, fc, order=2):
    b, a = butter(order, min(fc/(SR/2), 0.99), btype="low"); return lfilter(b, a, x)

def highpass(x, fc, order=2):
    b, a = butter(order, max(fc/(SR/2), 1e-4), btype="high"); return lfilter(b, a, x)

def bandpass(x, lo, hi):
    b, a = butter(2, [max(lo/(SR/2),1e-4), min(hi/(SR/2),0.99)], btype="band"); return lfilter(b, a, x)

def softclip(x, drive=1.0): return np.tanh(x * drive)

def norm(x, peak=0.891):  # ~ -1 dBFS
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak

def delay_fx(x, time_s, fb=0.35, mix=0.25):
    d = int(time_s * SR); y = np.copy(x)
    buf = np.zeros(len(x) + d * 8)
    buf[:len(x)] += x
    for i in range(1, 8):
        seg = x * (fb ** i)
        start = d * i
        buf[start:start+len(x)] += seg
    y = buf[:len(x)] * mix + x * (1 - mix)
    return y

def reverb(x, mix=0.18):
    # Schroeder-Reverb (4 Kammfilter + 2 Allpass), bewusst schlicht
    def comb(sig, dsec, fb):
        d = int(dsec * SR); out = np.copy(sig)
        for i in range(d, len(sig)): out[i] += out[i-d] * fb
        return out
    def allp(sig, dsec, g=0.5):
        d = int(dsec * SR); out = np.copy(sig)
        for i in range(d, len(sig)): out[i] = -g*sig[i] + sig[i-d] + g*out[i-d]
        return out
    wet = sum(comb(x, dz, fb) for dz, fb in
              [(0.0297,0.65),(0.0371,0.61),(0.0411,0.58),(0.0437,0.55)]) / 4
    wet = allp(allp(wet, 0.005), 0.0017)
    return x * (1-mix) + wet * mix

def sine(f, n, ph=0.0): return np.sin(2*np.pi*f*t_axis(n) + ph)

def saw(f, n):
    t = t_axis(n); return 2*(t*f - np.floor(0.5 + t*f))

def pluck(f, dur, bright=0.5):
    """Karplus-Strong — Kalimba/Marimba-artig."""
    n = int(dur*SR); d = max(2, int(SR/f))
    buf = RNG.uniform(-1, 1, d) * (1-bright) + saw(f, d) * bright
    out = np.zeros(n)
    for i in range(n):
        out[i] = buf[i % d]
        buf[i % d] = 0.5 * (buf[i % d] + buf[(i+1) % d]) * 0.996
    return out * env_adsr(n, 0.002, dur*0.3, 0.3, dur*0.5)

def fm_bell(f, dur, ratio=2.76, index=3.0):
    n = int(dur*SR); t = t_axis(n)
    mod = np.sin(2*np.pi*f*ratio*t) * index * np.exp(-t*4)
    y = np.sin(2*np.pi*f*t + mod) * np.exp(-t*3.2)
    return y

def frame_drum(dur=0.35, f0=130, f1=48):
    n = int(dur*SR); t = t_axis(n)
    f = f0 * (f1/f0) ** (t/dur)
    body = np.sin(2*np.pi*np.cumsum(f)/SR)
    skin = lowpass(RNG.uniform(-1,1,n), 900) * np.exp(-t*30) * 0.5
    return (body*np.exp(-t*9) + skin) * env_adsr(n, 0.001, dur*0.5, 0.0, 0.02)

def kick(dur=0.3):
    n = int(dur*SR); t = t_axis(n)
    f = 110 * (40/110) ** (t/0.12); f = np.clip(f, 40, 120)
    y = np.sin(2*np.pi*np.cumsum(f)/SR) * np.exp(-t*14)
    click = highpass(RNG.uniform(-1,1,n), 3000) * np.exp(-t*220) * 0.4
    return softclip(y*1.4 + click)

def shaker(dur=0.09):
    n = int(dur*SR)
    return bandpass(RNG.uniform(-1,1,n), 4000, 9000) * env_adsr(n, 0.003, 0.05, 0.0, 0.02)

def rim(dur=0.07):
    n = int(dur*SR)
    return bandpass(RNG.uniform(-1,1,n), 1500, 3500) * env_adsr(n, 0.001, 0.03, 0.0, 0.01)

def place(canvas, snd, beat_pos, gain=1.0):
    i = int(round(beat_pos * BEAT * SR))
    j = min(len(canvas), i + len(snd))
    if i < len(canvas): canvas[i:j] += snd[:j-i] * gain

# Harmonik: d-Moll-Pentatonik / Progression Dm — Bb — F — C (je 2 Takte)
D3, F3, G3, A3, C4, D4, F4, G4, A4, C5, D5 = (146.83, 174.61, 196.0, 220.0,
    261.63, 293.66, 349.23, 392.0, 440.0, 523.25, 587.33)
CHORDS = [(D3,F3,A3,C4), (116.54,F3,A3,D4), (F3,A3,C4,F4*0.5+87.31*0), (C4*0.5+65.41*0, G3, C4, 164.81)]
CHORDS = [(D3, F3, A3, C4), (116.54, 174.61, 233.08, 293.66),
          (174.61, 220.0, 261.63, 349.23), (130.81, 196.0, 261.63, 329.63)]
ROOTS  = [73.42, 58.27, 87.31, 65.41]   # D2, Bb1, F2, C2

def stem_a_urig():
    c = np.zeros(LOOP_LEN)
    for bar in range(8):
        b0 = bar*4
        for pos, g, tune in [(0,1.0,(130,46)),(1.5,0.7,(150,60)),(2,0.9,(130,46)),(3.5,0.5,(170,70))]:
            place(c, frame_drum(0.4, *tune), b0+pos, g)
        for s in range(8):
            place(c, shaker(), b0 + s*0.5 + 0.25, 0.25 if s%2 else 0.16)
    drone = (saw(73.42, LOOP_LEN)*0.4 + sine(36.71, LOOP_LEN)*0.6)
    drone = lowpass(drone, 220) * (0.8 + 0.2*sine(0.25, LOOP_LEN))
    return norm(reverb(c*0.9 + drone*0.14, 0.15), 0.7)

def stem_b_kalimba():
    c = np.zeros(LOOP_LEN)
    motiv = [(0,D4),(0.5,F4),(1,A4),(1.75,G4),(2.5,C5),(3,A4),(3.5,D5)]
    for bar in range(8):
        b0 = bar*4
        for pos, f in motiv:
            if (bar+pos) % 3 == 2 and pos > 2.5: continue
            place(c, pluck(f, 0.7, 0.35), b0+pos, 0.55)
        place(c, rim(), b0+2.5, 0.5); place(c, rim(), b0+3.75, 0.3)
    return norm(reverb(delay_fx(c, BEAT*0.75, 0.3, 0.2), 0.2), 0.6)

def stem_c_bass_kick():
    c = np.zeros(LOOP_LEN)
    for bar in range(8):
        b0 = bar*4; root = ROOTS[(bar//2) % 4]
        for k in range(4): place(c, kick(), b0+k, 0.9)
        for pos, mul in [(0,1),(0.75,1),(1.5,1.5),(2,1),(2.75,1),(3.5,0.75)]:
            n = int(0.4*SR)
            y = (sine(root*mul, n)*0.7 + saw(root*mul, n)*0.3)
            y = lowpass(y, 300) * env_adsr(n, 0.005, 0.15, 0.4, 0.1)
            place(c, y, b0+pos, 0.5)
    return norm(softclip(c, 1.2), 0.72)

def stem_d_modern():
    c = np.zeros(LOOP_LEN)
    for bar in range(8):
        b0 = bar*4; ch = CHORDS[(bar//2) % 4]
        for s in range(16):                       # 16tel-Arpeggio
            f = ch[s % 4] * (2 if s % 8 >= 4 else 1) * 2
            n = int(0.12*SR)
            y = lowpass(saw(f, n), 1200 + 2800*(s/16)) * env_adsr(n, 0.003, 0.06, 0.2, 0.04)
            place(c, y, b0 + s*0.25, 0.32)
        pad = sum(sine(f*2, int(BAR*SR)) for f in ch) / 4
        pad = lowpass(pad, 900) * env_adsr(int(BAR*SR), 0.4, 0.5, 0.7, 0.8)
        place(c, pad, b0, 0.16)
    return norm(reverb(delay_fx(c, BEAT*0.5, 0.35, 0.22), 0.2), 0.62)

def wav_write(name, x):
    from scipy.io import wavfile
    x16 = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    wavfile.write(f"{OUT}/{name}.wav", SR, x16)
    subprocess.run(["ffmpeg","-y","-loglevel","error","-i",f"{OUT}/{name}.wav",
                    "-c:a","libvorbis","-q:a","4",f"{OUT}/{name}.ogg"], check=True)

# ============ Musik rendern ============
A, B, C, D = stem_a_urig(), stem_b_kalimba(), stem_c_bass_kick(), stem_d_modern()
wav_write("music_stem1_urig", A)
wav_write("music_stem2_kalimba", B)
wav_write("music_stem3_bass", C)
wav_write("music_stem4_modern", D)

demo = np.concatenate([A, norm(A+B,0.85), norm(A+B+C,0.9), norm(A*0.8+B+C+D,0.95)])
wav_write("music_demo_steigerung", norm(demo, 0.89))

# ============ SFX ============
def sfx_rotate():
    n = int(0.09*SR)
    return norm(bandpass(RNG.uniform(-1,1,n),1800,4000)*env_adsr(n,0.001,0.04,0,0.02)
                + sine(660,n)*env_adsr(n,0.001,0.03,0,0.01)*0.3, 0.7)

def sfx_invalid():
    n = int(0.15*SR); f = np.linspace(220, 180, n)
    return norm(np.sin(2*np.pi*np.cumsum(f)/SR)*env_adsr(n,0.005,0.1,0,0.04)*0.6, 0.5)

def sfx_beam(dur=0.35, f0=250, f1=2400):
    n = int(dur*SR); t = t_axis(n)
    f = f0 * (f1/f0) ** (t/dur)
    y = saw(1,1)  # placeholder
    y = np.sin(2*np.pi*np.cumsum(f)/SR) + 0.4*np.sin(4*np.pi*np.cumsum(f)/SR)
    y *= env_adsr(n, 0.005, dur*0.4, 0.3, dur*0.3)
    return norm(reverb(y, 0.15), 0.75)

def sfx_crystal(f=880):
    y = fm_bell(f, 1.1)
    return norm(reverb(y, 0.3), 0.7)

def sfx_combo(step):
    base = 523.25 * (2 ** (step/12*2))
    y = fm_bell(base, 0.5, ratio=2.0, index=2.0)
    n=len(y); y += sine(base*1.5, n)*np.exp(-t_axis(n)*6)*0.3
    return norm(reverb(y,0.25), 0.72)

def sfx_explosion():
    n = int(1.6*SR); t = t_axis(n)
    f = np.clip(150*(28/150)**(t/0.25), 28, 150)
    boom = np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-t*5)
    debris = lowpass(RNG.uniform(-1,1,n), 2500)*np.exp(-t*4)*0.7
    sub = sine(34, n)*np.exp(-t*3.5)*0.8
    y = softclip(boom*1.3 + debris + sub, 1.4)
    for i in range(10):                                # Funkenregen
        fb = float(RNG.uniform(1200, 3800)); st = float(RNG.uniform(0.15, 0.9))
        bell = fm_bell(fb, 0.5, 3.1, 2.0) * 0.12
        j = int(st*SR); y[j:j+len(bell)] += bell[:max(0, min(len(bell), n-j))]
    return norm(reverb(y, 0.3), 0.85)

def sfx_star(i):
    y = fm_bell([1046.5, 1318.5, 1568.0][i], 0.7, 3.0, 1.6)
    return norm(reverb(y, 0.3), 0.68)

def sfx_laser_loop():
    n = int(1.0*SR)
    vib = 1 + 0.004*sine(6, n)
    y = lowpass(saw(96, n)*0.5 + saw(96.7, n)*0.5, 700) * vib + sine(48, n)*0.3
    fade = int(0.02*SR)                                # loop-sauber
    y[:fade] *= np.linspace(0,1,fade); y[-fade:] *= np.linspace(1,0,fade)
    return norm(y, 0.4)

def sfx_ui():
    n = int(0.05*SR)
    return norm(sine(880,n)*env_adsr(n,0.002,0.03,0,0.01), 0.5)

wav_write("sfx_rotate_tick", sfx_rotate())
wav_write("sfx_rotate_invalid", sfx_invalid())
wav_write("sfx_beam_connect", sfx_beam())
wav_write("sfx_crystal_lit", sfx_crystal())
for i in range(3): wav_write(f"sfx_combo_up{i+1}", sfx_combo(i*2+1))
wav_write("sfx_solve_explosion", sfx_explosion())
for i in range(3): wav_write(f"sfx_star_{i+1}", sfx_star(i))
wav_write("sfx_laser_loop", sfx_laser_loop())
wav_write("sfx_ui_tap", sfx_ui())

print("fertig:", len(os.listdir(OUT)), "Dateien in", OUT)
