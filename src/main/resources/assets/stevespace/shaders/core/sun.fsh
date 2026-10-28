#version 150

// The Sun up close, ray traced per pixel so it stays sharp however near you are: a boiling
// photosphere (granules inside supergranules, turbulent hot plasma over it), sunspots with
// their streaky penumbrae and bright faculae, flare ribbons, a darkening limb, and off the
// edge the chromosphere, spicules, prominences licking up and the corona's streamers.
//
// Directions are in the pose space the cube was drawn in; ViewToSun turns them into the
// ecliptic frame the spots and flares are given in (z = the Sun's spin axis).

uniform vec4 ColorModulator;
uniform vec3 SunDir;
uniform float SinRadius;
uniform float Spin;
uniform float AnimTime;
uniform float Activity;
uniform float Haze;
uniform mat4 ViewToSun;
uniform mat4 SpotsA;
uniform mat4 SpotsB;
uniform mat4 SpotsC;
uniform mat4 Flares;

in vec3 viewDir;

out vec4 fragColor;

vec3 hash3(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return fract(sin(p) * 43758.5453123);
}

// Gradient noise, -1..1.
float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float n000 = dot(hash3(i) * 2.0 - 1.0, f);
    float n100 = dot(hash3(i + vec3(1, 0, 0)) * 2.0 - 1.0, f - vec3(1, 0, 0));
    float n010 = dot(hash3(i + vec3(0, 1, 0)) * 2.0 - 1.0, f - vec3(0, 1, 0));
    float n110 = dot(hash3(i + vec3(1, 1, 0)) * 2.0 - 1.0, f - vec3(1, 1, 0));
    float n001 = dot(hash3(i + vec3(0, 0, 1)) * 2.0 - 1.0, f - vec3(0, 0, 1));
    float n101 = dot(hash3(i + vec3(1, 0, 1)) * 2.0 - 1.0, f - vec3(1, 0, 1));
    float n011 = dot(hash3(i + vec3(0, 1, 1)) * 2.0 - 1.0, f - vec3(0, 1, 1));
    float n111 = dot(hash3(i + vec3(1, 1, 1)) * 2.0 - 1.0, f - vec3(1, 1, 1));
    return 1.6 * mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
                     mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

// Cells whose centres wander with time: x = distance to the nearest centre, y = to the second.
vec2 cells(vec3 p, float t) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    float f1 = 8.0;
    float f2 = 8.0;
    for (int a = -1; a <= 1; a++) {
        for (int b = -1; b <= 1; b++) {
            for (int c = -1; c <= 1; c++) {
                vec3 o = vec3(a, b, c);
                vec3 h = hash3(i + o);
                vec3 centre = o + 0.5 + 0.38 * sin(t * (0.6 + h) + 6.2831 * h);
                float d = length(centre - f);
                if (d < f1) {
                    f2 = f1;
                    f1 = d;
                } else if (d < f2) {
                    f2 = d;
                }
            }
        }
    }
    return vec2(f1, f2);
}

// How much detail of this frequency the pixel can show (fades out before it would shimmer).
float detail(float frequency, float footprint) {
    return 1.0 - smoothstep(0.12, 0.5, frequency * footprint);
}

// Threads of hot plasma: ridged, warped turbulence, 0..1.
float fire(vec3 q, float t, float footprint) {
    vec3 warp = vec3(noise(q * 5.0 + vec3(0.0, 0.0, t * 0.004)), noise(q * 5.0 + vec3(17.0, 3.0, -t * 0.005)), noise(q * 5.0 + vec3(5.0, 29.0, t * 0.003)));
    q += warp * 0.06;
    float sum = 0.0;
    float norm = 0.0;
    float amp = 1.0;
    float freq = 14.0;
    for (int k = 0; k < 6; k++) {
        float w = detail(freq, footprint) * amp;
        if (w <= 0.0) {
            norm += amp * (1.0 - pow(0.6, float(6 - k))) / 0.4; // the rest would add nothing
            break;
        }
        float r = 1.0 - abs(noise(q * freq + vec3(0.0, t * 0.01 * float(k + 1), float(k) * 7.1)));
        sum += r * r * w;
        norm += amp;
        amp *= 0.6;
        freq *= 2.1;
    }
    return sum / norm;
}

