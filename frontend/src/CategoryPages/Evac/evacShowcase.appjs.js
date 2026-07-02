/* =============================================================================
   피난 동역학 쇼케이스 — 보행자 동역학 모델 3종 실시간 포팅 (vanilla JS)
   -----------------------------------------------------------------------------
   libsimulator 소스의 연산 모델을 그대로 옮긴 것:
     · CFSM  Collision-Free Speed Model   (1차, 간격기반 최적속도 + 지수 반발)
     · SFM   Social Force Model           (2차, 구동력 + 지수 반발 + 신체력/마찰)
     · AVM   Anticipation Velocity Model  (1차, 예측 측면회피 + 반응시간 평활 + 벽투영)
   기본 파라미터는 각 모델의 *Data.hpp / python 모듈 기본값을 그대로 사용.
   ========================================================================== */
// [이관] 원본: public/evac-sim/app.js (바닐라 IIFE). 운영 CSP가 인라인 스크립트를 막으므로
// IIFE 자동실행을 export function 으로 변경. 로직 본문은 원본 그대로 유지.
export function initEvacShowcase() {
  "use strict";

  var REDUCED = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var EPS = 1e-6;

  /* ---------------------------------------------------------------- math */
  function hypot(x, y) { return Math.sqrt(x * x + y * y); }
  function rand(a, b) { return a + Math.random() * (b - a); }
  function closestOnSeg(px, py, x1, y1, x2, y2) {
    var dx = x2 - x1, dy = y2 - y1, len2 = dx * dx + dy * dy;
    var t = len2 === 0 ? 0 : ((px - x1) * dx + (py - y1) * dy) / len2;
    t = t < 0 ? 0 : t > 1 ? 1 : t;
    return { x: x1 + t * dx, y: y1 + t * dy };
  }
  function segHit(ax, ay, bx, by, cx, cy, dx, dy) {
    function ccw(x1, y1, x2, y2, x3, y3) { return (y3 - y1) * (x2 - x1) > (y2 - y1) * (x3 - x1); }
    return ccw(ax, ay, cx, cy, dx, dy) !== ccw(bx, by, cx, cy, dx, dy) &&
           ccw(ax, ay, bx, by, cx, cy) !== ccw(ax, ay, bx, by, dx, dy);
  }
  // 벽에 가린 이웃 제거 (line-of-sight, CFSM/AVM 공용)
  function filterVisible(a, raw, near) {
    var out = [];
    for (var j = 0; j < raw.length; j++) {
      var b = raw[j], blocked = false;
      for (var k = 0; k < near.length; k++) {
        var s = near[k].seg;
        if (segHit(a.x, a.y, b.x, b.y, s[0], s[1], s[2], s[3])) { blocked = true; break; }
      }
      if (!blocked) out.push(b);
    }
    return out;
  }

  /* ------------------------------------------------ scenario authoring helpers */
  function rect(x, y, w, h) {
    return [[x, y, x + w, y], [x + w, y, x + w, y + h], [x + w, y + h, x, y + h], [x, y + h, x, y]];
  }
  function room(x, y, w, h, gaps) {
    gaps = gaps || {};
    var walls = [];
    function side(x1, y1, x2, y2, gap, axis) {
      if (!gap) { walls.push([x1, y1, x2, y2]); return; }
      if (axis === "x") { walls.push([x1, y1, gap[0], y1]); walls.push([gap[1], y1, x2, y2]); }
      else { walls.push([x1, y1, x1, gap[0]]); walls.push([x1, gap[1], x2, y2]); }
    }
    side(x, y, x + w, y, gaps.top, "x");
    side(x + w, y, x + w, y + h, gaps.right, "y");
    side(x, y + h, x + w, y + h, gaps.bottom, "x");
    side(x, y, x, y + h, gaps.left, "y");
    return walls;
  }
  function circlePoly(cx, cy, r, n) {
    var segs = [], prev = null;
    for (var i = 0; i <= n; i++) {
      var a = (i / n) * Math.PI * 2, p = [cx + Math.cos(a) * r, cy + Math.sin(a) * r];
      if (prev) segs.push([prev[0], prev[1], p[0], p[1]]);
      prev = p;
    }
    return segs;
  }
  function circleWaypoints(cx, cy, r, n) {
    var wp = [];
    for (var i = 0; i < n; i++) { var a = (i / n) * Math.PI * 2; wp.push({ x: cx + Math.cos(a) * r, y: cy + Math.sin(a) * r }); }
    return wp;
  }

  /* =========================================================== MODELS (real math) */

  // ---- CFSM ----------------------------------------------------------------
  function cfsmCompute(a, raw, near, P, goal, dt) {
    var v0 = P.v0 * (a.v0mult || 1);
    var neigh = filterVisible(a, raw, near);

    var nrx = 0, nry = 0;
    for (var j = 0; j < neigh.length; j++) {
      var b = neigh[j], dx = b.x - a.x, dy = b.y - a.y, d = hypot(dx, dy);
      if (d < EPS) continue;
      var l = a.r + b.r, mag = P.sNR * Math.exp((l - d) / P.rNR);
      nrx += -(dx / d) * mag; nry += -(dy / d) * mag;
    }
    var brx = 0, bry = 0;
    for (var k = 0; k < near.length; k++) {
      var nw = near[k], dvx = nw.cx - a.x, dvy = nw.cy - a.y, dd = nw.d;
      if (dd < EPS) continue;
      var R = -P.sGR * Math.exp((a.r - dd) / P.rGR);
      brx += (dvx / dd) * R; bry += (dvy / dd) * R;
    }
    var gdx = goal.x - a.x, gdy = goal.y - a.y, gl = hypot(gdx, gdy);
    var desx = gl < EPS ? a.ox : gdx / gl, desy = gl < EPS ? a.oy : gdy / gl;
    var sx = desx + nrx + brx, sy = desy + nry + bry, sl = hypot(sx, sy);
    var dirx = sl < EPS ? a.ox : sx / sl, diry = sl < EPS ? a.oy : sy / sl;

    var spacing = Infinity;
    for (var m = 0; m < neigh.length; m++) {
      var b2 = neigh[m], px = b2.x - a.x, py = b2.y - a.y;
      if (dirx * px + diry * py < 0) continue;
      var lat = Math.abs((-diry) * px + dirx * py), ll = a.r + b2.r;
      if (lat > ll) continue;
      var sgap = hypot(px, py) - ll; if (sgap < spacing) spacing = sgap;
    }
    var speed = Math.min(Math.max(spacing / P.T, 0), v0);
    a.ndx = dirx; a.ndy = diry; a.nvx = dirx * speed; a.nvy = diry * speed;
    a.nx = a.x + a.nvx * dt; a.ny = a.y + a.nvy * dt;
  }

  // ---- SFM -----------------------------------------------------------------
  function sfmForce(p1x, p1y, p2x, p2y, A, B, radius, velx, vely, P) {
    var dx = p1x - p2x, dy = p1y - p2y, dist = hypot(dx, dy); if (dist < EPS) dist = EPS;
    var nx = dx / dist, ny = dy / dist;            // p2 → p1
    var tx = -ny, ty = nx;                         // tangent
    var push = A * Math.exp((radius - dist) / B), fric = 0;
    if (dist < radius) {
      push += P.k * (radius - dist);
      fric = P.kappa * (radius - dist) * (velx * tx + vely * ty);
    }
    return { x: nx * push + tx * fric, y: ny * push + ty * fric };
  }
  function sfmCompute(a, raw, near, P, goal, dt) {
    var v0 = P.v0 * (a.v0mult || 1);
    var e0x = goal.x - a.x, e0y = goal.y - a.y, el = hypot(e0x, e0y);
    if (el < EPS) { e0x = a.ox; e0y = a.oy; } else { e0x /= el; e0y /= el; }
    // 구동력(가속도)
    var fx = (e0x * v0 - a.vx) / P.tau, fy = (e0y * v0 - a.vy) / P.tau;
    // 이웃 반발력 (가림 없음) / mass
    var rx = 0, ry = 0;
    for (var j = 0; j < raw.length; j++) {
      var b = raw[j];
      var F = sfmForce(a.x, a.y, b.x, b.y, P.A, P.B, a.r + b.r, b.vx - a.vx, b.vy - a.vy, P);
      rx += F.x; ry += F.y;
    }
    fx += rx / P.mass; fy += ry / P.mass;
    // 벽 반발력 / mass
    var ox = 0, oy = 0;
    for (var k = 0; k < near.length; k++) {
      var nw = near[k];
      var Fo = sfmForce(a.x, a.y, nw.cx, nw.cy, P.Aobst, P.B, a.r, a.vx, a.vy, P);
      ox += Fo.x; oy += Fo.y;
    }
    fx += ox / P.mass; fy += oy / P.mass;
    // 적분
    var nvx = a.vx + fx * dt, nvy = a.vy + fy * dt;
    var sp = hypot(nvx, nvy), cap = P.clampV || 3.2;
    if (sp > cap) { nvx = nvx / sp * cap; nvy = nvy / sp * cap; }
    a.nvx = nvx; a.nvy = nvy; a.nx = a.x + nvx * dt; a.ny = a.y + nvy * dt;
    var ml = hypot(nvx, nvy); a.ndx = ml < EPS ? a.ox : nvx / ml; a.ndy = ml < EPS ? a.oy : nvy / ml;
  }

  // ---- AVM -----------------------------------------------------------------
  function avmInfluenceDir(d1x, d1y, px, py) {
    var ox = -d1y, oy = d1x, ol = hypot(ox, oy); if (ol > EPS) { ox /= ol; oy /= ol; }
    var align = ox * px + oy * py, ix = ox, iy = oy;
    if (Math.abs(align) < 1e-4) { if (Math.random() < 0.5) { ix = -ox; iy = -oy; } }
    else if (align > 0) { ix = -ox; iy = -oy; }
    return { x: ix, y: iy };
  }
  function avmCompute(a, raw, near, P, goal, dt) {
    var v0 = P.v0 * (a.v0mult || 1);
    var neigh = filterVisible(a, raw, near);
    var d1x = goal.x - a.x, d1y = goal.y - a.y, dl = hypot(d1x, d1y);
    if (dl < EPS) { d1x = a.ox; d1y = a.oy; } else { d1x /= dl; d1y /= dl; }

    // 예측 기반 이웃 반발
    var nrx = 0, nry = 0;
    for (var j = 0; j < neigh.length; j++) {
      var b = neigh[j], dx = b.x - a.x, dy = b.y - a.y, d = hypot(dx, dy); if (d < EPS) continue;
      var epx = dx / d, epy = dy / d, adjusted = d - (a.r + b.r);
      var inPerc = (d1x * epx + d1y * epy) >= 0 || (a.ox * epx + a.oy * epy) >= 0;
      if (!inPerc) continue;
      var sGap = ((a.vx - b.vx) * epx + (a.vy - b.vy) * epy) * P.antiT;
      var Rd = adjusted - sGap; if (Rd < 0) Rd = 0;
      var alignF = 1 + 0.5 * (1 - (d1x * b.ox + d1y * b.oy));
      var strength = P.sNR * alignF * Math.exp(-Rd / P.rNR);
      var newepx = dx + b.vx * P.antiT, newepy = dy + b.vy * P.antiT;
      var infl = avmInfluenceDir(d1x, d1y, newepx, newepy);
      nrx += infl.x * strength; nry += infl.y * strength;
    }
    var sx = d1x + nrx, sy = d1y + nry, sl = hypot(sx, sy);
    var calcx = sl < EPS ? a.ox : sx / sl, calcy = sl < EPS ? a.oy : sy / sl;

    // 반응시간 방향 평활 (Eq.7)
    var desDotCalc = d1x * calcx + d1y * calcy, desDotAct = d1x * a.ox + d1y * a.oy;
    var dirx, diry;
    if (desDotCalc * desDotAct < 0) { dirx = calcx; diry = calcy; }
    else {
      var dvx = (calcx - a.ox) / P.reactT, dvy = (calcy - a.oy) / P.reactT;
      dirx = a.ox + dvx * dt; diry = a.oy + dvy * dt;
    }
    var dn = hypot(dirx, diry); if (dn < EPS) { dirx = a.ox; diry = a.oy; } else { dirx /= dn; diry /= dn; }

    // 간격(코리도, buffer 0.02) → 최적속도
    var spacing = Infinity;
    for (var m = 0; m < neigh.length; m++) {
      var b2 = neigh[m], px = b2.x - a.x, py = b2.y - a.y;
      if (dirx * px + diry * py < 0) continue;
      var lat = Math.abs((-diry) * px + dirx * py), ll = a.r + b2.r + 0.02;
      if (lat > ll) continue;
      var sgap = hypot(px, py) - ll; if (sgap < spacing) spacing = sgap;
    }
    var speed = Math.min(Math.max(spacing / P.T, 0), v0);

    // 벽 회피(into-wall 성분 제거 + pushout)
    var crit = P.wallBuf + a.r;
    for (var w = 0; w < near.length; w++) {
      var nw = near[w]; if (nw.d > crit) continue;
      var ex = (a.x - nw.cx), ey = (a.y - nw.cy), en = nw.d || EPS; ex /= en; ey /= en;
      var dot = dirx * ex + diry * ey;
      if (dot < 0) { dirx = dirx - ex * dot + ex * P.pushout; diry = diry - ey * dot + ey * P.pushout; }
    }
    var fn = hypot(dirx, diry); if (fn > EPS) { dirx /= fn; diry /= fn; }

    a.ndx = dirx; a.ndy = diry; a.nvx = dirx * speed; a.nvy = diry * speed;
    a.nx = a.x + a.nvx * dt; a.ny = a.y + a.nvy * dt;
  }

  // ---- BR (Bae–Ryou) : SFM 기반 + 복사력(불 회피) + 연기력(연기 안→밖 밀침 / 회피) ----
  function brCompute(a, raw, near, P, goal, dt, sim) {
    var v0 = P.v0 * (a.v0mult || 1);
    // SFM 기반(구동 + 이웃 + 벽)
    var e0x = goal.x - a.x, e0y = goal.y - a.y, el = hypot(e0x, e0y);
    if (el < EPS) { e0x = a.ox; e0y = a.oy; } else { e0x /= el; e0y /= el; }
    var fx = (e0x * v0 - a.vx) / P.tau, fy = (e0y * v0 - a.vy) / P.tau;
    for (var j = 0; j < raw.length; j++) {
      var b = raw[j], F = sfmForce(a.x, a.y, b.x, b.y, P.A, P.B, a.r + b.r, b.vx - a.vx, b.vy - a.vy, P);
      fx += F.x / P.mass; fy += F.y / P.mass;
    }
    for (var k = 0; k < near.length; k++) {
      var nw = near[k], Fo = sfmForce(a.x, a.y, nw.cx, nw.cy, P.Aobst, P.B, a.r, a.vx, a.vy, P);
      fx += Fo.x / P.mass; fy += Fo.y / P.mass;
    }
    // 복사력: 불을 보고 피해감 (비등방성, 점원 1/r² 열유속)
    var fires = sim.fires || [];
    for (var fi = 0; fi < fires.length; fi++) {
      var fr = fires[fi], dx = a.x - fr.x, dy = a.y - fr.y, r = hypot(dx, dy); if (r < 0.3) r = 0.3;
      var nx = dx / r, ny = dy / r;                       // 화원 → 사람
      var q = (P.chiR * P.Q) / (4 * Math.PI * r * r);     // 복사 열유속 kW/m²
      var over = q - P.qth; if (over < 0) over = 0;        // 임계 이상만 작용
      var mag = P.SR * Math.min(over / P.qref, 3);
      var facing = a.ox * (-nx) + a.oy * (-ny);           // 불을 향할수록 강화(비등방)
      mag *= 0.4 + 0.6 * Math.max(facing, 0);
      fx += nx * mag; fy += ny * mag;
    }
    // 연기력: 외부=회피(경계 밖), 내부=바깥으로 밀침
    var sm = sim.smoke || [];
    for (var si = 0; si < sm.length; si++) {
      var s = sm[si], ax2 = a.x - s.cx, ay2 = a.y - s.cy, dist = hypot(ax2, ay2); if (dist < EPS) dist = EPS;
      var ux = ax2 / dist, uy = ay2 / dist, d = dist - s.r; // 연기 경계까지 부호거리(<0=내부)
      if (d > 0) { if (d < 3) { var mo = (v0 / P.tau) * Math.exp(-d) * P.sOut; fx += ux * mo; fy += uy * mo; } }
      else { var mi = P.sIn * v0; fx += ux * mi; fy += uy * mi; }
    }
    // 적분(2차)
    var nvx = a.vx + fx * dt, nvy = a.vy + fy * dt;
    var sp = hypot(nvx, nvy), cap = P.clampV || 3.2;
    if (sp > cap) { nvx = nvx / sp * cap; nvy = nvy / sp * cap; }
    a.nvx = nvx; a.nvy = nvy; a.nx = a.x + nvx * dt; a.ny = a.y + nvy * dt;
    var ml = hypot(nvx, nvy); a.ndx = ml < EPS ? a.ox : nvx / ml; a.ndy = ml < EPS ? a.oy : nvy / ml;
  }

  var MODELS = {
    cfsm: {
      key: "cfsm", label: "CFSM", full: "Collision-Free Speed Model",
      cutOff: 3, sub: 5, dt: 0.02, compute: cfsmCompute,
      defaults: { v0: 1.2, T: 1.0, radius: 0.2, sNR: 8.0, rNR: 0.1, sGR: 5.0, rGR: 0.02 },
      sliders: [
        { key: "v0", label: "희망 속도 v0", unit: "m/s", min: 0.4, max: 2.2, step: 0.05 },
        { key: "T", label: "시간 간격 T", unit: "s", min: 0.2, max: 3.0, step: 0.02 },
        { key: "sNR", label: "이웃 반발 강도", unit: "", min: 0, max: 20, step: 0.5 },
        { key: "rNR", label: "이웃 반발 범위", unit: "m", min: 0.02, max: 0.6, step: 0.01 },
        { pop: true, label: "인원 수", unit: "명", min: 4, max: 120, step: 1 }
      ]
    },
    sfm: {
      key: "sfm", label: "SFM", full: "Social Force Model",
      cutOff: 2.5, sub: 8, dt: 0.0125, compute: sfmCompute,
      defaults: { v0: 0.8, radius: 0.3, mass: 80, tau: 0.5, A: 2000, Aobst: 2000, B: 0.08, k: 120000, kappa: 240000, clampV: 3.2 },
      sliders: [
        { key: "v0", label: "희망 속도 v0", unit: "m/s", min: 0.3, max: 2.0, step: 0.05 },
        { key: "A", label: "반발 강도 A", unit: "", min: 500, max: 4000, step: 50 },
        { key: "B", label: "반발 범위 B", unit: "m", min: 0.02, max: 0.3, step: 0.01 },
        { key: "tau", label: "반응 시간 τ", unit: "s", min: 0.1, max: 1.5, step: 0.05 },
        { pop: true, label: "인원 수", unit: "명", min: 4, max: 100, step: 1 }
      ]
    },
    avm: {
      key: "avm", label: "AVM", full: "Anticipation Velocity Model",
      cutOff: 3, sub: 5, dt: 0.02, compute: avmCompute,
      defaults: { v0: 1.2, T: 1.06, radius: 0.2, sNR: 8.0, rNR: 0.1, antiT: 1.0, reactT: 0.3, wallBuf: 0.1, pushout: 0.3 },
      sliders: [
        { key: "v0", label: "희망 속도 v0", unit: "m/s", min: 0.4, max: 2.2, step: 0.05 },
        { key: "T", label: "시간 간격 T", unit: "s", min: 0.2, max: 3.0, step: 0.02 },
        { key: "sNR", label: "이웃 반발 강도", unit: "", min: 0, max: 20, step: 0.5 },
        { key: "antiT", label: "예측 시간 ta", unit: "s", min: 0, max: 3.0, step: 0.05 },
        { key: "reactT", label: "반응 시간", unit: "s", min: 0.05, max: 1.0, step: 0.05 },
        { pop: true, label: "인원 수", unit: "명", min: 4, max: 120, step: 1 }
      ]
    },
    br: {
      key: "br", label: "BR", full: "Bae–Ryou 모델 (SFM + 화재·연기력)",
      cutOff: 2.5, sub: 8, dt: 0.0125, compute: brCompute, fireSmoke: true,
      defaults: {
        v0: 1.0, radius: 0.3, mass: 80, tau: 0.5, A: 2000, Aobst: 2000, B: 0.08, k: 120000, kappa: 240000, clampV: 3.2,
        chiR: 0.3, Q: 2000, qth: 2.5, qref: 5, SR: 4.0, sOut: 1.0, sIn: 1.2
      },
      sliders: [
        { key: "v0", label: "희망 속도 v0", unit: "m/s", min: 0.3, max: 2.0, step: 0.05 },
        { key: "SR", label: "복사력 강도(불 회피)", unit: "", min: 0, max: 10, step: 0.5 },
        { key: "sIn", label: "연기 밀침(안→밖)", unit: "", min: 0, max: 4, step: 0.1 },
        { key: "sOut", label: "연기 회피(경계)", unit: "", min: 0, max: 4, step: 0.1 },
        { pop: true, label: "인원 수", unit: "명", min: 4, max: 90, step: 1 }
      ]
    }
  };

  /* ----------------------------------------------------------- the Sim core */
  function makeSim(canvas, cfg) {
    var ctx = canvas.getContext("2d");
    var model = MODELS[cfg.model || "cfsm"];
    var sim = {
      canvas: canvas, ctx: ctx, cfg: cfg, model: model,
      walls: cfg.walls || [],
      exits: (cfg.exits || []).map(function (e) { return { seg: e.seg }; }),
      fires: cfg.fires || [],
      agents: [], specs: cfg.spawns || [],
      world: cfg.world,
      runCycle: !!cfg.runCycle,
      active: false,
      t: 0, elapsed: 0, evacuated: 0, resetAt: -1, lastProgressT: 0, lastEvacCount: 0,
      stats: { evacuated: 0, remaining: 0, elapsed: 0, density: 0, speed: 0 },
      params: Object.assign({}, model.defaults, cfg.params || {}),
      view: { s: 1, ox: 0, oy: 0, w: 1, h: 1, dpr: 1 }
    };
    sim._baseV0 = sim.params.v0;

    function buildSlots(spec) {
      var r = spec.rect, area = r[2] * r[3];
      var minGap = 2 * (sim.params.radius || 0.2) + 0.25;
      var gap = Math.max(minGap, Math.sqrt(area / Math.max(1, spec.count)) * 0.95);
      var cols = Math.max(1, Math.floor(r[2] / gap)), rows = Math.max(1, Math.floor(r[3] / gap));
      var slots = [];
      for (var iy = 0; iy < rows; iy++) for (var ix = 0; ix < cols; ix++)
        slots.push([r[0] + (ix + 0.5) * (r[2] / cols), r[1] + (iy + 0.5) * (r[3] / rows)]);
      while (slots.length < spec.count) slots.push([rand(r[0] + 0.4, r[0] + r[2] - 0.4), rand(r[1] + 0.4, r[1] + r[3] - 0.4)]);
      spec._slots = slots; spec._slotN = 0;
    }
    function spawnInto(a, spec) {
      if (!spec._slots) buildSlots(spec);
      var p = spec._slots[spec._slotN % spec._slots.length]; spec._slotN++;
      a.x = p[0] + rand(-0.06, 0.06); a.y = p[1] + rand(-0.06, 0.06);
      a.px = a.x; a.py = a.y; a.vx = 0; a.vy = 0; a.ox = 1; a.oy = 0;
      a.ti = 0; a.alive = true;
      a.route = spec.route; a.loop = !!spec.route.loop;
      a.exitIdx = spec.exitIdx || null; a.color = spec.color;
      a.v0mult = spec.v0 ? spec.v0 / sim._baseV0 : 1;
      a.r = spec.radius || sim.params.radius;
      a.grp = spec.group; a.spec = spec;
    }
    function wireChain() {
      if (!cfg.chain) return;
      var ca = sim.agents.filter(function (a) { return a.grp === cfg.chain.group; });
      for (var i = 1; i < ca.length; i++) ca[i].ahead = ca[i - 1];
    }
    sim.specs.forEach(function (spec) { for (var i = 0; i < spec.count; i++) { var a = {}; spawnInto(a, spec); sim.agents.push(a); } });
    wireChain();
    sim.baseTotal = sim.agents.length;

    sim.setPopulation = function (total) {
      var base = sim.baseTotal || 1, newAgents = [];
      sim.specs.forEach(function (spec) {
        spec._slots = null; spec._slotN = 0;
        var n = Math.max(1, Math.round(spec.count * total / base));
        for (var i = 0; i < n; i++) { var a = {}; spawnInto(a, spec); newAgents.push(a); }
      });
      sim.agents = newAgents; wireChain();
    };
    sim.setModel = function (key) {
      sim.model = MODELS[key];
      sim.params = Object.assign({}, sim.model.defaults, cfg.params || {});
      sim._baseV0 = sim.params.v0;
      var total = sim.agents.length;
      sim.specs.forEach(function (spec) { spec._slots = null; spec._slotN = 0; });
      var newAgents = [];
      sim.specs.forEach(function (spec) { for (var i = 0; i < spec.count; i++) { var a = {}; spawnInto(a, spec); newAgents.push(a); } });
      sim.agents = newAgents; wireChain();
      sim.baseTotal = sim.agents.length;
      sim.t = 0; sim.elapsed = 0; sim.evacuated = 0; sim.resetAt = -1; sim.lastEvacCount = 0; sim.lastProgressT = 0;
      if (sim._injFire) { sim.fires = []; sim._injFire = false; }
      sim.smoke = null;
    };

    /* ---- 공간 그리드 이웃 탐색 ---- */
    function buildGrid() {
      var cell = sim.model.cutOff, map = {}, ax = sim.agents;
      for (var i = 0; i < ax.length; i++) {
        var a = ax[i]; if (!a.alive) continue;
        var k = Math.floor(a.x / cell) + "," + Math.floor(a.y / cell);
        (map[k] || (map[k] = [])).push(a);
      }
      return { cell: cell, map: map };
    }
    function neighborsOf(a, grid) {
      var cell = grid.cell, out = [], cx = Math.floor(a.x / cell), cy = Math.floor(a.y / cell);
      for (var gx = cx - 1; gx <= cx + 1; gx++) for (var gy = cy - 1; gy <= cy + 1; gy++) {
        var arr = grid.map[gx + "," + gy]; if (!arr) continue;
        for (var i = 0; i < arr.length; i++) {
          var b = arr[i]; if (b === a || !b.alive) continue;
          if (hypot(b.x - a.x, b.y - a.y) < sim.model.cutOff) out.push(b);
        }
      }
      return out;
    }
    function nearestExitPoint(a) {
      var best = null, bd = 1e9, list = a.exitIdx ? a.exitIdx.map(function (k) { return sim.exits[k]; }) : sim.exits;
      for (var i = 0; i < list.length; i++) {
        var s = list[i].seg, c = closestOnSeg(a.x, a.y, s[0], s[1], s[2], s[3]), d = hypot(c.x - a.x, c.y - a.y);
        if (d < bd) { bd = d; best = { x: c.x, y: c.y, d: d }; }
      }
      return best;
    }
    function goalOf(a) {
      var t = a.route[a.ti];
      if (t.follow) return a.ahead ? { x: a.ahead.x, y: a.ahead.y, exit: false } : { x: a.x, y: a.y, exit: false };
      if (t.exit) { var e = nearestExitPoint(a); return { x: e.x, y: e.y, exit: true, d: e.d }; }
      return { x: t.x, y: t.y, exit: false };
    }

    function substep(dt) {
      var ax = sim.agents, walls = sim.walls, n = ax.length, grid = buildGrid(), P = sim.params, compute = sim.model.compute, cutOff = sim.model.cutOff;
      for (var i = 0; i < n; i++) {
        var a = ax[i]; if (!a.alive) continue;
        var near = [];
        for (var w = 0; w < walls.length; w++) {
          var s = walls[w], c = closestOnSeg(a.x, a.y, s[0], s[1], s[2], s[3]), d = hypot(c.x - a.x, c.y - a.y);
          if (d < cutOff) near.push({ seg: s, cx: c.x, cy: c.y, d: d });
        }
        var raw = neighborsOf(a, grid);
        var goal = goalOf(a); a._goal = goal;
        compute(a, raw, near, P, goal, dt, sim);
      }
      for (var m = 0; m < n; m++) {
        var a4 = ax[m]; if (!a4.alive) continue;
        a4.px = a4.x; a4.py = a4.y;
        a4.x = a4.nx; a4.y = a4.ny; a4.ox = a4.ndx; a4.oy = a4.ndy; a4.vx = a4.nvx; a4.vy = a4.nvy;
        if (a4.x < 0.1) a4.x = 0.1; if (a4.x > sim.world.w - 0.1) a4.x = sim.world.w - 0.1;
        if (a4.y < 0.1) a4.y = 0.1; if (a4.y > sim.world.h - 0.1) a4.y = sim.world.h - 0.1;
        var t = a4.route[a4.ti], g = a4._goal;
        if (t && !t.exit && !t.follow) {
          if (hypot(g.x - a4.x, g.y - a4.y) < (t.r || 0.8)) { a4.ti++; if (a4.ti >= a4.route.length) a4.ti = a4.loop ? 0 : a4.route.length - 1; }
        } else if (g && g.exit && g.d < a4.r + 0.45) {
          if (sim.runCycle) { a4.alive = false; sim.evacuated++; } else { spawnInto(a4, a4.spec); }
        }
      }
    }

    function updateSmoke(frameDt) {
      if (sim.model.fireSmoke) {
        if (!sim.fires.length) { sim.fires = [{ x: sim.world.w * 0.32, y: sim.world.h * 0.34, r: 1.0 }]; sim._injFire = true; }
        if (!sim.smoke) sim.smoke = sim.fires.map(function (f) { return { cx: f.x, cy: f.y, r: 1.2 }; });
        var rmax = Math.min(sim.world.w, sim.world.h) * 0.22;
        sim.smoke.forEach(function (s) {
          var target = rmax * (0.8 + 0.2 * Math.sin(sim.t * 0.5));   // 연기 확산(호흡)
          s.r += (target - s.r) * Math.min(1, frameDt * 0.6);
        });
      } else {
        if (sim._injFire) { sim.fires = []; sim._injFire = false; }
        sim.smoke = null;
      }
    }

    function step() {
      var SUB = sim.model.sub, dt = sim.model.dt;
      updateSmoke(SUB * dt);
      for (var s = 0; s < SUB; s++) { sim.t += dt; substep(dt); }
      var ax = sim.agents, n = ax.length, alive = 0, dsum = 0, vsum = 0;
      for (var i = 0; i < n; i++) {
        var a = ax[i]; if (!a.alive) continue; alive++; vsum += hypot(a.vx, a.vy);
        var c = 0;
        for (var j = 0; j < n; j++) { if (j === i) continue; var b = ax[j]; if (!b.alive) continue; if (hypot(a.x - b.x, a.y - b.y) < 1.0) c++; }
        dsum += c / Math.PI;
      }
      sim.stats.remaining = alive; sim.stats.evacuated = sim.evacuated;
      sim.stats.density = alive ? dsum / alive : 0; sim.stats.speed = alive ? vsum / alive : 0;

      if (sim.runCycle) {
        if (sim.evacuated !== sim.lastEvacCount) { sim.lastEvacCount = sim.evacuated; sim.lastProgressT = sim.t; }
        if (alive > 0 && (sim.t - sim.lastProgressT) > 14) {
          for (var sg = 0; sg < n; sg++) { var ag = ax[sg]; if (ag.alive) { ag.alive = false; sim.evacuated++; } }
          alive = 0; sim.stats.remaining = 0; sim.stats.evacuated = sim.evacuated; sim.lastProgressT = sim.t;
        }
        if (alive > 0) { sim.elapsed += SUB * dt; sim.resetAt = -1; }
        else {
          if (sim.resetAt < 0) sim.resetAt = sim.t + 1.8;
          if (sim.t >= sim.resetAt) {
            sim.specs.forEach(function (spec) { spec._slots = null; spec._slotN = 0; });
            var gi = 0;
            sim.specs.forEach(function (spec) { for (var q = 0; q < spec.count; q++) { if (sim.agents[gi]) spawnInto(sim.agents[gi], spec); gi++; } });
            wireChain();
            sim.elapsed = 0; sim.evacuated = 0; sim.resetAt = -1; sim.lastEvacCount = 0; sim.lastProgressT = sim.t;
          }
        }
        sim.stats.elapsed = sim.elapsed;
      }
    }

    /* ---- view / draw ---- */
    function resize() {
      var dpr = Math.min(2, window.devicePixelRatio || 1), w = canvas.clientWidth, h = canvas.clientHeight;
      if (!w || !h) return;
      canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
      var s = Math.min((w - 20) / sim.world.w, (h - 20) / sim.world.h);
      sim.view.s = s; sim.view.ox = (w - sim.world.w * s) / 2; sim.view.oy = (h - sim.world.h * s) / 2;
      sim.view.w = w; sim.view.h = h; sim.view.dpr = dpr;
    }
    function X(x) { return sim.view.ox + x * sim.view.s; }
    function Y(y) { return sim.view.oy + y * sim.view.s; }
    function draw() {
      var v = sim.view, c = ctx;
      c.setTransform(v.dpr, 0, 0, v.dpr, 0, 0); c.clearRect(0, 0, v.w, v.h);
      c.strokeStyle = "rgba(30,42,60,0.06)"; c.lineWidth = 1; c.beginPath();
      for (var gx = 0; gx <= sim.world.w; gx += 2) { c.moveTo(X(gx), Y(0)); c.lineTo(X(gx), Y(sim.world.h)); }
      for (var gy = 0; gy <= sim.world.h; gy += 2) { c.moveTo(X(0), Y(gy)); c.lineTo(X(sim.world.w), Y(gy)); }
      c.stroke();
      // 연기 영역(BR)
      if (sim.smoke) {
        for (var sk = 0; sk < sim.smoke.length; sk++) {
          var sm = sim.smoke[sk], pr = sm.r * v.s;
          var sg = c.createRadialGradient(X(sm.cx), Y(sm.cy), pr * 0.15, X(sm.cx), Y(sm.cy), pr);
          sg.addColorStop(0, "rgba(70,72,78,0.42)"); sg.addColorStop(0.7, "rgba(96,98,104,0.26)"); sg.addColorStop(1, "rgba(110,112,118,0)");
          c.fillStyle = sg; c.beginPath(); c.arc(X(sm.cx), Y(sm.cy), pr, 0, 7); c.fill();
        }
      }
      for (var fi = 0; fi < sim.fires.length; fi++) {
        var f = sim.fires[fi], pr = (f.r + 0.5 + Math.sin(sim.t * 4) * 0.35) * v.s;
        var grad = c.createRadialGradient(X(f.x), Y(f.y), 1, X(f.x), Y(f.y), pr * 2.4);
        grad.addColorStop(0, "rgba(255,120,40,0.85)"); grad.addColorStop(0.4, "rgba(255,70,30,0.35)"); grad.addColorStop(1, "rgba(255,60,30,0)");
        c.fillStyle = grad; c.beginPath(); c.arc(X(f.x), Y(f.y), pr * 2.4, 0, 7); c.fill();
        c.fillStyle = "#ffb347"; c.beginPath(); c.arc(X(f.x), Y(f.y), Math.max(2, pr * 0.5), 0, 7); c.fill();
      }
      c.strokeStyle = "#54606f"; c.lineWidth = Math.max(1.4, 0.16 * v.s); c.lineCap = "round"; c.beginPath();
      for (var w2 = 0; w2 < sim.walls.length; w2++) { var s2 = sim.walls[w2]; c.moveTo(X(s2[0]), Y(s2[1])); c.lineTo(X(s2[2]), Y(s2[3])); }
      c.stroke();
      for (var e = 0; e < sim.exits.length; e++) {
        var ex = sim.exits[e].seg; c.save();
        c.shadowColor = "rgba(15,164,90,0.55)"; c.shadowBlur = 14; c.strokeStyle = "#0FA45A";
        c.lineWidth = Math.max(2.4, 0.24 * v.s); c.beginPath(); c.moveTo(X(ex[0]), Y(ex[1])); c.lineTo(X(ex[2]), Y(ex[3])); c.stroke(); c.restore();
      }
      for (var i = 0; i < sim.agents.length; i++) {
        var a = sim.agents[i]; if (!a.alive) continue;
        var rr = Math.max(1.8, a.r * v.s);
        c.strokeStyle = a.color; c.globalAlpha = 0.22; c.lineWidth = rr * 0.9;
        c.beginPath(); c.moveTo(X(a.px), Y(a.py)); c.lineTo(X(a.x), Y(a.y)); c.stroke(); c.globalAlpha = 1;
        c.fillStyle = a.color; c.beginPath(); c.arc(X(a.x), Y(a.y), rr, 0, 7); c.fill();
      }
    }

    sim.step = step; sim.draw = draw; sim.resize = resize;
    resize(); draw();
    return sim;
  }

  /* ============================================================ SCENARIOS / VARIANTS */
  // 라이트 배경에서 잘 보이는 에이전트 색
  var PED = "#3f4a5c", CYAN = "#2563eb", AMBER = "#d97706", EMBER = "#ea5a16", GREEN = "#0a8f53";

  function S_hero() {
    var W = 56, H = 32, walls = room(1, 1, 54, 30, { top: [26, 30], bottom: [26, 30], left: [14, 18], right: [14, 18] });
    walls.push([19, 1, 19, 31]); walls.push([37, 1, 37, 31]);
    [[9, 9], [9, 22], [46, 9], [46, 22], [28, 8], [28, 24]].forEach(function (p) { walls = walls.concat(rect(p[0], p[1], 1.4, 1.4)); });
    var exits = [{ seg: [26, 1, 30, 1] }, { seg: [55, 14, 55, 18] }, { seg: [26, 31, 30, 31] }, { seg: [1, 14, 1, 18] }];
    var spawns = [
      { rect: [3, 3, 14, 26], group: "L", count: 44, color: PED, route: [{ exit: true }], exitIdx: [3] },
      { rect: [39, 3, 14, 26], group: "R", count: 44, color: PED, route: [{ exit: true }], exitIdx: [1] },
      { rect: [21, 3, 14, 11], group: "CT", count: 26, color: PED, route: [{ exit: true }], exitIdx: [0] },
      { rect: [21, 18, 14, 11], group: "CB", count: 26, color: PED, route: [{ exit: true }], exitIdx: [2] }
    ];
    return { world: { w: W, h: H }, walls: walls, exits: exits, fires: [{ x: 28, y: 16, r: 1.2 }], spawns: spawns, runCycle: true };
  }

  function routing_basic() {
    return { world: { w: 24, h: 16 }, walls: room(1, 1, 22, 14, { right: [7, 9] }), exits: [{ seg: [23, 7, 23, 9] }], spawns: [
      { rect: [2, 6, 4, 4], group: "short", count: 20, color: PED, route: [{ exit: true }] },
      { rect: [2, 10, 4, 4], group: "detour", count: 16, color: AMBER, route: [{ x: 5, y: 3 }, { x: 19, y: 3 }, { x: 21, y: 8 }, { exit: true }] }
    ] };
  }
  function routing_three() {
    return { world: { w: 24, h: 16 }, walls: room(1, 1, 22, 14, { right: [7, 9] }), exits: [{ seg: [23, 7, 23, 9] }], spawns: [
      { rect: [2, 7, 4, 2], group: "mid", count: 14, color: PED, route: [{ exit: true }] },
      { rect: [2, 3, 4, 3], group: "up", count: 12, color: AMBER, route: [{ x: 5, y: 3 }, { x: 19, y: 3 }, { x: 21, y: 8 }, { exit: true }] },
      { rect: [2, 11, 4, 3], group: "down", count: 12, color: CYAN, route: [{ x: 5, y: 13 }, { x: 19, y: 13 }, { x: 21, y: 8 }, { exit: true }] }
    ] };
  }
  function bottleneck_double() {
    var walls = room(1, 1, 26, 10, { right: [5, 7] });
    walls.push([10, 1, 10, 5]); walls.push([10, 7, 10, 11]); walls.push([18, 1, 18, 5]); walls.push([18, 7, 18, 11]);
    return { world: { w: 28, h: 12 }, walls: walls, exits: [{ seg: [27, 5, 27, 7] }], spawns: [
      { rect: [2, 2, 6, 8], group: "p", count: 44, color: PED, route: [{ x: 10, y: 6 }, { x: 18, y: 6 }, { exit: true }] }] };
  }
  function bottleneck_single() {
    var walls = room(1, 1, 26, 10, { right: [5, 7] });
    walls.push([13, 1, 13, 5]); walls.push([13, 7, 13, 11]);
    return { world: { w: 28, h: 12 }, walls: walls, exits: [{ seg: [27, 5, 27, 7] }], spawns: [
      { rect: [2, 2, 8, 8], group: "p", count: 44, color: PED, route: [{ x: 13, y: 6 }, { exit: true }] }] };
  }
  function corner_90() {
    var walls = [[2, 1, 2, 16], [2, 16, 17, 16], [6, 1, 6, 12], [6, 12, 17, 12]];
    return { world: { w: 18, h: 18 }, walls: walls, exits: [{ seg: [17, 12, 17, 16] }], pad: 0.4, spawns: [
      { rect: [2.4, 1.5, 3.2, 4], group: "p", count: 30, color: PED, route: [{ x: 4, y: 13.8 }, { x: 14, y: 14 }, { exit: true }] }] };
  }
  function corner_u() {
    var walls = [[2, 1, 2, 17], [6, 1, 6, 13], [6, 13, 12, 13], [12, 1, 12, 13], [2, 17, 16, 17], [16, 1, 16, 17]];
    return { world: { w: 18, h: 18 }, walls: walls, exits: [{ seg: [12, 1, 16, 1] }], pad: 0.4, spawns: [
      { rect: [2.4, 1.5, 3.2, 4], group: "p", count: 28, color: PED, route: [{ x: 4, y: 15 }, { x: 14, y: 15 }, { x: 14, y: 4 }, { exit: true }] }] };
  }
  function lane_straight() {
    return { world: { w: 28, h: 10 }, walls: [[1, 1, 27, 1], [1, 9, 27, 9]], exits: [{ seg: [27, 1, 27, 9] }, { seg: [1, 1, 1, 9] }], spawns: [
      { rect: [1.5, 2, 4, 6], group: "A", count: 24, color: CYAN, route: [{ exit: true }], exitIdx: [0] },
      { rect: [22.5, 2, 4, 6], group: "B", count: 24, color: EMBER, route: [{ exit: true }], exitIdx: [1] }] };
  }
  function lane_L() {
    var walls = [[1, 8, 13, 8], [13, 8, 13, 21], [1, 4, 9, 4], [9, 4, 9, 21]];
    return { world: { w: 14, h: 22 }, walls: walls, exits: [{ seg: [9, 21, 13, 21] }, { seg: [1, 4, 1, 8] }], pad: 0.4, spawns: [
      { rect: [1.5, 4.5, 3.5, 3], group: "A", count: 13, color: CYAN, route: [{ x: 7, y: 6 }, { x: 11, y: 11 }, { x: 11, y: 18 }, { exit: true }], exitIdx: [0] },
      { rect: [9.5, 16, 3, 4], group: "B", count: 13, color: EMBER, route: [{ x: 11, y: 11 }, { x: 7, y: 6 }, { exit: true }], exitIdx: [1] }] };
  }
  function lane_cross() {
    var walls = rect(1, 1, 9, 9).concat(rect(15, 1, 8, 9)).concat(rect(1, 15, 9, 8)).concat(rect(15, 15, 8, 8));
    return { world: { w: 24, h: 24 }, walls: walls, exits: [{ seg: [10, 1, 14, 1] }, { seg: [10, 23, 14, 23] }, { seg: [1, 10, 1, 14] }, { seg: [23, 10, 23, 14] }], spawns: [
      { rect: [10.5, 20, 3, 3], group: "up", count: 12, color: CYAN, route: [{ x: 12, y: 12 }, { exit: true }], exitIdx: [0] },
      { rect: [10.5, 1.5, 3, 3], group: "dn", count: 12, color: EMBER, route: [{ x: 12, y: 12 }, { exit: true }], exitIdx: [1] },
      { rect: [20, 10.5, 3, 3], group: "lf", count: 12, color: GREEN, route: [{ x: 12, y: 12 }, { exit: true }], exitIdx: [2] },
      { rect: [1.5, 10.5, 3, 3], group: "rt", count: 12, color: AMBER, route: [{ x: 12, y: 12 }, { exit: true }], exitIdx: [3] }] };
  }
  function queue_single() {
    var walls = room(1, 1, 16, 14, { top: [8, 10] }); walls.push([7, 5, 8, 3]); walls.push([11, 5, 10, 3]);
    return { world: { w: 18, h: 16 }, walls: walls, exits: [{ seg: [8, 1, 10, 1] }], spawns: [
      { rect: [2.5, 9, 13, 5], group: "p", count: 28, color: PED, route: [{ x: 9, y: 6.5 }, { x: 9, y: 3.5 }, { exit: true }] }] };
  }
  function queue_two() {
    var walls = [[1, 1, 4, 1], [6, 1, 11, 1], [13, 1, 17, 1], [17, 1, 17, 15], [17, 15, 1, 15], [1, 15, 1, 1]];
    return { world: { w: 18, h: 16 }, walls: walls, exits: [{ seg: [4, 1, 6, 1] }, { seg: [11, 1, 13, 1] }], spawns: [
      { rect: [2.5, 8, 13, 6], group: "p", count: 30, color: PED, route: [{ exit: true }] }] };
  }
  function single_rect() {
    var walls = rect(2, 2, 16, 12).concat(rect(7, 5.5, 6, 5));
    var route = [{ x: 4.5, y: 4 }, { x: 15.5, y: 4 }, { x: 15.5, y: 12 }, { x: 4.5, y: 12 }]; route.loop = true;
    return { world: { w: 20, h: 16 }, walls: walls, exits: [], spawns: [{ rect: [3.2, 3, 12, 1.2], group: "p", count: 14, color: GREEN, route: route }] };
  }
  function single_circle() {
    var walls = circlePoly(10, 8, 7, 40).concat(circlePoly(10, 8, 4.2, 32));
    var route = circleWaypoints(10, 8, 5.6, 24); route.loop = true;
    return { world: { w: 20, h: 16 }, walls: walls, exits: [], spawns: [{ rect: [14.7, 7.3, 1.2, 1.4], group: "p", count: 14, color: GREEN, route: route }] };
  }
  function journey_serial() {
    var walls = room(1, 1, 25, 12, { right: [6, 8] });
    walls.push([9, 1, 9, 5]); walls.push([9, 8, 9, 13]); walls.push([17, 1, 17, 4]); walls.push([17, 7, 17, 13]);
    return { world: { w: 27, h: 14 }, walls: walls, exits: [{ seg: [26, 6, 26, 8] }], spawns: [
      { rect: [2, 3, 5, 8], group: "p", count: 26, color: PED, route: [{ x: 9, y: 6.5 }, { x: 13, y: 6 }, { x: 17, y: 5.5 }, { x: 22, y: 7 }, { exit: true }] }] };
  }
  function journey_merge() {
    var walls = room(1, 1, 25, 12, { right: [6, 8] });
    walls.push([12, 1, 12, 5]); walls.push([12, 8, 12, 13]);
    return { world: { w: 27, h: 14 }, walls: walls, exits: [{ seg: [26, 6, 26, 8] }], spawns: [
      { rect: [2, 2, 8, 3], group: "top", count: 14, color: CYAN, route: [{ x: 12, y: 6.5 }, { x: 20, y: 7 }, { exit: true }] },
      { rect: [2, 9, 8, 3], group: "bot", count: 14, color: EMBER, route: [{ x: 12, y: 6.5 }, { x: 20, y: 7 }, { exit: true }] }] };
  }
  function moti_high() {
    return { world: { w: 16, h: 16 }, walls: room(1, 1, 14, 14, { bottom: [7, 9] }), exits: [{ seg: [7, 15, 9, 15] }], fires: [{ x: 8, y: 3, r: 0.9 }], params: { v0: 1.8 }, spawns: [
      { rect: [2, 2, 12, 9], group: "p", count: 46, color: EMBER, route: [{ exit: true }] }] };
  }
  function moti_low() {
    return { world: { w: 16, h: 16 }, walls: room(1, 1, 14, 14, { bottom: [7, 9] }), exits: [{ seg: [7, 15, 9, 15] }], params: { v0: 0.8 }, spawns: [
      { rect: [2, 2, 12, 9], group: "p", count: 46, color: PED, route: [{ exit: true }] }] };
  }
  function compareBuild(half) {
    var walls = room(1, 1, 10.5, 12, { top: [4.5, 6.5] }).concat(room(12.5, 1, 10.5, 12, { top: [16.5, 18.5] }));
    walls.push([6.25 - half, 9, 6.25 - half, 6]); walls.push([6.25 + half, 9, 6.25 + half, 6]);
    walls.push([17.5 - half, 9, 17.5 - half, 6]); walls.push([17.5 + half, 9, 17.5 + half, 6]);
    return { world: { w: 24, h: 14 }, walls: walls, exits: [{ seg: [4.5, 1, 6.5, 1] }, { seg: [16.5, 1, 18.5, 1] }], spawns: [
      { rect: [2, 9, 8.5, 3], group: "A", count: 22, color: CYAN, v0: 1.2, route: [{ x: 6.25, y: 7 }, { exit: true }], exitIdx: [0] },
      { rect: [13.5, 9, 8.5, 3], group: "B", count: 22, color: EMBER, v0: 1.6, route: [{ x: 17.5, y: 7 }, { exit: true }], exitIdx: [1] }] };
  }
  function compare_narrow() { return compareBuild(0.9); }
  function compare_wide() { return compareBuild(2.2); }
  function steerBuild(loopPts) {
    var walls = rect(1, 1, 20, 12), lr = loopPts.slice(); lr.loop = true;
    var spawns = [{ rect: [loopPts[0].x - 0.3, loopPts[0].y - 0.3, 0.6, 0.6], group: "chain", count: 1, color: GREEN, v0: 1.25, route: lr }];
    for (var i = 0; i < 9; i++) spawns.push({ rect: [loopPts[0].x - 0.3 - i * 0.1, loopPts[0].y - 0.3, 0.5, 0.5], group: "chain", count: 1, color: i % 2 ? PED : CYAN, v0: 1.6, route: [{ follow: true }] });
    return { world: { w: 22, h: 14 }, walls: walls, exits: [], spawns: spawns, chain: { group: "chain" } };
  }
  function steer_rect() { return steerBuild([{ x: 5, y: 4 }, { x: 17, y: 4 }, { x: 18, y: 10 }, { x: 5, y: 10 }, { x: 4, y: 6 }]); }
  function steer_eight() { return steerBuild([{ x: 6, y: 4 }, { x: 16, y: 10 }, { x: 16, y: 4 }, { x: 6, y: 10 }]); }

  var VARIANTS = {
    routing:    [{ label: "1출구 · 2경로", build: routing_basic }, { label: "3갈래 분기", build: routing_three }],
    bottleneck: [{ label: "이중 병목", build: bottleneck_double }, { label: "단일 병목", build: bottleneck_single }],
    corner:     [{ label: "90° 코너", build: corner_90 }, { label: "U자 반환", build: corner_u }],
    lane:       [{ label: "직선 복도", build: lane_straight }, { label: "ㄱ자 복도", build: lane_L }, { label: "십자 교차로", build: lane_cross }],
    queue:      [{ label: "단일 창구", build: queue_single }, { label: "2 창구", build: queue_two }],
    singlefile: [{ label: "사각 링", build: single_rect }, { label: "원형 링", build: single_circle }],
    journey:    [{ label: "3실 직렬", build: journey_serial }, { label: "Y자 합류", build: journey_merge }],
    motivation: [{ label: "고긴급", build: moti_high }, { label: "저긴급", build: moti_low }],
    compare:    [{ label: "좁은 병목", build: compare_narrow }, { label: "넓은 병목", build: compare_wide }],
    steering:   [{ label: "사각 경로", build: steer_rect }, { label: "8자 경로", build: steer_eight }]
  };
  var SCENARIOS = { hero: S_hero };
  Object.keys(VARIANTS).forEach(function (k) { SCENARIOS[k] = VARIANTS[k][0].build; });

  /* ============================================================ runtime */
  var registry = [];
  function buildSim(canvas, builder) { var sim = makeSim(canvas, builder()); registry.push({ sim: sim, el: canvas }); return registry[registry.length - 1]; }

  function loop() {
    for (var i = 0; i < registry.length; i++) { var e = registry[i]; if (!e.sim.active) continue; e.sim.step(); e.sim.draw(); if (e.onFrame) e.onFrame(e.sim); }
    requestAnimationFrame(loop);
  }

  function start() {
    var heroCanvas = document.getElementById("hero-canvas");
    if (heroCanvas) {
      var heroEntry = buildSim(heroCanvas, S_hero);
      var elEv = document.getElementById("hud-evac"), elRem = document.getElementById("hud-remain"),
          elTime = document.getElementById("hud-time"), elDen = document.getElementById("hud-density");
      var total = heroEntry.sim.agents.length, lastUpd = 0;
      heroEntry.onFrame = function (sim) {
        if (sim.t - lastUpd < 0.18) return; lastUpd = sim.t;
        if (elEv) elEv.innerHTML = sim.stats.evacuated + " <small>/ " + total + "</small>";
        if (elRem) elRem.textContent = sim.stats.remaining;
        if (elDen) elDen.textContent = sim.stats.density.toFixed(2);
        if (elTime) { var s = Math.floor(sim.stats.elapsed); elTime.textContent = String(Math.floor(s / 60)).padStart(2, "0") + ":" + String(s % 60).padStart(2, "0"); }
      };
    }
    document.querySelectorAll("canvas[data-scenario]").forEach(function (cv) {
      var key = cv.getAttribute("data-scenario"); if (SCENARIOS[key]) buildSim(cv, SCENARIOS[key]);
    });

    if ("IntersectionObserver" in window && !REDUCED) {
      var io = new IntersectionObserver(function (ents) {
        ents.forEach(function (en) { var e = registry.find(function (r) { return r.el === en.target; }); if (e) e.sim.active = en.isIntersecting; });
      }, { threshold: 0.05 });
      registry.forEach(function (e) { io.observe(e.el); });
    } else {
      registry.forEach(function (e) { for (var k = 0; k < 200; k++) e.sim.step(); e.sim.draw(); if (e.onFrame) e.onFrame(e.sim); });
    }
    if (!REDUCED) requestAnimationFrame(loop);

    var rt; window.addEventListener("resize", function () { clearTimeout(rt); rt = setTimeout(function () { registry.forEach(function (e) { e.sim.resize(); e.sim.draw(); }); if (modal.sim) modal.sim.resize(); }, 150); });
    initFilters(); initReveal(); initModal();
  }

  function initFilters() {
    var chips = document.querySelectorAll(".chip"), cards = document.querySelectorAll(".card");
    chips.forEach(function (chip) {
      chip.addEventListener("click", function () {
        chips.forEach(function (c) { c.classList.remove("active"); }); chip.classList.add("active");
        var f = chip.getAttribute("data-filter");
        cards.forEach(function (card) { var tags = card.getAttribute("data-tags") || ""; card.classList.toggle("is-hidden", !(f === "all" || tags.indexOf(f) >= 0)); });
        registry.forEach(function (e) { e.sim.resize(); });
      });
    });
  }
  function initReveal() {
    var els = document.querySelectorAll(".reveal");
    if (!("IntersectionObserver" in window) || REDUCED) { els.forEach(function (el) { el.classList.add("in"); }); return; }
    var io = new IntersectionObserver(function (ents) { ents.forEach(function (en) { if (en.isIntersecting) { en.target.classList.add("in"); io.unobserve(en.target); } }); }, { threshold: 0.12 });
    els.forEach(function (el) { io.observe(el); });
  }

  /* ---------------------------------------------------------- detail modal */
  var modal = { sim: null, raf: 0, key: null, variant: 0, model: "cfsm" };

  function initModal() {
    var m = document.getElementById("detail"); if (!m) return;
    document.querySelectorAll(".card[data-scenario]").forEach(function (card) {
      var key = card.getAttribute("data-scenario"); if (!VARIANTS[key]) return;
      card.classList.add("clickable");
      card.addEventListener("click", function (ev) { if (ev.target.closest("a")) return; openModal(key); });
    });
    m.querySelector(".detail-close").addEventListener("click", closeModal);
    m.querySelector(".detail-backdrop").addEventListener("click", closeModal);
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") closeModal(); });
    // 딥링크: #ex-lane 또는 #ex-motivation:br (모델 지정)
    var h = (location.hash || "").match(/^#ex-([a-z]+)(?::([a-z]+))?/i);
    if (h && VARIANTS[h[1]]) openModal(h[1], MODELS[h[2]] ? h[2] : "cfsm");
  }

  function openModal(key, model) {
    var m = document.getElementById("detail");
    modal.key = key; modal.variant = 0; modal.model = model || "cfsm";
    var meta = CARD_META[key] || { title: key };
    m.querySelector(".detail-title").textContent = meta.title;
    m.querySelector(".detail-desc").textContent = meta.descLong || "";
    m.querySelector(".detail-orig").href = meta.url || "#";

    // 모델 선택
    var msel = m.querySelector(".detail-models"); msel.innerHTML = "";
    ["cfsm", "sfm", "avm", "br"].forEach(function (mk) {
      var b = document.createElement("button");
      b.className = "mbtn" + (mk === modal.model ? " active" : ""); b.textContent = MODELS[mk].label;
      b.title = MODELS[mk].full;
      b.addEventListener("click", function () {
        msel.querySelectorAll(".mbtn").forEach(function (x) { x.classList.remove("active"); });
        b.classList.add("active"); modal.model = mk; loadVariant();
      });
      msel.appendChild(b);
    });

    // 변형 탭
    var tabs = m.querySelector(".detail-tabs"); tabs.innerHTML = "";
    VARIANTS[key].forEach(function (vrt, i) {
      var b = document.createElement("button");
      b.className = "vtab" + (i === 0 ? " active" : ""); b.textContent = vrt.label;
      b.addEventListener("click", function () {
        tabs.querySelectorAll(".vtab").forEach(function (x) { x.classList.remove("active"); });
        b.classList.add("active"); modal.variant = i; loadVariant();
      });
      tabs.appendChild(b);
    });

    m.classList.add("open"); m.setAttribute("aria-hidden", "false"); document.body.style.overflow = "hidden";
    loadVariant();
  }

  function loadVariant() {
    var m = document.getElementById("detail"), cv = m.querySelector(".detail-canvas");
    if (modal.raf) cancelAnimationFrame(modal.raf);
    var cfg = VARIANTS[modal.key][modal.variant].build();
    cfg.model = modal.model;
    modal.sim = makeSim(cv, cfg); modal.sim.active = true;
    renderTheory(m.querySelector(".detail-theory"), modal.model);
    buildSliders(m, modal.sim);
    var rs = m.querySelector("#rd-speed"), rd = m.querySelector("#rd-density"), rn = m.querySelector("#rd-n");
    function frame() {
      modal.sim.step(); modal.sim.draw();
      if (rs) rs.textContent = modal.sim.stats.speed.toFixed(2);
      if (rd) rd.textContent = modal.sim.stats.density.toFixed(2);
      if (rn) rn.textContent = modal.sim.stats.remaining;
      modal.raf = requestAnimationFrame(frame);
    }
    if (!REDUCED) frame(); else { for (var k = 0; k < 200; k++) modal.sim.step(); modal.sim.draw(); }
  }

  function buildSliders(m, sim) {
    var box = m.querySelector(".detail-sliders"); box.innerHTML = "";
    sim.model.sliders.forEach(function (sl) {
      var val = sl.pop ? sim.agents.length : sim.params[sl.key];
      var row = document.createElement("div"); row.className = "slider-row";
      row.innerHTML = '<label>' + sl.label + ' <b class="sval">' + fmt(val) + (sl.unit ? ' <span>' + sl.unit + '</span>' : '') + '</b></label>' +
        '<input type="range" min="' + sl.min + '" max="' + sl.max + '" step="' + sl.step + '" value="' + val + '">';
      var input = row.querySelector("input"), out = row.querySelector(".sval");
      input.addEventListener("input", function () {
        var v = parseFloat(input.value);
        out.innerHTML = fmt(v) + (sl.unit ? ' <span>' + sl.unit + '</span>' : '');
        if (sl.pop) sim.setPopulation(Math.round(v)); else sim.params[sl.key] = v;
      });
      box.appendChild(row);
    });
  }
  function fmt(v) { return (Math.round(v * 100) / 100).toString(); }

  function closeModal() {
    var m = document.getElementById("detail"); if (!m || !m.classList.contains("open")) return;
    if (modal.raf) cancelAnimationFrame(modal.raf); modal.raf = 0; modal.sim = null;
    m.classList.remove("open"); m.setAttribute("aria-hidden", "true"); document.body.style.overflow = "";
  }

  /* ---- 모델별 수학 이론 (요약 + KaTeX 수식 + 상세 한글) ---- */
  function rb(n) { return '<a class="ref-badge" href="#refs">' + n + '</a>'; }
  var MODEL_THEORY = {
    cfsm: {
      refs: rb("R1") + rb("R4") + rb("R6"),
      summary: "각 보행자의 이동 ‘방향’은 목표 방향에 이웃·벽의 지수형 반발을 더해 정규화하고, ‘속도’는 진행 방향 앞 최근접 이웃까지의 간격 s로 정하는 1차(속도 기반) 모델이다. 반발이 방향만 바꾸고 속도는 v₀로 상한되므로 폭발하지 않고 매우 안정적이다.",
      eqs: [
        "\\vec{e}_i = \\mathcal{N}\\!\\left(\\vec{e}_i^{\\,0} + \\textstyle\\sum_j \\vec{R}_{ij} + \\sum_w \\vec{R}_{iw}\\right)",
        "\\vec{R}_{ij} = -\\,\\hat{e}_{ij}\\,\\nu\\,\\exp\\!\\left(\\frac{l_{ij}-d_{ij}}{D}\\right),\\quad l_{ij}=r_i+r_j",
        "\\vec{R}_{iw} = -\\,\\hat{e}_{iw}\\,\\nu_w\\,\\exp\\!\\left(\\frac{r_i-d_{iw}}{D_w}\\right)",
        "s_i = \\min_{j\\,\\in\\,\\mathrm{front}}\\big(\\lVert \\vec{x}_j-\\vec{x}_i\\rVert - l_{ij}\\big)",
        "V(s_i) = \\min\\!\\Big(\\max\\big(\\tfrac{s_i}{T},0\\big),\\,v_0\\Big),\\;\\; \\vec{x}_i(t{+}\\Delta t)=\\vec{x}_i+V(s_i)\\,\\vec{e}_i\\,\\Delta t"
      ],
      detail:
        "<h5>방향 결정</h5><p>보행자 <code>i</code>의 이동 방향 <code>e_i</code>는 목표(출구·경유지)로 향하는 단위벡터 <code>e_i⁰</code>에 모든 이웃 <code>j</code>의 반발 <code>R_ij</code>와 벽 <code>w</code>의 반발 <code>R_iw</code>를 더한 뒤 정규화(<code>N(·)</code>)해 얻는다. 반발은 거리가 접촉거리 <code>l_ij = r_i + r_j</code>에 가까울수록 지수적으로 급증한다.</p>" +
        "<h5>속도 결정</h5><p>속도는 진행 방향 앞쪽(코리도 폭 <code>l_ij</code> 이내)에서 가장 가까운 이웃까지의 표면 간격 <code>s_i</code>로 정한다. 최적속도 <code>V(s)=min(max(s/T,0), v₀)</code>는 간격이 넓으면 자유속도 <code>v₀</code>, 좁으면 비례 감속, 닿으면 0이다. 반발이 ‘속도’가 아닌 ‘방향’만 바꾸므로 속도는 항상 <code>v₀</code> 이하 — 1차 모델이라 수치적으로 안정적이다.</p>" +
        "<h5>특징 · 용도</h5><ul><li>가볍고 안정적 → 수천~수만 명 대규모 피난 해석의 기본 모델.</li><li>벽에 가려 보이지 않는 이웃은 무시(line-of-sight).</li><li>전 보행자 동기(synchronous) 갱신 후 위치를 일괄 적용.</li></ul>",
      params: "ν = 8.0 · D = 0.1 m · ν_w = 5.0 · D_w = 0.02 m · T = 1.0 s · v₀ = 1.2 m/s · r = 0.2 m"
    },
    sfm: {
      refs: rb("R2") + rb("R4") + rb("R6"),
      summary: "목표를 향한 구동력과, 사람·벽으로부터의 지수형 반발력(겹칠 때 신체 반발 k·마찰 κ가 추가)을 합쳐 가속도를 구하고 적분하는 2차(힘 기반) 모델이다. 밀집 시 ‘밀침’과 출구 앞 아치형 정체 같은 물리적 군중 압력을 재현한다.",
      eqs: [
        "m\\frac{d\\vec{v}_i}{dt} = m\\,\\frac{v_0\\,\\vec{e}_i^{\\,0}-\\vec{v}_i}{\\tau} \\;+\\; \\textstyle\\sum_j \\vec{f}_{ij} \\;+\\; \\sum_w \\vec{f}_{iw}",
        "\\vec{f}_{ij} = \\Big[A\\,e^{(r_{ij}-d_{ij})/B} + k\\,g\\Big]\\hat{n}_{ij} + \\kappa\\,g\\,(\\Delta\\vec{v}\\!\\cdot\\!\\hat{t}_{ij})\\,\\hat{t}_{ij}",
        "g = \\max(r_{ij}-d_{ij},\\,0),\\quad \\hat{n}_{ij}=\\tfrac{\\vec{x}_i-\\vec{x}_j}{d_{ij}},\\quad \\hat{t}_{ij}=\\hat{n}_{ij}^{\\perp}",
        "\\vec{v}_i(t{+}\\Delta t)=\\vec{v}_i+\\tfrac{d\\vec{v}_i}{dt}\\Delta t,\\quad \\vec{x}_i(t{+}\\Delta t)=\\vec{x}_i+\\vec{v}_i\\,\\Delta t"
      ],
      detail:
        "<h5>구동력</h5><p>목표 속도 <code>v₀·e⁰</code>와 현재 속도 <code>v_i</code>의 차를 반응시간 <code>τ</code>로 나눈 가속이다. 사람을 항상 목표 속도로 끌어당긴다.</p>" +
        "<h5>상호작용력</h5><p>멀리서도 작용하는 지수 반발 <code>A·e^((r−d)/B)</code>에, 실제로 겹칠 때(<code>g&gt;0</code>)만 더해지는 신체 반발 <code>k·g</code>(법선)과 마찰 <code>κ·g·(Δv·t)</code>(접선, 상대 접선속도)가 붙는다. 벽에는 같은 형태의 장애물 반발을 적용한다.</p>" +
        "<h5>특징</h5><ul><li>가속도를 적분하는 2차 모델 → 강성이 커서 작은 Δt가 필요. 본 구현은 Δt = 0.0125 s 서브스텝과 속도 상한으로 안정화.</li><li>밀집 군중의 압력·정체·돌발 흐름 표현에 강점.</li><li>v₀ 기본값이 0.8 m/s로 낮아 다른 모델보다 차분하게 움직인다(슬라이더로 조절).</li></ul>",
      params: "m = 80 kg · v₀ = 0.8 m/s · τ = 0.5 s · A = 2000 · B = 0.08 m · k = 1.2×10⁵ · κ = 2.4×10⁵ · r = 0.3 m"
    },
    avm: {
      refs: rb("R3") + rb("R4") + rb("R6"),
      summary: "이웃의 미래 위치를 ‘예측 시간 tₐ’만큼 내다보고 미리 옆으로 비켜서며(측면 회피), 방향 변화는 반응시간 Tᵣ로 부드럽게 평활하는 1차 모델이다. 정면 교행·코너에서 사람처럼 자연스러운 회피와 차선 형성을 만든다.",
      eqs: [
        "R = \\max\\!\\Big( d_{ij}-(r_i+r_j) - \\big[(\\vec{v}_i-\\vec{v}_j)\\!\\cdot\\!\\hat{e}_{ij}\\big]\\,t_a,\\; 0\\Big)",
        "f_{ij} = \\nu\\big[\\,1 + \\tfrac12(1-\\hat{d}_i\\!\\cdot\\!\\hat{e}_j)\\,\\big]\\,e^{-R/D}",
        "\\vec{e}_i^{\\,c} = \\mathcal{N}\\!\\Big(\\vec{e}_i^{\\,0} + \\textstyle\\sum_j f_{ij}\\,\\hat{s}_{ij}\\Big),\\quad \\hat{s}_{ij}\\perp\\hat{d}_i",
        "\\frac{d\\vec{e}_i}{dt} = \\frac{\\vec{e}_i^{\\,c}-\\vec{e}_i}{T_r},\\qquad \\vec{v}_i = V(s_i)\\,\\vec{e}_i"
      ],
      detail:
        "<h5>예측 (Anticipation)</h5><p>실제 표면거리에서 접근 속도 성분 <code>(v_i−v_j)·ê · tₐ</code> 만큼을 빼 ‘미래 간격’ <code>R</code>을 만든다. 빠르게 접근할수록 R이 작아져 반발이 커진다 — 즉 충돌을 미리 예상한다.</p>" +
        "<h5>측면 회피</h5><p>반발은 정면이 아니라 목표 방향의 직교(좌/우) 방향 <code>ŝ_ij</code>로 작용하되, 예측 위치 <code>x_j + v_j·tₐ</code>의 반대쪽을 고른다 → 미리 옆으로 비켜서기. 정렬항 <code>[1 + ½(1 − d_i·e_j)]</code>은 마주 오는 상대일수록 회피를 강화한다.</p>" +
        "<h5>방향 평활</h5><p>계산된 방향 <code>e_c</code>로 즉시 꺾지 않고 반응시간 <code>Tᵣ</code>로 1차 지연시켜 떨림 없는 자연스러운 선회를 만든다. 속도는 CFSM과 같은 간격 기반 <code>V(s)</code>이고, 벽 근처에서는 벽 방향 성분을 제거하고 바깥으로 미끄러진다(pushout).</p>",
      params: "ν = 8.0 · D = 0.1 m · tₐ = 1.0 s(예측) · Tᵣ = 0.3 s(반응) · T = 1.06 s · v₀ = 1.2 m/s · 벽 버퍼 = 0.1 m · r = 0.2 m"
    },
    br: {
      refs: rb("R8") + rb("R9") + rb("R10"),
      summary: "한국 유홍선(중앙대) 그룹의 화재 피난 확장 모델. Helbing 사회력 모델에 ‘연기력(f_iS)’과 ‘복사력(f_iR)’을 더해, 보행자가 ① 불을 보고 피해가고 ② 연기를 회피하며 ③ 연기 속에 들면 바깥으로 밀려 나가는 거동을 재현한다.",
      eqs: [
        "m_i\\frac{d^2\\vec{x}_i}{dt^2}=\\frac{m_i}{\\tau_i}\\big(\\vec{v}_i^{\\,0}-\\vec{v}_i\\big)+\\textstyle\\sum_j \\vec{f}_{ij}+\\sum_W \\vec{f}_{iw}+\\vec{f}_{iS}+\\vec{f}_{iR}",
        "\\vec{f}_{iS}=\\underbrace{S_{iS}\\,e^{-d_i^{S}}u(d_i^{S})\\,\\hat{n}_{iS}}_{\\text{외부(회피)}}+\\underbrace{S_{iS}\\tfrac{V_i}{V_\\infty}\\big(1-u(d_i^{S})\\big)\\hat{n}_{iS}}_{\\text{내부(밀침)}}",
        "\\vec{f}_{iS}^{\\;mod}=\\frac{m_i v_i^0}{\\tau_i}e^{-d_{iS}}u(d_{iS})\\hat{n}_{iS}+m_i v_i^0\\frac{\\beta}{\\alpha}\\frac{dK_s}{dt}\\big(1-u(d_{iS})\\big)\\hat{n}_{iS}",
        "\\vec{f}_{iR}=S_R\\Big\\langle\\frac{\\dot q''_i-\\dot q''_{th}}{\\dot q''_0}\\Big\\rangle^{+}a(\\theta)\\,\\hat{n}_{iR},\\quad \\dot q''_i=\\frac{\\chi_r\\,\\dot Q}{4\\pi r_i^2}"
      ],
      detail:
        "<h5>구성</h5><p>BR(Bae–Ryou) 모델은 Helbing 사회력 모델에 <code>연기력 f_iS</code>(연기의 심리적 압박)과 <code>복사력 f_iR</code>(불의 복사열 압박)을 더한 확장이다. 유홍선(중앙대) 그룹의 일련의 연구(2015·2016·2020)에서 제안됐다.</p>" +
        "<h5>연기력 (smoke force)</h5><p>연기 경계 <b>밖</b>에서는 외부력이 사람을 연기에서 밀어내 <b>연기를 회피</b>하게 하고(경계 근처에서 망설임), 연기 <b>안</b>에서는 내부력이 사람을 <b>바깥으로 밀어낸다</b>. <code>n̂_iS</code>는 연기 경계→사람 방향, <code>u(d)</code>는 단위 계단함수다. 원형(식 2)은 내부력을 가시도 <code>V_i</code>에 비례시켰으나, 개정판(식 3, Energies 2020)은 연기밀도(감광계수 <code>K_s</code>)의 시간변화율 <code>dK_s/dt</code>에 비례시켜 짙은 연기 속에서도 탈출을 지속 유도한다.</p>" +
        "<h5>복사력 (radiation force)</h5><p>화원의 복사 열유속 <code>q″</code>(거리² 반비례)가 임계 <code>q″_th</code>(통증 한계 ≈ 2.5 kW/m²)를 넘으면, 그 초과분에 비례한 반발력이 <b>불에서 멀어지는 방향</b>(<code>n̂_iR</code>)으로 작용한다. <b>비등방성</b> <code>a(θ)</code>: 불을 정면으로 바라볼수록 강해 진행 방향을 틀게 만든다.</p>" +
        "<h5>라이브 데모 주의</h5><ul><li>연기력(외부/내부)·Helbing 기반은 원 논문 식 그대로.</li><li>복사력은 원 논문 [R8]의 상수식이 비공개라, 점원(point-source) 열유속 <code>q″=χ_r·Q̇/(4πr²)</code>로 재현했다.</li><li>발화점이 없는 예제에선 중앙에 발화점을 자동 배치한다.</li></ul>",
      params: "S_iS = 125 N · V_∞ = 30 m · α = 0.706 m/s · β = −0.057 m²/s · q″_th ≈ 2.5 kW/m² · χ_r ≈ 0.3 · (기반 SFM: m = 80, τ = 0.5, A = 2000, B = 0.08)"
    }
  };

  function renderTheory(host, mkey) {
    if (!host) return;
    var th = MODEL_THEORY[mkey], M = MODELS[mkey];
    var h = '<div class="theory">';
    h += '<div class="theory-head"><h4>이론 · ' + M.label + '</h4><span class="full">' + M.full + '</span>' + th.refs + '</div>';
    h += '<p class="theory-summary">' + th.summary + '</p>';
    h += '<div class="theory-eqs">';
    th.eqs.forEach(function (e, i) { h += '<div class="eq" data-eq="' + i + '"></div>'; });
    h += '</div>';
    h += '<div class="theory-detail">' + th.detail + '</div>';
    h += '<div class="theory-params"><b>기본 파라미터</b><br>' + th.params + '</div>';
    h += '</div>';
    host.innerHTML = h;
    th.eqs.forEach(function (e, i) {
      var el = host.querySelector('.eq[data-eq="' + i + '"]');
      if (window.katex) { try { window.katex.render(e, el, { displayMode: true, throwOnError: false }); return; } catch (err) { } }
      el.innerHTML = '<span class="eq-fallback">' + e.replace(/&/g, "&amp;").replace(/</g, "&lt;") + '</span>';
    });
  }

  var CARD_META = {
    routing: { title: "경로 선택과 대피 시간", url: "https://www.jupedsim.org/stable/notebooks/routing.html", descLong: "일부는 최단 경로로, 일부는 우회로로 출구에 도달한다. 경로 분담 비율과 파라미터가 전체 대피 시간을 어떻게 바꾸는지 본다." },
    bottleneck: { title: "병목 통과", url: "https://www.jupedsim.org/stable/notebooks/double-bottleneck.html", descLong: "좁은 통로를 지날 때의 정체와 유량. 출구 폭·인원·희망속도를 바꿔 피난 성능 변화를 본다." },
    corner: { title: "코너 통과 동선", url: "https://www.jupedsim.org/stable/notebooks/corner.html", descLong: "모퉁이를 도는 보행자 동선. 안쪽 쏠림과 곡선 흐름이 형성된다." },
    lane: { title: "양방향 흐름의 차선 형성", url: "https://www.jupedsim.org/stable/notebooks/lane-formation.html", descLong: "반대로 향하는 두 무리가 마주칠 때 스스로 차선을 이루는 자기조직화. 직선·ㄱ자·십자 형태에서 비교한다." },
    queue: { title: "대기열 행동", url: "https://www.jupedsim.org/stable/notebooks/queues_waiting.html", descLong: "창구로 모여 줄을 서는 과정. 창구 수와 인원에 따른 대기 양상." },
    singlefile: { title: "단일 대열 이동", url: "https://www.jupedsim.org/stable/notebooks/single-file.html", descLong: "한 줄로 이동하는 고전 실험. 간격–속도 관계(기본 다이어그램)의 토대." },
    journey: { title: "여정(Journey) 설계", url: "https://www.jupedsim.org/stable/notebooks/journey.html", descLong: "경유지·분기·합류가 있는 복합 경로. 여러 방을 거쳐 출구에 이르는 다단계 동선." },
    motivation: { title: "동기(Motivation) 모델링", url: "https://www.jupedsim.org/stable/notebooks/motivation.html", descLong: "긴급도(=희망속도 v0)가 높을수록 출구 앞 고밀집 아치형 정체가 심해진다." },
    compare: { title: "병목 폭과 흐름", url: "https://www.jupedsim.org/stable/notebooks/model-comparison.html", descLong: "희망속도가 다른 두 무리를 좁은/넓은 병목에 통과시켜 흐름 차이를 비교한다." },
    steering: { title: "리더-팔로워 직접 조향", url: "https://www.jupedsim.org/stable/notebooks/direct_steering.html", descLong: "인솔자가 경로를 그리면 무리가 뒤를 따른다. 사각/8자 경로에서 그룹 유도 동선을 본다." }
  };

  // [이관] 원본은 DOMContentLoaded 시점 start() 자동 실행. React effect에서 마운트 후 호출하므로 즉시 실행.
  start();
}
