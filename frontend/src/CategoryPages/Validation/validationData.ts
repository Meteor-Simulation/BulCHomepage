/**
 * BULC V&V 게시 데이터 (BULC-VV `export.homepage_summary` → `public/validation/<ver>/summary.json`, schema 3).
 * 정적 JSON 을 fetch 한다 (utils/eventConfig.ts 와 같은 no-store + TTL 패턴). 데이터 생성은 BULC-VV 리포 `runners/m5_summary.py`.
 */

export type Phase1Status =
  | 'PASS' | 'NOTE' | 'WARNING' | 'FAIL' | 'INCOMPARABLE' | 'ENGINE_FAIL' | 'DECK_UNAVAILABLE' | 'NOT_RUN';

export type Quadrant = 'both_match' | 'both_off' | 'x_only_off' | 'x_closer' | 'undetermined';

export interface MetricSub {
  value: number | null;
  fds: number | null;
  gpu: number | null;
  envelope: number | null;
  status: string | null;
  significance: string | null;
  magnitude_tier: number | null;
  roi: string | null;
  phase: string | null;
}

export interface GateCell { status: string; n: number; worst?: string }

export interface Benchmark {
  id: string;
  base_id: string;
  revision: number;
  title: string;
  source?: string | null;
  window: [number, number] | null;
  status: Phase1Status;
  counts: Record<string, number>;
  n_metrics: number;
  note: string | null;
  gate: { phases: string[]; rois: string[] };
  gate_cells: Record<string, Record<string, GateCell>>;
  metrics: Record<string, MetricSub>;
  ensemble: { n_gpu: number | null; n_fds: number | null } | null;
  known_issues: string[];
  figures: string[];
}

export interface Phase2Point {
  id: string;
  dataname: string | null;
  quantity: string;
  metric: string;
  label: string;
  exp: number | null;
  fds: number | null;
  gpu: number | null;
  rel: { fds_exp: number | null; gpu_exp: number | null; gpu_fds: number | null } | null;
  band2: number | null;
  within: { fds_exp: boolean | null; gpu_exp: boolean | null } | null;
  quadrant: Quadrant;
  sigma_e: number | null;
  sigma_m: number | null;
  note?: string | null;
}

export interface Phase2Category {
  name: string;
  n: number;
  guide: { sigma_e: number; sigma_m: number; delta: number } | null;
  cases: string[];
  fds: { n: number | null; mu: number | null; sigma_m: number | null; delta: number | null };
  gpu: { n: number | null; mu: number | null; sigma_m: number | null; delta: number | null };
  quadrants: Record<Quadrant, number>;
}

export interface KnownIssue {
  id: string;
  status: string;
  status_raw: string;
  since: string;
  jira: string | null;
  summary: string;
  cases: string[];
}

export interface AuditCase {
  id: string;
  revision: number;
  run_id: string;
  engine_commit: string | null;
  engine_binary_sha256: string | null;
  provenance_sha256: string;
  mesh: { ijk: number[]; xb: number[] }[];
  parameters: Record<string, unknown>;
  deck_sha256: string | null;
  raw: { n_files: number; bytes: number; sha256_digest: string };
  derived: { store: string | null; n_files: number; reduced_field: Record<string, unknown> };
  retention: { expires: string | null; deleted_at: string | null };
  verification: { at: string | null; ok: boolean | null; derived_recomputed_identical: boolean | null };
  fds?: { version: string | null; binary_sha256: string | null; run_id: string | null; provenance_sha256: string | null; deck_sha256: string | null };
}

export interface ValidationSummary {
  schema: number;
  generated_at: string;
  preliminary: boolean;
  engine: { name: string; version: string; commit: string | null; commits: string[] };
  reference: { fds: string | null; exp_commit: string | null; guide: string | null; vvlib: string | null; bulc_vv_commit: string | null; firemodels_fds_commit: string | null };
  hardware: { gpu: string | null; driver: string | null; cuda: string | null; os: string | null };
  counts: Record<string, number>;
  label_definitions: Record<string, string>;
  previous: null | Record<string, unknown>;
  known_issues: KnownIssue[];
  phase1: { benchmarks: Benchmark[] };
  phase2: {
    n_points: number; counts: Record<Quadrant, number>; categories: Phase2Category[]; cases: Phase2Point[];
    x_only_off: { case: string; quantity: string; label: string }[];
  } | null;
  audit: {
    package_url: string | null; package_sha256: string | null; package_bytes: number | null; package_uploaded: boolean | null;
    release_tag: string | null; excluded_private_cases: string[] | null; cases: AuditCase[];
  };
}

export interface ValidationIndexEntry {
  version: string;
  engine: string;
  generated_at: string;
  preliminary: boolean;
  counts: Record<string, number>;
  phase2_counts: Record<Quadrant, number> | null;
  path: string;
}

export interface ValidationIndex { schema: number; versions: ValidationIndexEntry[] }

const BASE = '/validation';
const CACHE_TTL_MS = 60_000;
const cache = new Map<string, { value: unknown; fetchedAt: number }>();

async function fetchJson<T>(path: string): Promise<T | null> {
  const now = Date.now();
  const hit = cache.get(path);
  if (hit && now - hit.fetchedAt < CACHE_TTL_MS) return hit.value as T | null;
  try {
    const res = await fetch(`${path}?t=${now}`, { cache: 'no-store' });
    if (!res.ok) { cache.set(path, { value: null, fetchedAt: now }); return null; }
    const value = (await res.json()) as T;
    cache.set(path, { value, fetchedAt: now });
    return value;
  } catch (e) {
    console.warn('[validation] fetch failed', path, e);
    cache.set(path, { value: null, fetchedAt: now });
    return null;
  }
}

export const fetchValidationIndex = () => fetchJson<ValidationIndex>(`${BASE}/index.json`);
export const fetchValidationSummary = (version: string) => fetchJson<ValidationSummary>(`${BASE}/${version}/summary.json`);
export const figureUrl = (version: string, rel: string) => `${BASE}/${version}/${rel}`;

export const STATUS_ORDER: Phase1Status[] = ['PASS', 'NOTE', 'WARNING', 'FAIL', 'INCOMPARABLE', 'ENGINE_FAIL', 'DECK_UNAVAILABLE', 'NOT_RUN'];
export const QUADRANT_ORDER: Quadrant[] = ['both_match', 'both_off', 'x_only_off', 'x_closer', 'undetermined'];

export const fmtNum = (v: number | null | undefined, digits = 3): string => {
  if (v === null || v === undefined || Number.isNaN(v)) return '—';
  const a = Math.abs(v);
  if (a !== 0 && (a < 1e-3 || a >= 1e5)) return v.toExponential(2);
  return v.toFixed(a >= 100 ? 1 : digits);
};

export const fmtPct = (v: number | null | undefined): string => {
  if (v === null || v === undefined || Number.isNaN(v)) return '—';
  const p = v * 100;
  return `${p > 0 ? '+' : ''}${p.toFixed(Math.abs(p) >= 10 ? 0 : 1)} %`;
};

export const fmtBytes = (b: number | null | undefined): string => {
  if (!b) return '—';
  if (b >= 1e9) return `${(b / 1e9).toFixed(2)} GB`;
  if (b >= 1e6) return `${(b / 1e6).toFixed(1)} MB`;
  return `${(b / 1e3).toFixed(0)} KB`;
};
