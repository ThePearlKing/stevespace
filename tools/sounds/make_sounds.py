"""Sounds for Steve's Space Program, all synthesised (no recordings).

Run:  python3 tools/sounds/make_sounds.py   (needs numpy, scipy and ffmpeg with libvorbis)
Prints peak and RMS levels of every sound; keep peaks at or below -3 dBFS.
"""
import os
import subprocess
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), '../../src/main/resources/assets/stevespace/sounds')


def lowpass(x, hz, order=4):
    b, a = signal.butter(order, hz / (RATE / 2), 'low')
    return signal.lfilter(b, a, x)


def bandpass(x, lo, hi, order=3):
    b, a = signal.butter(order, [lo / (RATE / 2), hi / (RATE / 2)], 'band')
    return signal.lfilter(b, a, x)


def seamless(x, fade):
    """Crossfade the tail into the head so the sound loops without a click."""
    n = int(fade * RATE)
    head, body, tail = x[:n], x[n:-n], x[-n:]
    ramp = np.linspace(0, 1, n)
    return np.concatenate([tail * (1 - ramp) + head * ramp, body])


def engine_loop(seconds=6.0):
    """A rocket engine heard from the cabin: a deep roar with the crackle real exhaust has, dense
    positive shock pops (the pressure trace is lopsided, skew about +2, like launch recordings)
    that come in uneven bursts, and a restless low flutter. The cabin walls damp the top end.
    Made round a loop (all filtering circular) so it repeats without a seam."""
    rng = np.random.default_rng(7)
    n = int(seconds * RATE)
    f = np.fft.rfftfreq(n, 1 / RATE)
    t = np.arange(n) / RATE

    def wander(per_second, depth):
        # Smooth random motion that wraps round the loop.
        k = int(seconds * per_second)
        v = rng.normal(size=k)
        return depth * np.interp(t, np.linspace(0, seconds, k + 1), np.append(v, v[0]), period=seconds)

    # Roar: noise shaped to peak near 250 Hz, falling ~1/f above, tight below 35 Hz.
    shape = (f / 35) ** 2 / (1 + (f / 35) ** 2) / np.sqrt(1 + (f / 250) ** 2.2) / (1 + (f / 7000) ** 4)
    roar = np.fft.irfft(np.fft.rfft(rng.normal(size=n)) * shape, n=n)
    roar /= np.std(roar)
    flutter = 1 + wander(9, 0.12)
    # Crackle: shock fronts as short positive bumps (~0.2 ms), about 180 a second, bunched into
    # bursts. Narrow, even amplitudes give the skew without a few big snaps (a fire, not a rocket).
    density = np.exp(wander(5, 0.7))
    count = int(seconds * 180)
    at = rng.choice(n, count, p=density / density.sum())
    pulses = np.zeros(n)
    np.add.at(pulses, at, rng.gamma(4.0, 0.25, count))
    w = 0.0002 * RATE
    k = np.arange(int(6 * w))
    bump = np.exp(-0.5 * ((k - 3 * w) / w) ** 2)
    crackle = np.real(np.fft.ifft(np.fft.fft(pulses) * np.fft.fft(bump, n)))
    crackle = loop_filter(crackle, 60)
    crackle /= np.std(crackle)
    x = roar * flutter + 1.4 * crackle
    x = np.fft.irfft(np.fft.rfft(x) / np.sqrt(1 + (f / 4500) ** 4), n=n)  # through the cabin wall
    return x - np.mean(x)


def radio_squelch():
    """A message coming in on the capsule radio: the squelch opens with a breath of FM hiss as the
    carrier comes up, the 2525 Hz intro tone (like Apollo's Quindar tones), then the carrier drops
    and the squelch tail: a burst of bright hiss cut off dead."""
    rng = np.random.default_rng(11)

    def fm_hiss(dur):
        # FM receiver noise rises with frequency, then the voice filter takes 300 Hz - 3 kHz.
        m = int(dur * RATE)
        f = np.fft.rfftfreq(m, 1 / RATE)
        g = (f / 3000) / np.sqrt(1 + (300 / (f + 1)) ** 6) / np.sqrt(1 + (f / 3100) ** 20)
        return np.fft.irfft(np.fft.rfft(rng.normal(size=m)) * g, n=m) / 0.2

    def ramp(m, a, b):
        return np.minimum(1, np.minimum(np.arange(m) / max(1, a), (m - np.arange(m)) / max(1, b)))

    opening = fm_hiss(0.07)
    m = len(opening)
    opening *= np.exp(-np.arange(m) / (0.018 * RATE)) * ramp(m, 0.002 * RATE, 0.002 * RATE) * 0.4  # carrier quieting it
    def carrier(dur):
        # Carrier up, channel quiet: a faint hum and what's left of the hiss.
        m = int(dur * RATE)
        return 0.01 * np.sin(2 * np.pi * 120 * np.arange(m) / RATE) + 0.004 * fm_hiss(dur)[:m]

    hum = carrier(0.1)
    tone_len = int(0.25 * RATE)
    tone = np.sin(2 * np.pi * 2525 * np.arange(tone_len) / RATE) * ramp(tone_len, 0.004 * RATE, 0.004 * RATE) * 0.32
    tone += 0.004 * fm_hiss(0.25)[:tone_len]
    gap = carrier(0.12)
    tail = fm_hiss(0.16) * 0.24
    tail *= np.minimum(1, np.arange(len(tail)) / (0.003 * RATE))
    tail[-int(0.0015 * RATE):] *= np.linspace(1, 0, int(0.0015 * RATE))  # cut off dead
    x = np.concatenate([opening, hum, tone, gap, tail, np.zeros(int(0.04 * RATE))])
    return np.tanh(1.6 * x) / 1.6


# ---------------------------------------------------------------------------------------------
# Plasma waves. Each is drawn as a spectrogram (power over time and frequency) and turned into
# sound as noise shaped by it: random phase and amplitude in every cell give the same speckle and
# grain the real spacecraft recordings have, and the time axis wraps so every sound loops cleanly.
# Shapes, rates and levels follow the Van Allen Probes, Juno, Cassini and Parker Solar Probe
# recordings (studied as spectrograms, nothing taken from them).

NFFT = 2048
HOP = 256
FREQS = np.fft.rfftfreq(NFFT, 1 / RATE)


