#version 300 es
precision highp float;
out vec4 pixel;
uniform vec2 size;
uniform sampler2D artwork;
uniform float artworkRatio, time, entrance, energy;
uniform vec2 pointer;
uniform int waveCount;
uniform vec4 waves[4];

float hash(vec2 xy) {
  return fract(sin(dot(xy, vec2(39.57, 177.11))) * 18273.71);
}

float roundedSquare(vec2 p, float halfSize, float radius) {
  vec2 q = abs(p) - (halfSize - radius);
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float noise(vec2 p) {
  vec2 cell = floor(p);
  vec2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  float a = mix(hash(cell), hash(cell + vec2(1, 0)), f.x);
  float b = mix(hash(cell + vec2(0, 1)), hash(cell + vec2(1, 1)), f.x);
  return mix(a, b, f.y);
}

vec3 spectrumAt(float wavelength) {
  float visible = smoothstep(389.0, 428.0, wavelength) * (1.0 - smoothstep(652.0, 748.0, wavelength));
  vec3 distanceToColor = (wavelength - vec3(612, 544, 460)) / vec3(61, 51, 50);
  return visible * exp(-distanceToColor * distanceToColor);
}

vec3 chromeSurface(vec2 p, vec2 face, vec2 cell, float seed, float bevel,
                   float pulse, float angle, float coverMask, vec3 base) {
  vec2 corner = max(abs(face) - 0.32, 0.0) * sign(face);
  vec2 edgeNormal = normalize(corner + vec2(0.0001));
  vec2 lean = (vec2(seed, hash(cell + 7.3)) - 0.5) * 0.19;
  vec2 toHand = pointer - p;
  lean += pointer * 0.13 + toHand * exp(-dot(toHand, toHand) * 7.0) * min(length(pointer), 0.6) * 0.48;
  vec3 normal = normalize(vec3(lean + face * 0.07 + edgeNormal * bevel * 0.24, 1.0));
  float cs = cos(angle);
  float sn = sin(angle);
  normal = vec3(normal.x * cs + normal.z * sn, normal.y, normal.z * cs - normal.x * sn);
  normal *= normal.z < 0.0 ? -1.0 : 1.0;

  vec2 lamp = vec2(0.47 * sin(time * 0.39 + 0.5), 0.58 * cos(time * 0.31 + 0.4));
  lamp += pointer * 0.91;
  vec3 view = normalize(vec3(-p, 1.08));
  vec3 light = normalize(vec3(lamp, 1.0));
  vec3 halfVector = normalize(light + view);
  float reflected = max(dot(normal, halfVector), 0.0);
  float soft = pow(reflected, 23.0);
  float hot = pow(reflected, 245.0);

  // Each etched tile inherits a direction from a drifting field, with small tile-to-tile differences.
  vec2 field = p * 1.83 + vec2(time * 0.076, -time * 0.061);
  float grainAngle = noise(field) * 5.3 + seed * 1.03;
  grainAngle += 0.68 * sin(distance(p, lamp * 0.74) * 17.0 - time * 1.08) + pulse * 2.9;
  vec3 tangent = vec3(cos(grainAngle), sin(grainAngle), 0.0);
  tangent = normalize(tangent - normal * dot(tangent, normal));
  vec3 foil = vec3(0.0);
  for (int i = -1; i <= 1; i++) {
    float offset = float(i) * 0.20;
    vec3 lightSample = normalize(vec3(lamp + offset * vec2(0.84, -0.55), 1.0));
    float wavelength = abs(dot(lightSample + view, tangent)) * 1430.0;
    foil += spectrumAt(wavelength) + spectrumAt(wavelength * 0.53) * 0.47;
  }
  foil *= 0.3333;
  float silverBand = dot(foil, vec3(0.26, 0.58, 0.16));
  foil = mix(vec3(silverBand), foil, 0.18);

  vec3 silver = vec3(0.91, 0.94, 1.0);
  float brightness = 0.93 + energy * 0.56 + pulse * 0.45;
  vec3 broad = (silver * soft * 0.24 + foil * 1.11) * brightness;
  vec3 sharp = silver * (hot * 1.08 + abs(sn) * 0.19 + pulse * 0.055) * brightness;
  vec3 surface = mix(base * 0.65 + silver * 0.036, base * 0.93, coverMask);
  surface += broad * mix(1.36, 0.72, coverMask) + sharp * mix(1.0, 0.64, coverMask);

  vec2 grainGrid = face * 4.0;
  vec2 grainCell = floor(grainGrid) + cell * 11.0;
  float speck = hash(grainCell);
  if (speck > 0.80) {
    vec2 spot = fract(grainGrid) - vec2(speck, hash(grainCell + 3.5)) * 0.7 - 0.15;
    vec3 grainNormal = normalize(normal + vec3((vec2(speck, hash(grainCell - 4.5)) - 0.63) * 0.37, 0));
    float glint = smoothstep(0.979, 0.9992, dot(grainNormal, halfVector));
    glint *= 1.0 - smoothstep(0.042, 0.20, length(spot));
    surface += mix(silver, spectrumAt(440.0 + speck * 82.0), 0.48) * glint * 1.53;
  }
  return surface / (1.0 + surface * 0.31);
}

void main() {
  vec2 screen = vec2(gl_FragCoord.x, size.y - gl_FragCoord.y);
  float scale = min(size.x, size.y);
  vec2 p = (screen - size * vec2(0.5, 0.46)) / scale;
  const float cellSize = 0.071;
  vec2 grid = p / cellSize;
  vec2 cell = floor(grid) + 0.5;
  vec2 local = grid - cell;
  vec2 center = cell * cellSize;
  float seed = hash(cell);

  float pulse = 0.0;
  float flip = 0.0;
  for (int i = 0; i < waveCount; ++i) {
    vec4 wave = waves[i];
    float travel = wave.z * 0.86;
    float radius = distance(p, wave.xy);
    float fromFront = (radius - travel + 0.09) / 0.23;
    float fade = smoothstep(0.0, 0.10, wave.z) * (1.0 - smoothstep(0.60, 1.64, wave.z));
    pulse += exp(-fromFront * fromFront) * fade * abs(wave.w);
    if (wave.w < 0.0) {
      float crossing = travel - distance(center, wave.xy);
      float phase = smoothstep(0.0, 0.29, crossing);
      flip += sin(phase * 3.14159265) * abs(wave.w) * 1.2;
    }
  }
  pulse = min(pulse, 1.0);
  float introAmount = smoothstep(0.0, 0.44, entrance - length(center) * 0.98 - seed * 0.06);
  float angle = flip + (1.0 - introAmount) * 1.47;
  float compression = max(0.055, abs(cos(angle)));
  vec2 face = vec2(local.x / compression, local.y);
  float edge = roundedSquare(face, 0.463, 0.082);
  float smoothEdge = 1.0 / (scale * cellSize);
  smoothEdge *= mix(1.0, 1.0 / compression, smoothstep(0.37, 0.53, abs(face.x)));
  float mask = 1.0 - smoothstep(-smoothEdge * 0.6, smoothEdge * 0.6, edge);
  float bevel = smoothstep(-0.10, -0.011, edge);

  float imageSize = 0.86;
  vec2 aspect = vec2(min(1.0, artworkRatio), min(1.0, 1.0 / artworkRatio));
  vec2 artP = (center + face * cellSize) / (imageSize * aspect);
  vec2 uv = artP + 0.5;
  float inner = max(abs(artP.x), abs(artP.y));
  float coverMask = 1.0 - smoothstep(0.50, 0.55, inner);
  vec2 mirrorUv = 1.0 - abs(mod(uv, 2.0) - 1.0);
  vec3 margin = textureLod(artwork, mirrorUv, 6.0).rgb;
  vec3 base = margin * 0.46 + vec3(0.028, 0.041, 0.075);
  if (coverMask > 0.0) {
    vec3 photo = textureLod(artwork, clamp(uv, 0.0, 1.0), 0.8).rgb;
    base = mix(base, photo * 0.91, coverMask);
  }

  float falloff = 1.0 - 0.27 * smoothstep(0.34, 1.03, length(p * vec2(0.91, 0.66)));
  vec3 seam = vec3(0.025, 0.033, 0.064) + base * 0.18;
  vec3 reflection = chromeSurface(p, face, cell, seed, bevel, pulse, angle, coverMask, base);
  pixel = vec4(mix(seam, clamp(reflection, 0.0, 1.0), mask) * falloff, 1.0);
}