// Colour of hot gas by brightness: deep red, orange, yellow, white.
vec3 heat(float x) {
    vec3 c = mix(vec3(0.30, 0.04, 0.0), vec3(0.95, 0.32, 0.03), smoothstep(0.0, 0.45, x));
    c = mix(c, vec3(1.0, 0.70, 0.22), smoothstep(0.4, 0.8, x));
    c = mix(c, vec3(1.0, 0.93, 0.70), smoothstep(0.8, 1.15, x));
    return mix(c, vec3(1.0, 1.0, 0.96), smoothstep(1.15, 1.6, x));
}

void oneSpot(vec3 centre, float size, vec3 n, vec3 q, inout float dark) {
    float d = length(n - centre);
    float umbra = 0.006 + 0.03 * size;
    float penumbra = umbra * 2.2;
    if (d > penumbra * 1.4) {
        return;
    }
    // Penumbral filaments: streaks running out from the centre.
    vec3 out_ = normalize(n - centre * dot(n, centre) + 1e-6);
    float streak = 0.5 + 0.5 * noise(out_ * 70.0 + centre * 13.0);
    float ragged = noise(q * 260.0 + centre * 5.0) * 0.22;
    float inPen = 1.0 - smoothstep(penumbra * (0.8 + ragged), penumbra * (1.1 + ragged), d);
    float inUmbra = 1.0 - smoothstep(umbra * (0.75 + ragged), umbra * (1.05 + ragged), d);
    float level = mix(1.0, 0.45 + 0.25 * streak, inPen);
    level = mix(level, 0.08, inUmbra);
    dark = min(dark, level);
}

// A sunspot group: a big leading spot, a trailing one behind it (east), and small pores between.
void spot(vec4 s, vec3 n, vec3 q, inout float dark, inout float near) {
    if (s.w <= 0.0) {
        return;
    }
    float reach = 0.03 + 0.12 * s.w;
    float d = length(n - s.xyz);
    near = max(near, exp(-pow(d / (reach * 2.2), 2.0)));
    if (d > reach * 1.6) {
        return;
    }
    vec3 east = normalize(cross(vec3(0.0, 0.0, 1.0), s.xyz) + 1e-6);
    vec3 north = cross(s.xyz, east);
    vec3 h = hash3(s.xyz * 97.0);
    oneSpot(s.xyz, s.w, n, q, dark);
    oneSpot(normalize(s.xyz - east * reach * (0.8 + 0.3 * h.x) + north * reach * (h.y - 0.5) * 0.4), s.w * (0.4 + 0.4 * h.z), n, q, dark);
    for (int i = 0; i < 4; i++) {
        vec3 r = hash3(s.xyz * 31.0 + float(i) * 7.3);
        vec3 c = normalize(s.xyz - east * reach * r.x * 0.9 + north * reach * (r.y - 0.5) * 0.6);
        oneSpot(c, s.w * 0.12 * r.z, n, q, dark);
    }
}