def loop_filter(x, lo=0.0, hi=None):
    """Band limit a looping sound in the frequency domain (wraps cleanly, unlike a filter run)."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    gain = np.ones_like(f)
    if hi:
        gain /= np.sqrt(1 + (f / hi) ** 16)
    if lo:
        gain *= 1 / np.sqrt(1 + (f / lo + 1e-9) ** -8)
    return np.fft.irfft(spec * gain, n=len(x))


def frames(seconds):
    return int(seconds * RATE / HOP)


def times(n):
    return np.arange(n) * HOP / RATE


def tf_noise(power, rng, grain=1.0, coherent=False):
    """Noise with this spectrogram (frames x bins), overlap-added round a loop. grain 1 is plain
    noise (full speckle, like a waveform recording); lower keeps cell levels closer to as drawn.
    coherent: every frequency keeps a continuous phase from frame to frame (each slightly off its
    bin so nothing repeats), the smooth texture of audio rebuilt from measured spectra (Juno)."""
    n = power.shape[0]
    length = n * HOP
    if coherent:
        offset = rng.uniform(-0.5, 0.5, len(FREQS)) * RATE / NFFT
        start = rng.uniform(0, 2 * np.pi, len(FREQS))
        # Advance per hop; whole turns round the loop so the phase meets itself at the seam.
        turns = np.round((FREQS + offset) * length / RATE)
        step = 2 * np.pi * turns / n
        z = np.exp(1j * (start[None, :] + np.arange(n)[:, None] * step[None, :]))
    else:
        z = rng.normal(size=power.shape) + 1j * rng.normal(size=power.shape)
        mag = np.abs(z) / np.sqrt(2)
        z = z / (np.abs(z) + 1e-12) * mag ** grain / np.sqrt(np.mean(mag ** (2 * grain)))
    cells = np.sqrt(np.maximum(power, 0)) * z
    window = np.hanning(NFFT + 1)[:-1]
    grains = np.fft.irfft(cells, n=NFFT, axis=1) * window
    out = np.zeros(length)
    for i in range(n):
        idx = (i * HOP + np.arange(NFFT)) % length
        out[idx] += grains[i]
    return out


def smooth_noise(n, rng, cell):
    """Slow random wobble, period about {@code cell} frames, wrapping round the loop, mean 0 sd 1."""
    k = max(2, n // cell)
    knots = rng.normal(size=k)
    x = np.arange(n) / n * k
    i = np.floor(x).astype(int)
    u = x - i
    u = u * u * (3 - 2 * u)
    out = knots[i % k] * (1 - u) + knots[(i + 1) % k] * u
    return out / (out.std() + 1e-9)


def band(lo, hi, sharp_lo=4, sharp_hi=12):
    """Spectral shape: passes lo..hi Hz, with soft (low) and steep (high) edges."""
    f = FREQS + 1e-6
    return (f / lo) ** sharp_lo / (1 + (f / lo) ** sharp_lo) / (1 + (f / hi) ** sharp_hi)


def ridge(power, t0, dur, freq_of, width, amp, env, n):
    """Adds a narrow line following freq_of(tau) for dur seconds from t0 (wrapping)."""
    f0 = int(t0 * RATE / HOP)
    m = max(1, int(dur * RATE / HOP))
    tau = np.arange(m) * HOP / RATE
    fc = freq_of(tau)
    # A fast sweep crosses many bins in one frame: widen it there so it stays a continuous line.
    slope = np.abs(np.gradient(fc)) if m > 1 else np.zeros(1)
    w = np.sqrt(width ** 2 + (0.6 * slope) ** 2)
    shape = np.exp(-((FREQS[None, :] - fc[:, None]) / w[:, None]) ** 2) * (width / w)[:, None]
    rows = (f0 + np.arange(m)) % n
    power[rows] += amp * env(tau / dur)[:, None] * shape


def floor_noise(n, level, knee=3000):
    """The receiver's own hiss over the whole band, falling off above the knee."""
    return np.full((n, len(FREQS)), level) / (1 + (FREQS / knee) ** 2)[None, :]


