import React, { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import '../Common/CategoryPages.css';
import './Validation.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';
import Seo from '../../components/Seo';
import {
  Benchmark, fetchValidationSummary, figureUrl, fmtBytes, fmtNum, fmtPct, Phase2Point, STATUS_ORDER,
  ValidationSummary,
} from './validationData';
import { QuadrantBadge, QuadrantBar, Sha, StatusBadge } from './ValidationWidgets';

/** /validation/:version — 로드맵 Phase 5 의 버전 페이지 4 블록: Summary · Case Result · Spatial/Statistical · Audit Information. */
const ValidationVersionPage: React.FC = () => {
  const { t } = useTranslation();
  const { version = '' } = useParams<{ version: string }>();
  const [data, setData] = useState<ValidationSummary | null | undefined>(undefined);
  const [openCase, setOpenCase] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setData(undefined);
    fetchValidationSummary(version).then((s) => { if (alive) setData(s); });
    return () => { alive = false; };
  }, [version]);

  const benchmarks = useMemo(() => data?.phase1.benchmarks ?? [], [data]);
  // 기본 선택: 멀티메시 파생(_mm)이 아닌 첫 케이스
  const selected = useMemo(() => benchmarks.find((b) => b.id === openCase) ?? benchmarks.find((b) => !/_mm\d/.test(b.id)) ?? benchmarks[0], [benchmarks, openCase]);
  const p2ByCase = useMemo(() => {
    const m = new Map<string, Phase2Point[]>();
    for (const p of data?.phase2?.cases ?? []) { const a = m.get(p.id) ?? []; a.push(p); m.set(p.id, a); }
    return m;
  }, [data]);

  const title = `${t('validation.title')} — ${data ? `${data.engine.name} ${data.engine.version}` : version}`;

  return (
    <div className="app">
      <Seo title={`${title} | BUL:C`} description={t('validation.seo.versionDescription', { version })} path={`/validation/${version}`} />
      <Header logoText="BUL:C" />
      <main className="main-content sub-page">
        <div className="docs-container vv-container vv-wide">
          <p className="vv-breadcrumb"><Link to="/validation">{t('validation.title')}</Link> / {version}</p>
          {data === undefined && <p className="vv-muted">{t('validation.loading')}</p>}
          {data === null && <p className="vv-muted">{t('validation.notFound', { version })}</p>}
          {data && (
            <>
              <h1>{data.engine.name} {data.engine.version} {data.preliminary && <span className="vv-tag">{t('validation.preliminary')}</span>}</h1>
              <p className="docs-lead">{t('validation.versionLead', { fds: data.reference.fds ?? '—', guide: data.reference.guide ?? '—' })}</p>
              <div className="vv-notice">{t('validation.preliminaryNotice')}</div>

              {/* ── 1. Summary ─────────────────────────────────────────── */}
              <section className="vv-block">
                <h2>{t('validation.blocks.summary')}</h2>
                <div className="vv-kpis">
                  <div className="vv-kpi"><div className="vv-kpi-num">{data.counts.cases}</div><div className="vv-kpi-label">{t('validation.phase1Cases')}</div></div>
                  {STATUS_ORDER.filter((s) => data.counts[s]).map((s) => (
                    <div className="vv-kpi" key={s}><div className="vv-kpi-num">{data.counts[s]}</div><div className="vv-kpi-label"><StatusBadge status={s} title={data.label_definitions[s]} /></div></div>
                  ))}
                  {data.phase2 && (
                    <div className="vv-kpi vv-kpi-wide">
                      <div className="vv-kpi-num">{data.phase2.n_points}</div>
                      <div className="vv-kpi-label">{t('validation.phase2Points')}</div>
                      <QuadrantBar counts={data.phase2.counts} />
                    </div>
                  )}
                </div>
                <p className="vv-muted vv-small">
                  {t('validation.previousNone')} · {t('validation.privateExcluded', { n: data.counts.private_excluded ?? 0 })} · {t('validation.generatedAt')} {data.generated_at.slice(0, 16).replace('T', ' ')}
                </p>
                <details className="vv-details">
                  <summary>{t('validation.labelDefinitions')}</summary>
                  <table className="vv-table vv-small"><tbody>
                    {Object.entries(data.label_definitions).map(([k, v]) => (
                      <tr key={k}><td><StatusBadge status={k} /></td><td>{v}</td></tr>
                    ))}
                  </tbody></table>
                </details>
                <h3>{t('validation.knownIssues')} ({data.known_issues.length})</h3>
                <table className="vv-table vv-table-wrap vv-small">
                  <thead><tr><th>id</th><th>{t('validation.ki.status')}</th><th>{t('validation.ki.since')}</th><th>Jira</th><th>{t('validation.ki.summary')}</th><th>{t('validation.ki.cases')}</th></tr></thead>
                  <tbody>
                    {data.known_issues.map((k) => (
                      <tr key={k.id}>
                        <td><code>{k.id}</code></td>
                        <td><span className={`vv-badge vv-ki-${k.status}`}>{k.status}</span></td>
                        <td>{k.since}</td>
                        <td>{k.jira ?? '—'}</td>
                        <td className="vv-wrap">{k.summary}</td>
                        <td>{k.cases.join(', ') || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>

              {/* ── 2. Case Result ─────────────────────────────────────── */}
              <section className="vv-block">
                <h2>{t('validation.blocks.caseResult')}</h2>
                <h3>{t('validation.phase1Title')}</h3>
                <p className="vv-muted vv-small">{t('validation.phase1Desc')}</p>
                <div className="vv-scroll">
                  <table className="vv-table">
                    <thead>
                      <tr>
                        <th>{t('validation.table.case')}</th><th>rev</th><th>{t('validation.table.window')}</th><th>{t('validation.table.status')}</th>
                        <th>{t('validation.table.gateCells')}</th><th>{t('validation.table.counts')}</th><th>{t('validation.table.ensemble')}</th><th>{t('validation.table.knownIssues')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {benchmarks.map((b) => (
                        <tr key={b.id} className={selected?.id === b.id ? 'vv-row-active' : ''} onClick={() => setOpenCase(b.id)}>
                          <td><button type="button" className="vv-linkbtn">{b.id}</button></td>
                          <td>{b.revision}</td>
                          <td>{b.window ? `${fmtNum(b.window[0], 1)}–${fmtNum(b.window[1], 1)} s` : '—'}</td>
                          <td><StatusBadge status={b.status} title={data.label_definitions[b.status]} /></td>
                          <td className="vv-cells">
                            {Object.entries(b.gate_cells).flatMap(([ph, rois]) => Object.entries(rois).map(([roi, c]) => (
                              <span key={`${ph}/${roi}`} className={`vv-cell vv-status-${(c?.status ?? 'na').toLowerCase()}`} title={`${ph} × ${roi}: ${c?.status} (n=${c?.n}${c?.worst ? `, worst ${c.worst}` : ''})`}>{roi}</span>
                            )))}
                          </td>
                          <td className="vv-small">{Object.entries(b.counts).filter(([k]) => k !== 'INFO').map(([k, v]) => `${k} ${v}`).join(' · ')}</td>
                          <td>{b.ensemble?.n_gpu ? `${b.ensemble.n_gpu}/${b.ensemble.n_fds}` : '—'}</td>
                          <td className="vv-small">{b.known_issues.join(', ') || '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {selected && <CaseDetail b={selected} version={version} p2={p2ByCase.get(selected.id) ?? []} />}

                {data.phase2 && (
                  <>
                    <h3>{t('validation.phase2Title')}</h3>
                    <p className="vv-muted vv-small">{t('validation.phase2Desc')}</p>
                    <div className="vv-scroll">
                      <table className="vv-table vv-small">
                        <thead>
                          <tr><th>{t('validation.table.case')}</th><th>{t('validation.table.quantity')}</th><th>{t('validation.table.point')}</th><th>Exp</th><th>FDS</th><th>GPU</th>
                            <th>FDS/Exp</th><th>GPU/Exp</th><th>GPU/FDS</th><th>2σ̃</th><th>{t('validation.table.quadrant')}</th></tr>
                        </thead>
                        <tbody>
                          {data.phase2.cases.map((p, i) => (
                            <tr key={`${p.id}-${i}`}>
                              <td>{p.id}</td><td>{p.quantity}</td><td>{p.label} <span className="vv-muted">[{p.metric}]</span></td>
                              <td>{fmtNum(p.exp)}</td><td>{fmtNum(p.fds)}</td><td>{fmtNum(p.gpu)}</td>
                              <td className={p.within?.fds_exp === false ? 'vv-off' : ''}>{fmtPct(p.rel?.fds_exp)}</td>
                              <td className={p.within?.gpu_exp === false ? 'vv-off' : ''}>{fmtPct(p.rel?.gpu_exp)}</td>
                              <td>{fmtPct(p.rel?.gpu_fds)}</td>
                              <td>±{p.band2 !== null ? (p.band2 * 100).toFixed(0) : '—'} %</td>
                              <td><QuadrantBadge q={p.quadrant} /></td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                    <h4>{t('validation.categories')}</h4>
                    <div className="vv-scroll">
                      <table className="vv-table vv-small">
                        <thead><tr><th>{t('validation.table.category')}</th><th>n</th><th>Guide σ̃_E</th><th>Guide σ̃_M</th><th>Guide δ</th><th>FDS δ</th><th>FDS σ̃_M</th><th>GPU δ</th><th>GPU σ̃_M</th><th>{t('validation.table.quadrant')}</th></tr></thead>
                        <tbody>
                          {data.phase2.categories.map((c) => (
                            <tr key={c.name}>
                              <td>{c.name}</td><td>{c.n}</td>
                              <td>{fmtNum(c.guide?.sigma_e, 2)}</td><td>{fmtNum(c.guide?.sigma_m, 2)}</td><td>{fmtNum(c.guide?.delta, 2)}</td>
                              <td>{fmtNum(c.fds.delta, 2)}</td><td>{fmtNum(c.fds.sigma_m, 2)}</td><td>{fmtNum(c.gpu.delta, 2)}</td><td>{fmtNum(c.gpu.sigma_m, 2)}</td>
                              <td><QuadrantBar counts={c.quadrants} compact /></td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </>
                )}
              </section>

              {/* ── 3. Spatial / Statistical ───────────────────────────── */}
              <section className="vv-block">
                <h2>{t('validation.blocks.spatial')}</h2>
                <p className="vv-muted vv-small">{t('validation.spatialDesc')}</p>
                <div className="vv-figgrid">
                  {benchmarks.filter((b) => b.figures.some((f) => f.endsWith('T_mean.png'))).map((b) => (
                    <figure key={b.id} className="vv-fig">
                      <img src={figureUrl(version, b.figures.find((f) => f.endsWith('T_mean.png')) as string)} alt={`${b.id} T mean`} loading="lazy" />
                      <figcaption>{b.id}</figcaption>
                    </figure>
                  ))}
                </div>
              </section>

              {/* ── 4. Audit Information ───────────────────────────────── */}
              <section className="vv-block">
                <h2>{t('validation.blocks.audit')}</h2>
                <table className="vv-table vv-small vv-kv">
                  <tbody>
                    <tr><th>{t('validation.audit.solverCommit')}</th><td>{data.engine.commits.map((c) => <code key={c} className="vv-sha">{c}</code>)}</td></tr>
                    <tr><th>{t('validation.audit.reference')}</th><td>FDS {data.reference.fds} · firemodels/fds <Sha value={data.reference.firemodels_fds_commit} n={8} /> · firemodels/exp <Sha value={data.reference.exp_commit} n={8} /> · Guide {data.reference.guide}</td></tr>
                    <tr><th>{t('validation.audit.analysisVersion')}</th><td>vvlib {data.reference.vvlib} · BULC-VV <code className="vv-sha">{data.reference.bulc_vv_commit}</code></td></tr>
                    <tr><th>{t('validation.audit.hardware')}</th><td>{data.hardware.gpu} · driver {data.hardware.driver} · {data.hardware.os}</td></tr>
                    <tr><th>{t('validation.audit.package')}</th><td>
                      {data.audit.package_uploaded && data.audit.package_url
                        ? <a href={data.audit.package_url}>{data.audit.package_url.split('/').pop()}</a>
                        : <span className="vv-muted">{t('validation.audit.packagePending')}</span>}
                      {' '}({fmtBytes(data.audit.package_bytes)}) · sha256 <Sha value={data.audit.package_sha256} n={16} />
                      {data.audit.excluded_private_cases?.length ? ` · ${t('validation.audit.excluded')}: ${data.audit.excluded_private_cases.join(', ')}` : ''}
                    </td></tr>
                  </tbody>
                </table>
                <div className="vv-scroll">
                  <table className="vv-table vv-small">
                    <thead><tr><th>{t('validation.table.case')}</th><th>rev</th><th>{t('validation.audit.commit')}</th><th>{t('validation.audit.mesh')}</th><th>{t('validation.audit.parameters')}</th><th>{t('validation.audit.deck')}</th><th>{t('validation.audit.rawHash')}</th><th>{t('validation.audit.provenance')}</th><th>{t('validation.audit.retention')}</th><th>{t('validation.audit.verified')}</th></tr></thead>
                    <tbody>
                      {data.audit.cases.map((a) => (
                        <tr key={a.id}>
                          <td>{a.id}</td><td>{a.revision}</td><td><code className="vv-sha">{a.engine_commit}</code></td>
                          <td className="vv-small">{a.mesh.map((m, i) => <div key={i}>{m.ijk.join('×')}</div>)}</td>
                          <td className="vv-small">{Object.entries(a.parameters).map(([k, v]) => `${k}=${String(v)}`).join(' ')}</td>
                          <td><Sha value={a.deck_sha256} n={8} /></td>
                          <td><Sha value={a.raw.sha256_digest} n={8} /> <span className="vv-muted">({a.raw.n_files} files, {fmtBytes(a.raw.bytes)})</span></td>
                          <td><Sha value={a.provenance_sha256} n={8} /></td>
                          <td>{a.retention.deleted_at ? `${t('validation.audit.deleted')} ${a.retention.deleted_at.slice(0, 10)}` : `${t('validation.audit.expires')} ${a.retention.expires ?? '—'}`}</td>
                          <td>{a.verification.ok === true ? '✓' : a.verification.ok === false ? '✗' : '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="vv-muted vv-small">{t('validation.audit.note')}</p>
              </section>
            </>
          )}
        </div>
      </main>
      <Footer />
    </div>
  );
};

const CaseDetail: React.FC<{ b: Benchmark; version: string; p2: Phase2Point[] }> = ({ b, version, p2 }) => {
  const { t } = useTranslation();
  const metricRows = Object.entries(b.metrics);
  return (
    <div className="vv-case">
      <h4>{b.id} <span className="vv-muted">rev {b.revision}</span> <StatusBadge status={b.status} /></h4>
      {b.note && <p className="vv-muted vv-small">{b.note}</p>}
      <div className="vv-case-grid">
        <div>
          <div className="vv-scroll">
          <table className="vv-table vv-small">
            <thead><tr><th>metric</th><th>FDS</th><th>GPU</th><th>{t('validation.table.value')}</th><th>envelope</th><th>ROI/phase</th><th>{t('validation.table.status')}</th></tr></thead>
            <tbody>
              {metricRows.map(([k, m]) => (
                <tr key={k}>
                  <td><code>{k}</code></td><td>{fmtNum(m.fds)}</td><td>{fmtNum(m.gpu)}</td><td>{fmtNum(m.value)}</td><td>{fmtNum(m.envelope)}</td>
                  <td className="vv-muted">{m.roi ?? '—'}/{m.phase ?? '—'}</td>
                  <td>{m.status ? <StatusBadge status={m.status} title={`${m.significance ?? ''}${m.magnitude_tier ? ` · tier ${m.magnitude_tier}` : ''}`} /> : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
          {p2.length > 0 && (
            <p className="vv-small">{t('validation.phase2Short')}: {p2.map((p, i) => <span key={i}>{p.label} <QuadrantBadge q={p.quadrant} /> </span>)}</p>
          )}
        </div>
        <div className="vv-figcol">
          {b.figures.map((f) => (
            <figure key={f} className="vv-fig">
              <img src={figureUrl(version, f)} alt={`${b.id} ${f.split('/').pop()}`} loading="lazy" />
              <figcaption>{f.split('/').pop()?.replace('.png', '')}</figcaption>
            </figure>
          ))}
        </div>
      </div>
    </div>
  );
};

export default ValidationVersionPage;