void main() {
    vec3 d = normalize(viewDir);
    float c = dot(d, SunDir);
    if (c <= 0.0) {
        discard;
    }
    float sinR = SinRadius;
    float b2 = max(0.0, 1.0 - c * c);
    float b = sqrt(b2) / sinR; // closest approach to the centre, in solar radii
    if (b > 9.0) {
        discard;
    }
    float t = AnimTime;
    float edgeWidth = max(fwidth(b), 1e-4);
    vec3 disc = vec3(0.0);
    float discAlpha = 1.0 - smoothstep(1.0 - edgeWidth, 1.0, b);

    if (b < 1.0) {
        // Where the ray meets the surface, and which way the surface faces there.
        float along = c - sqrt(max(0.0, sinR * sinR - b2));
        vec3 n = (d * along - SunDir) / sinR;
        float mu = clamp(dot(n, -d), 0.0, 1.0);
        vec3 ni = normalize((ViewToSun * vec4(n, 0.0)).xyz);
        float cs = cos(-Spin);
        float sn = sin(-Spin);
        vec3 q = vec3(cs * ni.x - sn * ni.y, sn * ni.x + cs * ni.y, ni.z);
        float footprint = length(fwidth(ni)) + 1e-6;

        // Supergranules (the big cells you see from a distance) and granules (close up).
        // (each layer is only worked out when it is big enough on screen to see)
        float superCell = 1.0;
        float dSuper = detail(38.0, footprint);
        if (dSuper > 0.0) {
            vec2 sg = cells(q * 38.0 + noise(q * 9.0) * 0.6, t * 0.002);
            superCell = mix(1.0, 0.9 + 0.1 * smoothstep(0.0, 0.25, sg.y - sg.x), dSuper);
        }
        float gran = 0.8;
        float dGran = detail(640.0, footprint);
        if (dGran > 0.0) {
            vec3 gq = q * 640.0;
            gq += vec3(noise(gq * 0.25), noise(gq * 0.25 + 7.0), noise(gq * 0.25 + 13.0)) * 0.45;
            vec2 g = cells(gq, t * 0.05);
            // Soft round tops, wide dim lanes between them.
            float granule = smoothstep(0.0, 0.4, g.y - g.x) * 0.6 + (1.0 - smoothstep(0.1, 0.75, g.x)) * 0.55;
            gran = mix(0.8, 0.45 + 0.6 * granule, dGran);
        }
        float dFine = detail(2400.0, footprint);
        if (dFine > 0.0) {
            vec2 g2 = cells(q * 2400.0, t * 0.12);
            gran *= mix(1.0, 0.85 + 0.25 * smoothstep(0.0, 0.2, g2.y - g2.x), dFine);
        }

        float dark = 1.0;
        float near = 0.0;
        for (int i = 0; i < 4; i++) {
            spot(SpotsA[i], ni, q, dark, near);
            spot(SpotsB[i], ni, q, dark, near);
            spot(SpotsC[i], ni, q, dark, near);
        }
        float flame = fire(q, t, footprint);
        float limb = 1.0 - 0.58 * (1.0 - mu) - 0.22 * (1.0 - mu) * (1.0 - mu);
        float faculae = near * (0.15 + 0.5 * (1.0 - mu)) * smoothstep(0.45, 0.8, flame + noise(q * 90.0) * 0.3);
        float level = (0.62 + 0.5 * gran) * superCell * limb;
        level += (flame - 0.35) * (0.25 + 0.55 * near + 0.15 * Activity);
        level = level * dark + faculae;
        // Flare ribbons: twin strands of white-hot light over an active region.
        for (int i = 0; i < 4; i++) {
            vec4 f = Flares[i];
            if (f.w <= 0.0) {
                continue;
            }
            float fd = length(ni - f.xyz);
            float ribbon = pow(1.0 - abs(noise(q * 140.0 + vec3(t * 0.02))), 6.0);
            level += f.w * (exp(-pow(fd / 0.05, 2.0)) * ribbon * 1.6 + exp(-pow(fd / 0.018, 2.0)) * 1.2);
        }
        disc = heat(level * (0.85 + 0.35 * mu));
    }

    // Off the edge: chromosphere, spicules, prominences, corona.
    float h = max(0.0, b - 1.0);
    vec3 axis = (transpose(ViewToSun) * vec4(0.0, 0.0, 1.0, 0.0)).xyz;
    vec3 up = axis - SunDir * dot(axis, SunDir);
    up = length(up) < 1e-4 ? vec3(0.0, 1.0, 0.0) : normalize(up);
    vec3 side = cross(SunDir, up);
    float phi = atan(dot(d, side), dot(d, up));
    vec3 ring = vec3(cos(phi), sin(phi), 0.0); // seamless around the limb
    float aroundFoot = 1.0 / max(1.0, fwidth(phi) * 300.0);

    float chromo = exp(-h / 0.006);
    float spicules = smoothstep(0.03, 0.0, h) * (0.45 + 0.55 * noise(vec3(ring.xy * 260.0, h * 70.0 + t * 0.15))) * aroundFoot;
    // Prominences: a few arches of glowing gas standing on the limb, each in its own sector,
    // their strands torn into flame by turbulence.
    vec3 pp = vec3(ring.xy * 30.0, h * 40.0 - t * 0.02);
    pp += vec3(noise(pp * 0.5 + t * 0.003), noise(pp * 0.5 + 9.0), 0.0) * 1.2;
    float tear = noise(pp) * 0.5 + 0.5;
    float prominence = 0.0;
    const float SECTORS = 16.0;
    float sector = floor((phi + 3.14159265) / 6.2831853 * SECTORS);
    for (int k = -1; k <= 1; k++) {
        float id = mod(sector + float(k), SECTORS);
        vec3 rnd = hash3(vec3(id, floor(Spin * 3.0), 5.0));
        vec3 rnd2 = hash3(vec3(id, floor(Spin * 3.0), 9.0));
        if (rnd.x > 0.25 + 0.35 * Activity) {
            continue;
        }
        float centre = (id + 0.2 + 0.6 * rnd.y) / SECTORS * 6.2831853 - 3.14159265;
        float dphi = phi - centre;
        dphi -= 6.2831853 * floor((dphi + 3.14159265) / 6.2831853);
        float width = 0.03 + 0.09 * rnd.z;
        float x = dphi / width;
        if (abs(x) >= 1.0) {
            continue;
        }
        float top = (0.025 + 0.1 * rnd2.x) * sqrt(1.0 - x * x);
        float wob = (tear - 0.5) * 0.02;
        float strand = exp(-pow((h - top + wob) / (0.004 + 0.01 * rnd2.y), 2.0));
        float body = smoothstep(top + 0.01, top * 0.3, h) * 0.35 * smoothstep(0.35, 0.8, tear);
        prominence += (strand * (0.6 + 0.8 * tear) + body) * (1.0 - smoothstep(0.75, 1.0, abs(x)));
    }
    prominence *= 1.8;
    float streamers = 0.35 + 0.65 * pow(noise(ring * 3.0 + vec3(0.0, 0.0, Spin * 0.3)) * 0.5 + 0.5, 2.0);
    float rays = 0.75 + 0.25 * noise(vec3(ring.xy * 45.0, h * 0.4 + 2.0));
    float corona = streamers * rays * pow(max(b, 1.0), -3.2) * 0.55;
    float glow = exp(-h * 1.3) * 0.16 + exp(-h * 9.0) * 0.12;

    float air = 1.0 - Haze;
    vec3 halo = vec3(1.0, 0.30, 0.10) * (chromo * 0.9 + spicules * 0.7) * air
              + vec3(1.0, 0.33, 0.08) * min(prominence, 1.6) * air
              + vec3(1.0, 0.90, 0.78) * corona * (0.3 + 0.7 * air)
              + vec3(1.0, 0.78, 0.45) * glow;
    halo *= 1.0 - discAlpha;

    // Premultiplied: the disc covers what's behind it, the glow adds to it.
    vec3 colour = disc * discAlpha + halo;
    fragColor = vec4(colour * ColorModulator.rgb, discAlpha * ColorModulator.a) * vec4(vec3(ColorModulator.a), 1.0);
}
