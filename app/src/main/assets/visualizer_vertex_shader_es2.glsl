// Vertex shader for 3D terrain visualizer (OpenGL ES 2.0 compatible)
// Ported from sonic-topography CustomShaderMaterial

precision mediump float;

uniform float uTime;
uniform mat4 uProjectionMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uModelMatrix;

// Frequency envelopes
uniform float uSubBass;
uniform float uBass;
uniform float uLowMid;
uniform float uMid;
uniform float uHighMid;
uniform float uPresence;
uniform float uBrilliance;
uniform float uAir;

// Timbral
uniform float uWarmth;
uniform float uBrightness;
uniform float uSharpness;
uniform float uSmoothness;
uniform float uDensity;
uniform float uEnergy;

// Ripple uniforms (manual expansion for OpenGL ES 2.0)
uniform vec2 uRipplePos0;
uniform vec2 uRipplePos1;
uniform vec2 uRipplePos2;
uniform vec2 uRipplePos3;
uniform vec2 uRipplePos4;
uniform vec2 uRipplePos5;
uniform vec2 uRipplePos6;
uniform vec2 uRipplePos7;
uniform vec2 uRipplePos8;
uniform vec2 uRipplePos9;

uniform float uRippleTime0;
uniform float uRippleTime1;
uniform float uRippleTime2;
uniform float uRippleTime3;
uniform float uRippleTime4;
uniform float uRippleTime5;
uniform float uRippleTime6;
uniform float uRippleTime7;
uniform float uRippleTime8;
uniform float uRippleTime9;

uniform float uRippleStrength0;
uniform float uRippleStrength1;
uniform float uRippleStrength2;
uniform float uRippleStrength3;
uniform float uRippleStrength4;
uniform float uRippleStrength5;
uniform float uRippleStrength6;
uniform float uRippleStrength7;
uniform float uRippleStrength8;
uniform float uRippleStrength9;

uniform int uRippleActive0;
uniform int uRippleActive1;
uniform int uRippleActive2;
uniform int uRippleActive3;
uniform int uRippleActive4;
uniform int uRippleActive5;
uniform int uRippleActive6;
uniform int uRippleActive7;
uniform int uRippleActive8;
uniform int uRippleActive9;

attribute vec2 aPosition;
attribute vec2 aInstancePos;

varying vec2 vUv;
varying float vElevation;
varying float vDistance;
varying vec3 vNormal;
varying vec2 vInstancePosVar;

// Simplex noise function
vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec2 mod289(vec2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec3 permute(vec3 x) { return mod289(((x*34.0)+1.0)*x); }

float snoise(vec2 v) {
    const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
    vec2 i  = floor(v + dot(v, C.yy));
    vec2 x0 = v - i + dot(i, C.xx);
    vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    i = mod289(i);
    vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
    vec3 m = max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy), dot(x12.zw,x12.zw)), 0.0);
    m = m*m;
    m = m*m;
    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * (a0*a0 + h*h);
    vec3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
}

