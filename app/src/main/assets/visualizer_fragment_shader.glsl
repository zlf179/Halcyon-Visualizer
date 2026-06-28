// Fragment shader for 3D terrain visualizer
// Ported from sonic-topography CustomShaderMaterial

precision mediump float;

uniform float uTime;
uniform float uWarmth;
uniform float uBrightness;
uniform float uSharpness;
uniform float uSmoothness;
uniform float uDensity;
uniform float uEnergy;

// Frequency bands for color effects (from sonic-topography)
uniform float uPresence;
uniform float uBrilliance;
uniform float uAir;

// Theme colors (will be set from Android palette)
uniform vec3 uBaseColor1;
uniform vec3 uBaseColor2;
uniform vec3 uCoolCore;
uniform vec3 uCoolEdge;
uniform vec3 uWarmCore;
uniform vec3 uWarmEdge;
uniform float uGlowIntensity;

varying vec2 vUv;
varying float vElevation;
varying float vDistance;
varying vec3 vNormal;
varying vec2 vInstancePosVar;

float random(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

void main() {
    float rnd = random(vInstancePosVar);
    float centerDist = length(vInstancePosVar);

    float normElevation = clamp(vElevation / 8.0, 0.0, 1.0);

    // Base dark pillars
    vec3 cBase1 = uBaseColor1;
    vec3 cBase2 = uBaseColor2;

    // Timbre determines palette
    vec3 coolCore = uCoolCore;
    vec3 coolEdge = uCoolEdge;
    vec3 warmCore = uWarmCore;
    vec3 warmEdge = uWarmEdge;

    float warmBlend = smoothstep(0.0, 1.0, uWarmth * 1.5 + (0.5 - centerDist/80.0));

    vec3 zoneCore = mix(coolCore, warmCore, warmBlend);
    vec3 zoneEdge = mix(coolEdge, warmEdge, warmBlend);

    // Shift colors slightly per pillar
    vec3 targetGlow = mix(zoneCore, zoneEdge, fract(rnd * 11.0));

    // Distance fade for contrast and brightness
    float distFade = 1.0 - smoothstep(40.0, 75.0, centerDist);

    // Brightness lifts the black point of the glow
    targetGlow = mix(targetGlow, vec3(0.4, 0.8, 1.0), uBrightness * 0.6);

    vec3 currentGlow = mix(cBase2, targetGlow, normElevation) * uGlowIntensity * distFade;

    vec3 bodyColor = mix(cBase1, cBase2, normElevation * distFade);

    // Top face lighting
    float topIntensity = smoothstep(0.0, 0.4, normElevation);
    
    // Twinkle multiplier based on smoothness (from sonic-topography)
    float twinkleMultiplier = (1.0 - uSmoothness) * 2.0 + 0.5;
    
    // Air: sparkle effect on low elevation pillars (from sonic-topography)
    bool isSparkleTarget = fract(rnd * 31.0) > 0.95;
    if (isSparkleTarget && normElevation < 0.1) {
        topIntensity += uAir * 2.0 * twinkleMultiplier;
    }
    
    vec3 finalColor = mix(cBase2, currentGlow, topIntensity);

    // Edge glow
    float edgeX = smoothstep(0.05, 0.01, vUv.x) + smoothstep(0.95, 0.99, vUv.x);
    float edgeY = smoothstep(0.05, 0.01, vUv.y) + smoothstep(0.95, 0.99, vUv.y);
    float edge = min(edgeX + edgeY, 1.0);
    finalColor += currentGlow * edge * 0.8 * (topIntensity + 0.3);

    // Presence: flickering flashes (from sonic-topography)
    float flashChance = smoothstep(0.3, 1.0, uPresence);
    if (fract(rnd * 53.0 + uTime * 2.0) > 0.98 - flashChance * 0.1) {
        float flashSync = sin(uTime * 40.0 + rnd * 100.0) * 0.5 + 0.5;
        finalColor += mix(vec3(1.0), vec3(0.5, 1.0, 1.0), rnd) * flashSync * uPresence * (1.0 + uSharpness * 2.0) * twinkleMultiplier;
    }

    // Brilliance: micro-sparks on edges (from sonic-topography)
    if (edge > 0.5 && fract(rnd * 89.0 + uTime * 2.0) > 0.98) {
        finalColor += vec3(1.0) * uBrilliance * 3.0 * twinkleMultiplier;
    }

    // Aerial Perspective / Fog
    float aerialFog = smoothstep(30.0, 65.0, vDistance);
    vec3 atmosphericColor = mix(cBase1, cBase2, 0.4);
    finalColor = mix(finalColor, atmosphericColor, aerialFog * 0.5);

    // Distance fade out
    float alphaFade = 1.0 - smoothstep(55.0, 78.0, vDistance);

    gl_FragColor = vec4(finalColor, alphaFade);
}