def quirks(power, rng, spikes, fades, top=9000):
    """What makes the real recordings untidy: parts of the spectrum fading in and out on their own
    (each band follows its own slow swell, now and then rising out of nothing over a second or two),
    and spikes: single broadband instants from the spacecraft, some only reaching part way up."""
    n = power.shape[0]
    edges = np.geomspace(150, top, 13)
    gain = np.ones_like(power)
    for lo, hi in zip(edges[:-1], edges[1:]):
        g = 10 ** (fades * smooth_noise(n, rng, rng.integers(120, 500)))
        if rng.random() < 0.5:  # a band that dies away and fades back in
            c = rng.integers(0, n)
            w = rng.integers(80, 400)
            d = (np.arange(n) - c + n // 2) % n - n // 2
            g *= 1 - 0.9 * np.exp(-(d / w) ** 4)
        sel = (FREQS >= lo) & (FREQS < hi)
        gain[:, sel] *= g[:, None]
    # Smooth the band edges so the fades blend into each other.
    from scipy.ndimage import uniform_filter1d
    power = power * uniform_filter1d(gain, 25, axis=1, mode='nearest')
    level = np.median(power)
    for _ in range(int(spikes * n * HOP / RATE)):
        i = rng.integers(0, n)
        reach = top if rng.random() < 0.6 else rng.uniform(800, top)
        power[i] += level * rng.lognormal(np.log(6), 0.8) * band(80, reach, 2, 12)
    return power


def plasma_earth(seconds=20.0):
    """Earth's chorus (Van Allen Probes): a strong hiss band whose top fades out around 1.1-1.5 kHz,
    and a steady procession of rising tones from ~1.1 kHz, each a second or so long, climbing
    roughly 1 kHz a second, overlapping, each with a faint fuzzy skirt below; the odd broadband tick."""
    rng = np.random.default_rng(31)
    n = frames(seconds)
    power = floor_noise(n, 0.0008, 2500)
    hiss = band(120, 960, 3, 16) * (1 + 0.8 / (1 + (FREQS / 400) ** 2)) + 0.04 * band(900, 1500, 6, 10)
    swell = 10 ** (0.1 * smooth_noise(n, rng, 160))
    power += hiss[None, :] * swell[:, None]
    busy = 0.55 + 0.45 * np.tanh(1.4 * smooth_noise(n, rng, 600) + 0.6)
    tt = 0.0
    while tt < seconds:
        rate_now = busy[int(tt / seconds * n) % n]
        tt += rng.exponential(0.25 + 0.9 * (1 - rate_now)) + 0.12
        f_start = rng.uniform(1000, 1450)
        dur = rng.uniform(0.75, 1.5)
        rise = rng.uniform(650, 1300)
        bend = rng.uniform(-0.3, 0.1)
        amp = rng.lognormal(np.log(1.3), 0.5) * (0.4 + rate_now)
        sub = rng.uniform(5, 11)
        phase = rng.uniform(0, 6.3)
        env = lambda u, sub=sub, phase=phase, dur=dur: (np.minimum(1, u / 0.2) * np.minimum(1, (1 - u) / 0.3) ** 1.3
                                                       * (0.85 + 0.15 * np.sin(2 * np.pi * sub * u * dur + phase)))
        path = lambda tau, f=f_start, r=rise, b=bend, d=dur: f + r * (tau / d) * (1 + b * (tau / d - 1))
        ridge(power, tt, dur, path, rng.uniform(14, 24), amp, env, n)
        ridge(power, tt, dur, lambda tau, p=path: p(tau) - 70, 70, amp * 0.08, env, n)  # the skirt
        if rng.random() < 0.18:
            off = rng.uniform(80, 180)
            ridge(power, tt + rng.uniform(0.0, 0.1), dur * 0.7, lambda tau, p=path, o=off: p(tau) + o, 16, amp * 0.35, env, n)
    power = quirks(power, rng, spikes=0.3, fades=0.15)
    x = tf_noise(power, rng)
    return loop_filter(x, 40, 9000)


def blocks(n, rng, lo_ms, hi_ms):
    """Splits the loop into frame blocks of random length; returns each frame's block index."""
    idx = np.zeros(n, int)
    i = k = 0
    while i < n:
        m = max(1, int(rng.uniform(lo_ms, hi_ms) / 1000 * RATE / HOP))
        idx[i:i + m] = k
        i += m
        k += 1
    return idx, k


def plasma_jupiter(seconds=20.0):
    """Inside Jupiter's magnetosphere (Juno): a smooth dense roar up to a firm edge near 2.45 kHz,
    a quiet gap above it; in the outer region a thin steady tone near 2 kHz with a brighter band
    over it, and the floor stepping as the spacecraft crosses between regions."""
    rng = np.random.default_rng(32)
    n = frames(seconds)
    power = floor_noise(n, 0.0015, 2000)
    inner = band(80, 2380, 2, 40)
    outer = band(80, 2380, 2, 40) * (0.55 + 0.45 / (1 + np.minimum(1e12, ((FREQS + 1.0) / 2020) ** -40)))
    region = np.zeros(n)
    edges = np.sort(rng.choice(np.arange(n), 4, replace=False))
    for a, b in zip(edges[::2], edges[1::2]):
        region[a:b] = 1
    power += region[:, None] * outer[None, :] * 1.1 + (1 - region)[:, None] * inner[None, :] * 0.8
    blk, k = blocks(n, rng, 40, 90)
    flutter = 10 ** (0.05 * rng.normal(size=(k, 8)))
    groups = np.minimum(7, (FREQS / 3000 * 8).astype(int))
    power *= flutter[blk][:, groups]
    power *= (10 ** (0.08 * smooth_noise(n, rng, 200)))[:, None]
    line = 2020 + 12 * smooth_noise(n, rng, 300) + np.cumsum(np.where(rng.random(n) < 0.004, rng.choice([-18, 18], n), 0))
    power += region[:, None] * 2.0 * np.exp(-((FREQS[None, :] - line[:, None]) / 12) ** 2)
    # Faint flicker just over the edge.
    power = quirks(power, rng, spikes=0.5, fades=0.07)
    x = tf_noise(power, rng)
    return loop_filter(x, 40, 3000)


def plasma_ganymede(seconds=24.0):
    """Ganymede (Juno flyby): the 'dial-up' mosaic: in short blocks the spectrum is either nearly
    empty or full of short steady tones that hold for a few blocks and step about; a bright band
    near 2.75 kHz, a busy low band under 800 Hz, the odd broadband flash. Halfway round the loop it
    shifts into a busier, higher region."""
    rng = np.random.default_rng(33)
    n = frames(seconds)
    power = floor_noise(n, 0.0004, 2500)
    blk, k = blocks(n, rng, 50, 110)
    tones = []  # [freq, blocks left, level]
    col = np.zeros((k, len(FREQS)))
    for b in range(k):
        later = b > k / 2
        tones = [[f, left - 1, a] for f, left, a in tones if left > 1]
        while len(tones) < (9 if later else 6):
            tones.append([rng.uniform(850 + (150 if later else 0), 2550), rng.integers(1, 6), rng.uniform(0.4, 1.4)])
        for t_ in tones:
            if rng.random() < 0.2:
                t_[0] += rng.choice([-1, 1]) * rng.uniform(40, 120)  # a step
        dark = rng.random() < (0.3 if later else 0.42)
        if dark:
            # Empty, or empty only over part of the range.
            lo, hi = (850, 2600) if rng.random() < 0.6 else sorted(rng.uniform(850, 2600, 2))
            col[b] = 0.25 * band(850, 2600, 6, 20) * (1 - band(lo, hi, 30, 30) / band(lo, hi, 30, 30).max())
            col[b] = np.maximum(col[b], 0.004 * band(850, 2600, 6, 20))
        else:
            # Full: combed with faint stripes, and the held tones bright on top.
            comb = np.zeros(len(FREQS))
            for f in np.arange(850, 2600, rng.uniform(45, 75)):
                comb += rng.uniform(0.0, 0.25) * np.exp(-((FREQS - f) / 9) ** 2)
            col[b] = 0.05 * band(850, 2600, 6, 20) + comb
            for f, _, a in tones:
                col[b] += 2.2 * a * np.exp(-((FREQS - f) / 18) ** 2)
        col[b] += (1.5 + 1.0 * rng.random()) * band(2620, 2900, 30, 30)
        col[b] += (0.8 + 1.2 * rng.random()) * band(60, 820, 2, 12)
        if rng.random() < 0.05:
            col[b] += 0.3 * band(200, 7000, 2, 6)
    power += col[blk]
    power = quirks(power, rng, spikes=0.8, fades=0.3)
    x = tf_noise(power, rng)
    return loop_filter(x, 40, 3200)


def plasma_saturn(seconds=24.0):
    """Saturn's kilometric radio (Cassini), drawn the way its spectrogram looks: several broad
    bands of diffuse noise stacked up the spectrum, each a few hundred Hz tall, drifting and
    sloping slowly, swelling and fading over seconds, streaky along their length; faint thin
    lines in the quiet parts; regular fine vertical striations through stretches of it; and now
    and then a band that rises steeply across everything."""
    rng = np.random.default_rng(34)
    n = frames(seconds)
    power = floor_noise(n, 0.002, 1500)
    for k in range(5):
        centre = rng.uniform(250, 2200) + 260 * smooth_noise(n, rng, rng.integers(300, 900)) + np.linspace(0, rng.uniform(-300, 300), n)
        width = rng.uniform(110, 260) * 10 ** (0.12 * smooth_noise(n, rng, 400))
        level = np.clip(smooth_noise(n, rng, rng.integers(250, 700)) + rng.uniform(-0.2, 0.6), 0, None) ** 1.3
        prof = np.exp(-np.abs((FREQS[None, :] - centre[:, None]) / width[:, None]) ** 2.5)
        power += rng.uniform(1.0, 2.2) * level[:, None] * prof
    # Streaks: each frequency's level holds for half a second or so, giving the lengthwise grain.
    from scipy.ndimage import zoom
    streak = zoom(rng.lognormal(0, 0.35, (max(4, n // 60), len(FREQS))), (n / max(4, n // 60), 1), order=1)[:n]
    power *= streak
    for _ in range(int(seconds * 1.2)):  # thin faint lines
        f0 = rng.uniform(200, 2400)
        ridge(power, rng.uniform(0, seconds), rng.uniform(0.3, 1.5), lambda tau, f0=f0: np.full_like(tau, f0), 10,
              rng.uniform(0.1, 0.4), lambda u: np.ones_like(u), n)
    for _ in range(int(seconds / 9)):
        t0 = rng.uniform(0, seconds)
        dur = rng.uniform(1.0, 1.8)
        f0 = rng.uniform(400, 800)
        ridge(power, t0, dur, lambda tau, f0=f0, d=dur: f0 + (2600 - f0) * tau / d, 80, rng.uniform(1.2, 2.0),
              lambda u: np.minimum(1, u / 0.1) * np.minimum(1, (1 - u) / 0.15), n)
    # Striations: regular, about 10 a second, in some stretches.
    gate = np.clip(smooth_noise(n, rng, 500), 0, None)
    period = 0.1 * RATE / HOP
    k = 0.0
    while k < n:
        i = int(k)
        power[i] += 0.4 * gate[i] * band(100, 2450, 2, 40)
        k += period * rng.uniform(0.85, 1.15)
    x = tf_noise(power, rng)
    return loop_filter(x, 40, 2450)


def plasma_solar_wind(seconds=24.0):
    """The solar wind (Parker Solar Probe): a low band under ~1.2 kHz laced with faint steady
    lines, faint narrow gusts that fan upward, clusters of short bright chirps between 1 and
    5 kHz, grainy hiss over it all, and the static of dust grains hitting the spacecraft."""
    rng = np.random.default_rng(35)
    n = frames(seconds)
    power = floor_noise(n, 0.004, 2500)
    power += band(40, 700, 2, 6)[None, :] * 0.25 * (10 ** (0.12 * smooth_noise(n, rng, 250)))[:, None]
    # Lines at uneven spacing, each wavering a little and coming and going on its own.
    for f0 in sorted(rng.uniform(250, 2700, 11)):
        line = f0 + rng.uniform(2, 9) * smooth_noise(n, rng, rng.integers(150, 500))
        there = np.clip(0.6 + smooth_noise(n, rng, rng.integers(200, 600)), 0, None)
        power += (rng.uniform(0.2, 0.7) / (1 + f0 / 1500) * there)[:, None] * np.exp(-((FREQS[None, :] - line[:, None]) / 7) ** 2)
    tt = 0.0
    idx = np.arange(n)
    while tt < seconds:
        tt += rng.uniform(1.5, 3.5)
        c = tt * RATE / HOP
        width = (rng.uniform(0.25, 0.6) - 0.2 * np.clip(FREQS / 4500, 0, 1)) * RATE / HOP
        lean = rng.uniform(-0.15, 0.15) * RATE / HOP * np.clip(FREQS / 4500, 0, 1)
        d = (idx[:, None] - c - lean[None, :] + n / 2) % n - n / 2
        power += rng.uniform(0.015, 0.05) * np.exp(-(d / width[None, :]) ** 2) * band(300, rng.uniform(3000, 5200), 2, 5)[None, :]
    for _ in range(int(seconds / 12)):
        c0 = rng.uniform(0, seconds)
        for _ in range(rng.integers(5, 10)):
            t0 = c0 + rng.uniform(0, 1.6)
            lo, hi = rng.uniform(700, 1600), rng.uniform(2800, 5200)
            dur = rng.uniform(0.04, 0.11)
            slant = rng.uniform(-0.5, 0.5)
            ridge(power, t0, dur, lambda tau, lo=lo, hi=hi, d=dur, sl=slant: (lo + hi) / 2 + sl * (hi - lo) * (tau / d - 0.5),
                  (hi - lo) / 2, rng.uniform(0.3, 0.9), lambda u: np.sin(np.pi * np.clip(u, 0, 1)), n)
    power = quirks(power, rng, spikes=0.1, fades=0.15)
    x = tf_noise(power, rng)
    hits = np.zeros(len(x))
    for _ in range(int(seconds * 1.3)):
        hits[rng.integers(0, len(x))] += rng.choice([-1, 1]) * rng.lognormal(0, 0.6)
    ring = np.exp(-np.arange(300) / 40.0) * np.sin(np.arange(300) * 0.9)
    rung = np.real(np.fft.ifft(np.fft.fft(hits) * np.fft.fft(ring, len(hits))))
    x = x / np.std(x) + 0.6 * loop_filter(rung, 500, 8000)
    return loop_filter(x, 40, 9000)


def geiger_click(variant=0):
    """One count of a handheld radiation counter: the tube's pulse kicks the little speaker cone
    (a sharp one-sided push), which then rings at a few modes between 3 and 4 kHz plus a faint
    8 kHz edge for a few milliseconds, over a short 550 Hz thump. Decay and balance follow real
    counters (-20 dB in about 3-6 ms). Each variant shifts the modes a little so a run of clicks
    isn't one sample repeating."""
    rng = np.random.default_rng(26 + variant)
    n = int(0.025 * RATE)
    t = np.arange(n) / RATE
    x = np.zeros(n)
    for hz, decay, amp in ((550, 0.0018, 0.5), (2350, 0.0015, 0.4), (3050, 0.0022, 1.0), (3400, 0.0016, 0.8),
                           (3950, 0.0026, 0.75), (6300, 0.0009, 1.1), (6900, 0.0008, 0.9), (8100, 0.0008, 0.9)):
        hz *= rng.uniform(0.96, 1.04)
        x += amp * rng.uniform(0.8, 1.2) * np.sin(2 * np.pi * hz * t + rng.uniform(0, 6.3)) * np.exp(-t / (decay * rng.uniform(0.85, 1.15)))
    x *= np.minimum(1, t / 0.0001)
    x += 1.2 * (np.exp(-t / 0.00016) - np.exp(-t / 0.00007))  # the cone's first push
    x += 0.06 * rng.normal(size=n) * np.exp(-t / 0.0004)
    fade = int(0.004 * RATE)
    x[-fade:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, fade))
    return x


def level(x, rms_db, ceiling_db=-3.0):
    """Sets the loudness to rms_db, then rounds off any peaks above the ceiling (spikes, ticks)."""
    x = x / np.sqrt(np.mean(x ** 2)) * 10 ** (rms_db / 20)
    c = 10 ** (ceiling_db / 20)
    knee = 0.7 * c
    over = np.abs(x) > knee
    x[over] = np.sign(x[over]) * (knee + (c - knee) * np.tanh((np.abs(x[over]) - knee) / (c - knee)))
    return x


def write(name, x, peak_db=-3.0, rms_db=None):
    if rms_db is not None:
        x = level(x, rms_db, peak_db)
    x = x / np.max(np.abs(x)) * 10 ** (peak_db / 20) if rms_db is None else x
    peak = 20 * np.log10(np.max(np.abs(x)))
    rms = 20 * np.log10(np.sqrt(np.mean(x ** 2)))
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + '.wav')
        wavfile.write(wav, RATE, (x * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '4',
                        os.path.join(OUT, name + '.ogg')], check=True)
    print(f'{name:16s} {len(x) / RATE:5.2f} s  peak {peak:6.1f} dBFS  rms {rms:6.1f} dBFS')


# --- Worlds without a famous recording: drawn from what is known of each place. ---------------

def chirp_train(power, rng, n, seconds, rate, f_lo, f_hi, dur, rise, width, amp, falling=False):
    """Scatter rising (or falling) tones: rate per second, start f_lo..f_hi, sweep 'rise' Hz."""
    tt = 0.0
    while tt < seconds:
        tt += rng.exponential(1 / rate)
        f0 = rng.uniform(f_lo, f_hi)
        d = rng.uniform(*dur)
        r = rng.uniform(*rise) * (-1 if falling else 1)
        bend = rng.uniform(-0.3, 0.2)
        ridge(power, tt, d, lambda tau, f0=f0, r=r, d=d, b=bend: f0 + r * (tau / d) * (1 + b * (tau / d - 1)),
              rng.uniform(*width), rng.lognormal(np.log(amp), 0.5),
              lambda u: np.minimum(1, u / 0.15) * np.minimum(1, (1 - u) / 0.4) ** 1.3, n)


def whistler(power, rng, n, t0, f_top, f_bottom, dur, amp, width=20):
    """A lightning whistler: a tone falling fast at first, then slower (f ~ 1/t^2 dispersion)."""
    ridge(power, t0, dur, lambda tau, ft=f_top, fb=f_bottom, d=dur: fb + (ft - fb) / (1 + 12 * tau / d) ** 2,
          width, amp, lambda u: np.minimum(1, u / 0.03) * (1 - u) ** 1.5, n)


def plasma_mercury(seconds=20.0):
    """Mercury's tiny magnetosphere: nothing settles; every few seconds a substorm floods the band
    with bursty noise and rising blips, then it drains away again."""
    rng = np.random.default_rng(41)
    n = frames(seconds)
    power = floor_noise(n, 0.002, 2500)
    idx = np.arange(n)
    tt = 0.0
    while tt < seconds:
        tt += rng.uniform(2.0, 4.0)
        c = tt * RATE / HOP
        d = (idx - c + n / 2) % n - n / 2
        burst = np.where(d >= 0, np.exp(-d / (rng.uniform(0.6, 1.4) * RATE / HOP)), np.exp(d / (0.08 * RATE / HOP)))
        lo, hi = rng.uniform(200, 600), rng.uniform(2500, 5000)
        power += rng.uniform(0.6, 1.4) * burst[:, None] * band(lo, hi, 3, 8)[None, :]
    chirp_train(power, rng, n, seconds, 3.0, 800, 2200, (0.08, 0.25), (300, 900), (20, 40), 0.8)
    power = quirks(power, rng, spikes=0.5, fades=0.25)
    return loop_filter(tf_noise(power, rng), 40, 8000)


def plasma_venus(seconds=24.0):
    """Venus: no field of its own, just the solar wind piling up against its air. A low hiss, and
    every so often a lightning whistler falling out of the top of the band."""
    rng = np.random.default_rng(42)
    n = frames(seconds)
    power = floor_noise(n, 0.004, 2000)
    power += 0.35 * band(80, 900, 2, 5)[None, :] * (10 ** (0.15 * smooth_noise(n, rng, 300)))[:, None]
    tt = 0.0
    while tt < seconds:
        tt += rng.exponential(2.2)
        for k in range(rng.integers(1, 4)):  # often in quick groups
            whistler(power, rng, n, tt + 0.12 * k, rng.uniform(5000, 8000), rng.uniform(300, 700), rng.uniform(0.5, 1.1),
                     rng.uniform(0.6, 1.6), rng.uniform(25, 60))
    power = quirks(power, rng, spikes=0.3, fades=0.2)
    return loop_filter(tf_noise(power, rng), 40, 9000)


def plasma_moon(seconds=24.0):
    """The Moon: in the plasma wake behind it, almost silence. A thin hiss, faint electrostatic
    tones that come and go, the occasional tick."""
    rng = np.random.default_rng(43)
    n = frames(seconds)
    power = floor_noise(n, 0.006, 3000)
    for f0 in rng.uniform(1500, 6000, 4):
        line = f0 + 40 * smooth_noise(n, rng, 400)
        there = np.clip(smooth_noise(n, rng, 300) - 0.3, 0, None) ** 2
        power += 0.3 * there[:, None] * np.exp(-((FREQS[None, :] - line[:, None]) / 30) ** 2)
    power = quirks(power, rng, spikes=0.4, fades=0.3)
    return loop_filter(tf_noise(power, rng), 40, 9000)


def plasma_mars(seconds=24.0):
    """Mars: the solar wind meets the planet's patchy crustal fields and its thin ionosphere. Low
    warbling tones (proton cyclotron waves) wandering over the wind's hiss."""
    rng = np.random.default_rng(44)
    n = frames(seconds)
    power = floor_noise(n, 0.006, 2200)
    power += 0.3 * band(60, 900, 2, 6)[None, :]
    for k in range(3):
        base = rng.uniform(250, 700)
        f = base * (1 + 0.15 * smooth_noise(n, rng, 500)) + 20 * np.sin(2 * np.pi * rng.uniform(1.5, 3.5) * times(n))
        there = np.clip(smooth_noise(n, rng, 350) + 0.6, 0, None)
        for h, a in ((1, 1.0), (2, 0.25)):
            power += rng.uniform(0.8, 1.6) * a * there[:, None] * np.exp(-((FREQS[None, :] - h * f[:, None]) / 18) ** 2)
    power = quirks(power, rng, spikes=0.3, fades=0.25)
    return loop_filter(tf_noise(power, rng), 40, 8000)


def plasma_io(seconds=20.0):
    """Io: inside the flux tube that ties it to Jupiter, the decametric radio storm: long waves of
    noise that wash in and out like surf, and bursts of popcorn crackle."""
    rng = np.random.default_rng(45)
    n = frames(seconds)
    power = floor_noise(n, 0.004, 2500)
    idx = np.arange(n)
    tt = 0.0
    while tt < seconds:  # L-bursts: surf
        tt += rng.uniform(0.7, 2.0)
        c = tt * RATE / HOP
        d = (idx - c + n / 2) % n - n / 2
        w = rng.uniform(0.3, 0.8) * RATE / HOP
        sweep = rng.uniform(-200, 200)
        lo = rng.uniform(150, 500)
        for i in np.nonzero(np.abs(d) < 3 * w)[0]:
            power[i] += rng.uniform(0.5, 1.0) * np.exp(-(d[i] / w) ** 2) * band(lo + sweep * d[i] / w, 3500, 2, 6)
    x = tf_noise(quirks(power, rng, spikes=0.3, fades=0.2), rng)
    # S-bursts: tight clusters of very short clicks, each a tiny falling chirp.
    pops = np.zeros(len(x))
    t = 0.0
    while t < seconds:
        t += rng.exponential(1.4)
        for _ in range(rng.integers(8, 40)):
            i = int((t + rng.exponential(0.12)) * RATE) % len(x)
            m = int(0.004 * RATE)
            k = np.arange(m)
            f0 = rng.uniform(1500, 3500)
            chirp = np.sin(2 * np.pi * np.cumsum(f0 - 0.6 * f0 * k / m) / RATE) * np.hanning(m)
            pops[(i + k) % len(x)] += chirp * rng.lognormal(0, 0.4)
    x = x / np.std(x) + 0.7 * pops / (np.std(pops) + 1e-9) * 0.5
    return loop_filter(x, 40, 6000)


def plasma_europa(seconds=24.0):
    """Europa (Juno flyby): Juno's blocky texture again but softer and lower than Ganymede, a band
    up to ~2.7 kHz with short held tones drifting in it and patches dropping out."""
    rng = np.random.default_rng(46)
    n = frames(seconds)
    power = floor_noise(n, 0.001, 2500)
    blk, k = blocks(n, rng, 70, 160)
    col = np.zeros((k, len(FREQS)))
    tones = []
    for b in range(k):
        tones = [[f + rng.normal(0, 25), left - 1, a] for f, left, a in tones if left > 1]
        while len(tones) < 5:
            tones.append([rng.uniform(1300, 2600), rng.integers(2, 8), rng.uniform(0.4, 1.2)])
        col[b] = 0.5 * band(100, 2650, 2, 30) * rng.uniform(0.5, 1.2)
        if rng.random() < 0.25:
            lo, hi = sorted(rng.uniform(1000, 2650, 2))
            col[b] *= 1 - 0.9 * band(lo, hi, 20, 20) / band(lo, hi, 20, 20).max()
        for f, _, a in tones:
            col[b] += 1.5 * a * np.exp(-((FREQS - f) / 25) ** 2)
    power += col[blk]
    power = quirks(power, rng, spikes=0.6, fades=0.15)
    return loop_filter(tf_noise(power, rng), 40, 3000)


def plasma_callisto(seconds=20.0):
    """Callisto: far out in Jupiter's magnetosphere. The Jovian roar, distant and thinner, swelling
    slowly as the plasma sheet sweeps over it every few seconds."""
    rng = np.random.default_rng(47)
    n = frames(seconds)
    power = floor_noise(n, 0.002, 2000)
    sheet = 0.25 + 0.75 * (0.5 + 0.5 * np.sin(2 * np.pi * times(n) / (seconds / 3))) ** 3
    power += 0.6 * sheet[:, None] * band(120, 2200, 2, 30)[None, :]
    chirp_train(power, rng, n, seconds, 0.6, 600, 1400, (0.4, 1.0), (400, 900), (15, 25), 0.5)
    power = quirks(power, rng, spikes=0.3, fades=0.15)
    return loop_filter(tf_noise(power, rng), 40, 3000)


def plasma_titan(seconds=24.0):
    """Titan: drifting through Saturn's magnetosphere wrapped in its own ionosphere. Broad soft noise
    that swells and sinks over seconds, Saturn's radio faint in the background."""
    rng = np.random.default_rng(48)
    n = frames(seconds)
    power = floor_noise(n, 0.003, 1800)
    for k in range(3):
        centre = rng.uniform(300, 1800) + 300 * smooth_noise(n, rng, 600)
        level = np.clip(smooth_noise(n, rng, 400) + 0.5, 0, None) ** 1.5
        power += 0.8 * level[:, None] * np.exp(-((FREQS[None, :] - centre[:, None]) / rng.uniform(300, 600)) ** 2)
    power += 0.2 * band(60, 500, 2, 6)[None, :]
    power = quirks(power, rng, spikes=0.2, fades=0.25)
    return loop_filter(tf_noise(power, rng), 40, 4000)


def plasma_enceladus(seconds=20.0):
    """Enceladus: inside the plume, ice grains pelting the antennas like rain on a tin roof, over a
    steady tone from ions spiralling in Saturn's field."""
    rng = np.random.default_rng(49)
    n = frames(seconds)
    power = floor_noise(n, 0.004, 2500)
    line = 900 + 15 * smooth_noise(n, rng, 300)
    power += 1.0 * np.exp(-((FREQS[None, :] - line[:, None]) / 12) ** 2) + 0.3 * np.exp(-((FREQS[None, :] - 2 * line[:, None]) / 15) ** 2)
    x = tf_noise(quirks(power, rng, spikes=0.2, fades=0.15), rng)
    density = 40 + 30 * np.sin(2 * np.pi * np.arange(len(x)) / len(x) * 2)
    hits = np.zeros(len(x))
    i = 0
    while i < len(x):
        i += int(rng.exponential(RATE / density[i % len(x)]))
        hits[i % len(x)] += rng.choice([-1, 1]) * rng.lognormal(0, 0.7)
    ring = np.exp(-np.arange(220) / 25.0) * np.sin(np.arange(220) * rng.uniform(0.6, 1.1))
    rung = np.real(np.fft.ifft(np.fft.fft(hits) * np.fft.fft(ring, len(hits))))
    x = x / np.std(x) + 0.3 * loop_filter(rung, 300, 9000) / (np.std(rung) + 1e-9)
    return loop_filter(x, 40, 9000)


def plasma_uranus(seconds=24.0):
    """Uranus (as Voyager 2 heard it): a lopsided, tumbling field. Slow chorus that sweeps the band
    as the magnetosphere wobbles round, with whistling hiss underneath."""
    rng = np.random.default_rng(50)
    n = frames(seconds)
    power = floor_noise(n, 0.002, 2000)
    tilt = 0.5 + 0.5 * np.sin(2 * np.pi * times(n) / seconds)
    hiss_top = 500 + 500 * tilt
    for i in range(n):
        power[i] += 0.6 * band(80, hiss_top[i], 2, 10)
    tt = 0.0
    while tt < seconds:
        tt += rng.exponential(0.6)
        lift = 500 * tilt[int(tt / seconds * n) % n]
        f0 = rng.uniform(700, 1000) + lift
        d = rng.uniform(0.8, 1.8)
        ridge(power, tt, d, lambda tau, f0=f0, d=d: f0 + rng.uniform(300, 600) * (tau / d) ** 0.7, rng.uniform(18, 30),
              rng.lognormal(np.log(0.9), 0.5), lambda u: np.minimum(1, u / 0.2) * np.minimum(1, (1 - u) / 0.4), n)
    power = quirks(power, rng, spikes=0.3, fades=0.2)
    return loop_filter(tf_noise(power, rng), 40, 6000)


def plasma_neptune(seconds=24.0):
    """Neptune (Voyager 2): smooth radio like wind, rising and falling twice a loop as the planet's
    beam swings past, and a faint narrow tone riding on it."""
    rng = np.random.default_rng(51)
    n = frames(seconds)
    power = floor_noise(n, 0.002, 2000)
    beam = (0.5 + 0.5 * np.cos(4 * np.pi * times(n) / seconds)) ** 2
    centre = 900 + 500 * beam
    for i in range(n):
        power[i] += (0.2 + 1.0 * beam[i]) * np.exp(-((FREQS - centre[i]) / 500) ** 2)
    tone = 1700 + 30 * smooth_noise(n, rng, 400)
    power += 0.4 * np.exp(-((FREQS[None, :] - tone[:, None]) / 10) ** 2)
    power = quirks(power, rng, spikes=0.2, fades=0.2)
    return loop_filter(tf_noise(power, rng), 40, 5000)


def plasma_triton(seconds=24.0):
    """Triton: deep in Neptune's magnetosphere over a thin nitrogen air. A faint aurora hiss that
    flickers, under Neptune's radio wind far away."""
    rng = np.random.default_rng(52)
    n = frames(seconds)
    power = floor_noise(n, 0.003, 2500)
    beam = (0.5 + 0.5 * np.cos(4 * np.pi * times(n) / seconds)) ** 2
    power += (0.1 + 0.3 * beam)[:, None] * np.exp(-((FREQS[None, :] - 1100) / 500) ** 2)
    blk, k = blocks(n, rng, 80, 300)
    flick = np.clip(rng.normal(0.5, 0.18, k), 0, None)
    power += 0.5 * flick[blk][:, None] * band(2000, 6000, 4, 6)[None, :]
    power = quirks(power, rng, spikes=0.2, fades=0.25)
    return loop_filter(tf_noise(power, rng), 40, 8000)


def plasma_pluto(seconds=24.0, seed=53):
    """Pluto: the solar wind out at 40 AU, thin and slow, piling up heavy ions from Pluto's escaping
    air. Near silence with a low wandering warble and rare soft blips."""
    rng = np.random.default_rng(seed)
    n = frames(seconds)
    power = floor_noise(n, 0.005, 1500)
    f = 180 * (1 + 0.3 * smooth_noise(n, rng, 600))
    there = np.clip(smooth_noise(n, rng, 500) + 0.4, 0, None)
    power += 0.9 * there[:, None] * np.exp(-((FREQS[None, :] - f[:, None]) / 15) ** 2)
    power += 0.25 * there[:, None] * np.exp(-((FREQS[None, :] - 2 * f[:, None]) / 20) ** 2)
    chirp_train(power, rng, n, seconds, 0.25, 500, 1500, (0.2, 0.6), (-200, 200), (20, 40), 0.4)
    power = quirks(power, rng, spikes=0.15, fades=0.3)
    return loop_filter(tf_noise(power, rng), 40, 6000)


def plasma_charon(seconds=24.0):
    """Charon: in Pluto's thin wake, quieter still, the warble lower and slower."""
    x = plasma_pluto(seconds, seed=54)
    return x


def plasma_acheron(seconds=24.0):
    """Acheron: nothing natural. Almost nothing at all, a thin hiss, and under it two faint tones a
    tritone apart that never settle: each slides a little, one beats slowly against a third just
    off it, and the pair swells and sinks in long, uneven breaths. Now and then, very far off, a
    single falling whistle."""
    rng = np.random.default_rng(66)
    n = frames(seconds)
    t = times(n)
    power = floor_noise(n, 0.0035, 1200)
    # Breathing: three a loop, slow in and quicker out, never quite the same depth.
    phase = (t / seconds * 3) % 1
    breath = np.where(phase < 0.65, np.sin(np.pi / 2 * phase / 0.65) ** 2, np.cos(np.pi / 2 * (phase - 0.65) / 0.35) ** 2)
    breath = 0.25 + 0.75 * breath * (1 + 0.25 * smooth_noise(n, rng, 700))
    glide = 1 + 0.025 * np.sin(2 * np.pi * t / seconds) + 0.01 * smooth_noise(n, rng, 900)
    for f, w, a in ((164.0, 10, 1.0), (164.0 * 2 ** 0.5, 10, 0.6), (164.0 * 2 ** 0.5 + 6, 10, 0.35)):
        power += a * breath[:, None] * np.exp(-((FREQS[None, :] - f * glide[:, None]) / w) ** 2)
    for _ in range(2):  # far whistles
        t0 = rng.uniform(0, seconds)
        f0 = rng.uniform(1700, 2300)
        ridge(power, t0, rng.uniform(1.6, 2.4), lambda tau, f0=f0: f0 * (1 - 0.18 * tau / 2.4), 18,
              rng.uniform(0.18, 0.28), lambda u: np.sin(np.pi * np.clip(u, 0, 1)) ** 1.5, n)
    power = quirks(power, rng, spikes=0.05, fades=0.15)
    x = tf_noise(power, rng, grain=0.6)
    return loop_filter(x, 40, 5000)


def plasma_sun(seconds=20.0):
    """Close to the Sun (Parker Solar Probe): type III radio bursts racing down the spectrum in a
    second or two, Langmuir tones flaring and fading, gusty whistler hiss low down."""
    rng = np.random.default_rng(55)
    n = frames(seconds)
    power = floor_noise(n, 0.004, 3000)
    power += 0.3 * band(60, 1200, 2, 6)[None, :] * (10 ** (0.15 * smooth_noise(n, rng, 200)))[:, None]
    tt = 0.0
    while tt < seconds:  # type III: a broad band sweeping down fast
        tt += rng.exponential(1.8)
        d = rng.uniform(0.8, 2.0)
        top = rng.uniform(5000, 8500)
        ridge(power, tt, d, lambda tau, top=top, d=d: 300 + (top - 300) / (1 + 6 * tau / d) ** 1.5, rng.uniform(150, 400),
              rng.uniform(0.6, 1.5), lambda u: np.minimum(1, u / 0.05) * (1 - u) ** 1.2, n)
    for _ in range(int(seconds * 1.5)):  # Langmuir flares
        f0 = rng.uniform(1500, 4500)
        ridge(power, rng.uniform(0, seconds), rng.uniform(0.05, 0.3), lambda tau, f0=f0: np.full_like(tau, f0), 25,
              rng.uniform(0.8, 2.5), lambda u: np.sin(np.pi * np.clip(u, 0, 1)) ** 2, n)
    power = quirks(power, rng, spikes=0.4, fades=0.2)
    return loop_filter(tf_noise(power, rng), 40, 9000)


PLASMA = {'plasma_earth': plasma_earth, 'plasma_jupiter': plasma_jupiter, 'plasma_ganymede': plasma_ganymede,
          'plasma_saturn': plasma_saturn, 'plasma_solar_wind': plasma_solar_wind,
          'plasma_mercury': plasma_mercury, 'plasma_venus': plasma_venus, 'plasma_moon': plasma_moon, 'plasma_mars': plasma_mars,
          'plasma_io': plasma_io, 'plasma_europa': plasma_europa, 'plasma_callisto': plasma_callisto, 'plasma_titan': plasma_titan,
          'plasma_enceladus': plasma_enceladus, 'plasma_uranus': plasma_uranus, 'plasma_neptune': plasma_neptune,
          'plasma_triton': plasma_triton, 'plasma_pluto': plasma_pluto, 'plasma_charon': plasma_charon, 'plasma_sun': plasma_sun,
          'plasma_acheron': plasma_acheron}


# --- Nuclear blasts ------------------------------------------------------------------------------
# What arrives is a pressure wave, not a noise: an N-wave (a jump up, a straight fall below
# ambient, a jump back), and then the same wave again and again, later and duller, off the
# ground, hills, cloud and turbulent air: that is the rolling. Close in, its front is steep
# enough to crack and splutter (nonlinear steepening), and the blast wind rushes past after it.

def n_wave(seconds, rise, amp=1.0):
    """One N-wave this long; rise is its front's rise time (s): the steeper, the sharper the crack."""
    n = max(8, int(seconds * RATE))
    x = np.linspace(1, -1, n) * amp
    x = np.concatenate([np.zeros(int(0.004 * RATE)), x, np.zeros(int(0.05 * RATE) + int(seconds * RATE * 0.3))])
    # Rise time: a moving average about that long, twice (a smooth, physical front, not a click).
    k = max(1, int(rise * RATE))
    box = np.ones(k) / k
    x = np.convolve(np.convolve(x, box, 'same'), box, 'same')
    return x


def add_at(out, x, t):
    i = int(t * RATE)
    if i >= len(out):
        return
    m = min(len(x), len(out) - i)
    out[i:i + m] += x[:m]


def arrivals(out, rng, count, t0, spread, dur, rise, amp, dull, clusters=()):
    """The wave's echoes: count copies after t0 thinning out over spread seconds, each later one
    longer, lower and duller (longer paths lose their highs); clusters are hills: bursts of them."""
    times = [(t, 1.0) for t in t0 + rng.exponential(spread, count)]
    for at, n, width in clusters:
        times += [(t, 0.5) for t in at + np.abs(rng.normal(0, width, n))]  # a hillside: many weaker returns
    for t, gain in times:
        late = t - t0
        a = amp * gain * rng.lognormal(0, 0.55) * (1 + late / 0.6) ** -0.9 * np.exp(-late / (spread * 2.5))
        d = dur * rng.uniform(0.6, 1.6) * (1 + late / 6)
        r = rise * (1 + late * 2.5) * rng.uniform(0.7, 1.5)
        w = n_wave(d, r, a)
        cut = max(90, dull / (1 + late / 1.5))
        w = lowpass(w, cut, 2)
        add_at(out, w, t)


def rumble(n, rng, env, cut):
    """Brown, uneven low rumble that follows env (no tone in it)."""
    x = np.cumsum(rng.normal(size=n))
    x = x - lowpass(x, 18, 2)  # no drift
    x = lowpass(x, cut, 3)
    wob = smooth_noise(n, rng, int(0.35 * RATE))
    x = x * np.exp(0.5 * wob)
    return x / (np.std(x) + 1e-9) * env


def crackle(n, rng, rate_env, peak, width_ms=(0.15, 0.9)):
    """Spluttering: sparse sharp mostly-positive spikes in clumps, sizes heavy-tailed (as in shock
    crackle), never a steady hiss."""
    out = np.zeros(n)
    t = 0.0
    clump = 1.0
    while True:
        i = int(t * RATE)
        if i >= n:
            break
        r = rate_env[i] * clump
        if r < 0.5:
            t += 0.005  # quiet stretch: look again a moment later
            continue
        t += rng.exponential(1 / r)
        if rng.random() < 0.08:
            clump = rng.choice([0.2, 1.0, 3.0])
        i = int(t * RATE)
        if i >= n - 200:
            break
        a = peak * min(3, rng.pareto(2.2) + 0.15) * (1 if rng.random() < 0.8 else -0.5)
        k = int(RATE * rng.uniform(*width_ms) / 1000) + 2
        shape = np.exp(-np.arange(k) / (k / 3)) - np.exp(-np.arange(k) / 1.2)
        out[i:i + k] += a * shape / shape.max()
    return out


def blast_wind(n, rng, start, length):
    """The wind behind the shock: a gusting rush, then the softer suck back the other way."""
    t = np.arange(n) / RATE
    noise = rng.normal(size=n)
    rush = lowpass(noise, 700, 3) + 0.35 * bandpass(noise, 700, 2500, 2)
    gust = np.exp(0.35 * smooth_noise(n, rng, int(0.25 * RATE)) + 0.2 * smooth_noise(n, rng, int(0.06 * RATE)))
    u = np.clip((t - start) / length, 0, None)
    env = np.where(t < start, 0, (1 - np.exp(-(t - start) / 0.03)) * np.exp(-u * 2.2))
    back = 0.2 * np.exp(-((t - start - length * 0.9) / (length * 0.35)) ** 2)
    low = lowpass(rng.normal(size=n), 260, 3)
    return rush / np.std(rush) * env * gust + low / np.std(low) * back * gust


def audible(x, peak=None, rms=None):
    """What a microphone keeps of a pressure trace (nothing below about 25 Hz), scaled to a peak or an RMS."""
    x = x - lowpass(x, 25, 2)
    if peak is not None:
        return x / (np.max(np.abs(x)) + 1e-12) * peak
    return x / (np.sqrt(np.mean(x ** 2)) + 1e-12) * rms


def nuke_far(seconds=22.0, seed=1945):
    """A blast heard from well outside the damage: a heavy boom with a dull crack on the front of
    it, the second jump a beat later, then rolling thunder off the land for a long time, getting
    duller."""
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    t0 = 0.12
    main = np.zeros(n)
    add_at(main, lowpass(n_wave(0.5, 0.0015, 1.0), 2600, 2), t0)
    echoes = np.zeros(n)
    arrivals(echoes, rng, 110, t0 + 0.06, 2.6, 0.45, 0.002, amp=1.0, dull=3200,
             clusters=[(t0 + rng.uniform(1.2, 2.0), 25, 0.35), (t0 + rng.uniform(3.5, 5.0), 30, 0.6),
                       (t0 + rng.uniform(7, 9), 25, 1.0), (t0 + rng.uniform(11, 13), 15, 1.4)])
    echoes = audible(echoes, peak=0.5)
    env = lowpass(np.abs(echoes), 1.5, 2)
    env = np.clip(env / (env.max() + 1e-9), 0, 1) * (t > t0)
    roll = audible(rumble(n, rng, env, 420), rms=0.015)
    rate = 30 * np.exp(-np.clip(t - t0, 0, None) / 1.0) * (t > t0)
    splutter = crackle(n, rng, rate, 1.0, (0.6, 2.5))
    splutter = audible(lowpass(splutter, 2500, 2), peak=0.18)
    out = audible(main, peak=1.0) + echoes + roll + splutter
    out = np.tanh(out * 1.2) / 1.2
    return out * np.clip((seconds - t) / 3.0, 0, 1)


def nuke_near(seconds=20.0, seed=1962):
    """A blast heard close (inside the 1 psi ring): the shock hits like a wall with a hard crack and
    a splutter, the wind tears past, and the land throws it back for a long while."""
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    t0 = 0.08
    main = np.zeros(n)
    add_at(main, lowpass(n_wave(0.9, 0.0004, 1.0), 7000, 2), t0)
    echoes = np.zeros(n)
    arrivals(echoes, rng, 120, t0 + 0.04, 2.6, 0.7, 0.0015, amp=1.0, dull=3000,
             clusters=[(t0 + rng.uniform(0.8, 1.3), 12, 0.3), (t0 + rng.uniform(2.5, 3.5), 25, 0.5),
                       (t0 + rng.uniform(5, 7), 30, 0.9), (t0 + rng.uniform(9, 11), 20, 1.3)])
    echoes = audible(echoes, peak=0.42)
    wind = audible(blast_wind(n, rng, t0 + 0.15, 4.5), rms=0.045)
    env = lowpass(np.abs(echoes), 1.5, 2)
    env = np.clip(env / (env.max() + 1e-9), 0, 1) * (t > t0)
    roll = audible(rumble(n, rng, env, 300), rms=0.025)
    after = np.clip(t - t0, 0, None)
    rate = (260 * np.exp(-after / 0.25) + 30 * np.exp(-after / 1.5)) * (t > t0)
    splutter = audible(crackle(n, rng, rate, 1.0), peak=0.45)
    out = audible(main, peak=1.0) + echoes + wind + roll + splutter
    # Loud enough to push the air (and the microphone) past straight lines: a soft round-off.
    out = np.tanh(out * 1.6) / 1.6
    return out * np.clip((seconds - t) / 3.0, 0, 1)


NUKE = {'nuke_near': nuke_near, 'nuke_far': nuke_far}


def giant_wind(seconds=12.0, seed=1995):
    """Thick air tearing past the hull deep in a giant planet (Galileo's probe year): a heavy low
    roar that surges and sags, the hull drumming under the buffeting, and a faint hollow moan in
    the gusts. Made round a loop so it repeats without a seam."""
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    f = np.fft.rfftfreq(n, 1 / RATE)
    # Roar: falls off steeply above ~300 Hz (the hull keeps the hiss out), firm down to ~30 Hz.
    shape = (f / 30) ** 2 / (1 + (f / 30) ** 2) / (1 + (f / 300) ** 2.4)
    roar = np.fft.irfft(np.fft.rfft(rng.normal(size=n)) * shape, n=n)
    roar /= np.std(roar)
    gust = np.exp(0.45 * smooth_noise(n, rng, int(1.6 * RATE)) + 0.25 * smooth_noise(n, rng, int(0.35 * RATE)))
    # Buffeting: the hull panels thump in uneven beats, 3-9 a second.
    beat = smooth_noise(n, rng, int(0.11 * RATE))
    drum = loop_filter(rng.normal(size=n), 25, 90)
    drum = drum / np.std(drum) * np.clip(beat, 0, None) ** 2
    # The moan: a narrow band that wanders slowly in pitch, only in the strong gusts.
    moan = loop_filter(rng.normal(size=n), 150, 240)
    moan = moan / np.std(moan) * np.clip(gust - 1.1, 0, None)
    x = roar * gust + 0.8 * drum * gust + 0.35 * moan
    return x - np.mean(x)


def hull_creak(variant=0):
    """A hull straining under the pressure: metal groaning as it slips a little at a time (short
    sticks and slips ringing the panels' low modes), swelling then dying away, with a sharp tick
    at the end now and then."""
    rng = np.random.default_rng(300 + variant)
    seconds = 2.6
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    # Stick-slip: a train of little impulses whose rate rises and falls (the pitch of the groan).
    rate = 38 + 26 * np.sin(np.pi * t / seconds) + 10 * smooth_noise(n, rng, int(0.2 * RATE))
    phase = np.cumsum(rate / RATE)
    pulses = np.zeros(n)
    ticks = np.nonzero(np.diff(np.floor(phase)) > 0)[0]
    pulses[ticks] = rng.gamma(3, 0.35, len(ticks))
    x = np.zeros(n)
    for mode, q in ((70 + 25 * variant, 30), (131 + 9 * variant, 40), (212, 50), (347 - 20 * variant, 60), (590, 70)):
        b, a = signal.iirpeak(mode / (RATE / 2), q)
        x += signal.lfilter(b, a, pulses) * (90 / mode) ** 0.5
    env = np.sin(np.pi * np.clip(t / seconds, 0, 1)) ** 1.5
    x = x * env
    if variant != 1:
        k = int(rng.uniform(0.75, 0.9) * n)
        click = rng.normal(size=600) * np.exp(-np.arange(600) / 90)
        x[k:k + 600] += bandpass(click, 900, 4000) * 3 * np.std(x)
    return lowpass(x, 3000, 2)

def rcs_puff(variant=0):
    """One RCS valve opening and closing: the solenoid's metallic tick and a short hiss of gas
    through the nozzle (bright, with a ragged start)."""
    rng = np.random.default_rng(410 + variant)
    seconds = 0.32
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    on = 0.11 + 0.03 * variant  # how long the valve stays open
    gas = rng.normal(size=n)
    gas = bandpass(gas, 1200 + 300 * variant, 9000)
    attack = np.clip(t / 0.004, 0, 1)
    release = np.where(t < on, 1.0, np.exp(-(t - on) / 0.025))
    flutter = 1 + 0.25 * smooth_noise(n, rng, int(0.006 * RATE))
    x = gas * attack * release * flutter
    # Solenoid ticks at open and close: short rings of the valve body.
    for at, level in ((0.0, 1.0), (on, 0.6)):
        k = int(at * RATE)
        m = min(n - k, 700)
        ring = np.zeros(m)
        for f, q in ((2400 + 150 * variant, 25), (4100, 30), (6300 - 200 * variant, 35)):
            b, a = signal.iirpeak(f / (RATE / 2), q)
            imp = np.zeros(m)
            imp[0] = 1
            ring += signal.lfilter(b, a, imp)
        x[k:k + m] += ring * level * 6 * np.std(gas)
    # (No low knock: a thump at the start and end of each burst sounded like something else.)
    return bandpass(x, 600, 12000)


def preview(folder, names):
    """Writes WAVs to a scratch folder instead of the mod (python3 make_sounds.py --preview DIR name...)."""
    os.makedirs(folder, exist_ok=True)
    makers = dict(PLASMA, **NUKE, engine_loop=engine_loop, radio_squelch=radio_squelch, geiger_click=geiger_click)
    for name in names:
        x = makers[name]()
        x = x / np.max(np.abs(x)) * 10 ** (-6 / 20)
        wavfile.write(os.path.join(folder, name + '.wav'), RATE, (x * 32767).astype(np.int16))
        print(name, f'{len(x) / RATE:.1f} s rms {20 * np.log10(np.sqrt(np.mean(x ** 2))):.1f} dBFS')


if __name__ == '__main__':
    import sys
    if len(sys.argv) > 2 and sys.argv[1] == '--only':
        if 'giant_wind' in sys.argv:
            write('giant_wind', giant_wind(), peak_db=-4.0, rms_db=-18.0)
        if 'hull_creak' in sys.argv:
            for v in range(3):
                write(f'hull_creak_{v + 1}', hull_creak(v), peak_db=-4.0)
        if 'rcs_puff' in sys.argv:
            for v in range(3):
                write(f'rcs_puff_{v + 1}', rcs_puff(v), peak_db=-5.0)
        sys.exit()
    if len(sys.argv) > 2 and sys.argv[1] == '--preview':
        preview(sys.argv[2], sys.argv[3:])
        sys.exit()
    write('engine_loop', engine_loop(), peak_db=-3.4, rms_db=-18.0)  # vorbis adds ~0.3 dB
    write('radio_squelch', radio_squelch(), peak_db=-7.3)  # decodes to about -6
    for name, make in PLASMA.items():
        write(name, make(), peak_db=-4.0, rms_db=-20.0)
    for v in range(3):
        write(f'geiger_click_{v + 1}', geiger_click(v), peak_db=-8.0)
    for name, make in NUKE.items():
        write(name, make(), peak_db=-1.3)  # decodes to about -1
    write('giant_wind', giant_wind(), peak_db=-4.0, rms_db=-18.0)
    for v in range(3):
        write(f'hull_creak_{v + 1}', hull_creak(v), peak_db=-4.0)
    for v in range(3):
        write(f'rcs_puff_{v + 1}', rcs_puff(v), peak_db=-5.0)