float random(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

void main() {
    vUv = aPosition * 0.5 + 0.5;
    vNormal = vec3(0.0, 1.0, 0.0);
    vInstancePosVar = aInstancePos;

    vec2 pos2D = aInstancePos;
    float centerDist = length(pos2D);
    vDistance = centerDist;

    float rnd = random(pos2D);

    // 1. Idle Background state (smooth, ocean-like)
    vec2 movingPos = pos2D * 0.05 + vec2(uTime * 0.1, uTime * 0.05);
    float baseNoise = (snoise(movingPos) + 1.0) * 0.5;
    float wave = sin(pos2D.x * 0.15 + pos2D.y * 0.1 - uTime * 0.6) * 0.5 + 0.5;

    float globalFalloff = smoothstep(60.0, 30.0, centerDist);
    float idleElevation = mix(baseNoise, wave, uSmoothness * 0.5 + 0.2) * 0.8 * globalFalloff;

    // 2. Frequency Regions & Displacements

    // Sub-Bass: Center heavy, ultra slow rolling hills
    float subRegion = smoothstep(25.0, 0.0, centerDist);
    float subLift = uSubBass * subRegion * 5.0;

    // Bass: Chunk-based lifts
    float bassNoise = snoise(pos2D * 0.1 - vec2(0.0, uTime * 0.2));
    float bassRegion = smoothstep(35.0, 5.0, centerDist + bassNoise * 5.0);
    float bassLift = uBass * bassRegion * (smoothstep(0.0, 1.0, rnd + uDensity * 0.5)) * 4.0;

    // Low Mid: Flowing waves across the whole map
    float lowMidNoise = snoise(pos2D * 0.05 + vec2(uTime * 0.1, 0.0));
    float lowMidLift = uLowMid * (lowMidNoise * 0.5 + 0.5) * 2.5;

    // Mid: River-like current
    float riverFlow = sin(pos2D.x * 0.2 + pos2D.y * 0.2 + snoise(pos2D * 0.1) * 2.0 - uTime * 2.0);
    float midLift = uMid * max(0.0, riverFlow) * 3.0;

    // High Mid: Individual scattered spikes
    float highMidRegion = smoothstep(10.0, 45.0, centerDist);
    float highMidLift = 0.0;
    if (fract(rnd * 13.3) > 0.8) {
        highMidLift = uHighMid * highMidRegion * fract(rnd * 7.7) * 2.5;
    }

    // Combine
    float audioElevation = subLift + bassLift + lowMidLift + midLift + highMidLift;

    // Energy Spike (increased probability and strength for more visible spikes)
    if (rnd > 0.97) { // Changed from 0.99 to 0.97 (3% probability instead of 1%)
        audioElevation += uEnergy * 10.0; // Increased from 5.0 to 10.0 for stronger spikes
    }

    audioElevation *= globalFalloff;

    float elevation = idleElevation + audioElevation;

    // Ripples (manual expansion for OpenGL ES 2.0)
    float rippleElevation = 0.0;
    float speed = 15.0;
    float width = 3.0;

    // Ripple 0
    if (uRippleActive0 > 0) {
        float dist = length(pos2D - uRipplePos0);
        float timeSince = uTime - uRippleTime0;
        float waveRadius = timeSince * speed;
        float d = dist - waveRadius;
        float rippleWave = exp(-d * d / width);
        float fade = exp(-waveRadius / 15.0);
        rippleElevation += rippleWave * fade * uRippleStrength0 * 4.0;
    }

    // Ripple 1
    if (uRippleActive1 > 0) {
        float dist = length(pos2D - uRipplePos1);
        float timeSince = uTime - uRippleTime1;
        float waveRadius = timeSince * speed;
        float d = dist - waveRadius;
        float rippleWave = exp(-d * d / width);
        float fade = exp(-waveRadius / 15.0);
        rippleElevation += rippleWave * fade * uRippleStrength1 * 4.0;
    }

    // Ripple 2
    if (uRippleActive2 > 0) {
        float dist = length(pos2D - uRipplePos2);
        float timeSince = uTime - uRippleTime2;
        float waveRadius = timeSince * speed;
        float d = dist - waveRadius;
        float rippleWave = exp(-d * d / width);
        float fade = exp(-waveRadius / 15.0);
        rippleElevation += rippleWave * fade * uRippleStrength2 * 4.0;
    }

    // Ripple 3
    if (uRippleActive3 > 0) {
        float dist = length(pos2D - uRipplePos3);
        float timeSince = uTime - uRippleTime3;
        float waveRadius = timeSince * speed;
        float d = dist - waveRadius;
        float rippleWave = exp(-d * d / width);
        float fade = exp(-waveRadius / 15.0);
        rippleElevation += rippleWave * fade * uRippleStrength3 * 4.0;
    }

    // Ripple 4
    if (uRippleActive4 > 0) {
        float dist = length(pos2D - uRipplePos4);
        float timeSince = uTime - uRippleTime4;
        float waveRadius = timeSince * speed;
        float d = dist - waveRadius;
        float rippleWave = exp(-d * d / width);
        float fade = exp(-waveRadius / 15.0);
        rippleElevation += rippleWave * fade * uRippleStrength4 * 4.0;
    }

    // Ripple 5
    if (uRippleActive5 > 0) {
        float dist = length(pos2D - uRipplePos5);
        float timeSince = uTime - uRippleTime5;
        float waveRadius = timeSince * speed;
        float d = dist - waveRadius;
        float rippleWave = exp(-d * d / width);
        float fade = exp(-waveRadius / 15.0);
        rippleElevation += rippleWave * fade * uRippleStrength5 * 4.0;
    }

    // Ripple 6
    if (uRippleActive6 > 0) {
        float dist = length(pos2D - uRipplePos6);
        float timeSince = uTime - uRippleTime6;
        float waveRadius = timeSince * speed;
        float d = dist - waveRadius;
        float rippleWave = exp(-d * d / width);
        float fade = exp(-waveRadius / 15.0);
        rippleElevation += rippleWave * fade * uRippleStrength6 * 4.0;
    }

    // Ripple 7
    if (uRippleActive7 > 0) {
        float dist = length(pos2D - uRipplePos7);
        float timeSince = uTime - uRippleTime7;
        float waveRadius = timeSince * speed;
        float d = dist - waveRadius;
        float rippleWave = exp(-d * d / width);
        float fade = exp(-waveRadius / 15.0);
        rippleElevation += rippleWave * fade * uRippleStrength7 * 4.0;
    }

    // Ripple 8
    if (uRippleActive8 > 0) {
        float dist = length(pos2D - uRipplePos8);
        float timeSince = uTime - uRippleTime8;
        float waveRadius = timeSince * speed;
        float d = dist - waveRadius;
        float rippleWave = exp(-d * d / width);
        float fade = exp(-waveRadius / 15.0);
        rippleElevation += rippleWave * fade * uRippleStrength8 * 4.0;
    }

    // Ripple 9
    if (uRippleActive9 > 0) {
        float dist = length(pos2D - uRipplePos9);
        float timeSince = uTime - uRippleTime9;
        float waveRadius = timeSince * speed;
        float d = dist - waveRadius;
        float rippleWave = exp(-d * d / width);
        float fade = exp(-waveRadius / 15.0);
        rippleElevation += rippleWave * fade * uRippleStrength9 * 4.0;
    }

    elevation += rippleElevation;
    vElevation = elevation;

    // Create world position with elevation (scale up for better visibility)
    vec4 worldPosition = vec4(pos2D.x, elevation * 3.0, pos2D.y, 1.0);
    
    // Apply model-view-projection transformation
    gl_Position = uProjectionMatrix * uViewMatrix * uModelMatrix * worldPosition;
    
    // Point size for better visibility (much larger)
    gl_PointSize = 8.0 + elevation * 2.0;
}