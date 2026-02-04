import React from 'react';
import { X, CheckCircle, Code2, Zap, FileBarChart2 } from 'lucide-react';

const ComparisonSection: React.FC = () => {
  return (
    <section id="comparison" className="bulc-comparison">
      <div className="bulc-comparison__container">
        <div className="bulc-comparison__header">
          <h2 className="bulc-comparison__label">Comparison</h2>
          <h3 className="bulc-comparison__title">
            Why Switch to BUL:C Now?
          </h3>
          <p className="bulc-comparison__subtitle">
            Traditional methods are slow, error-prone, and expensive. It's time
            to upgrade your workflow.
          </p>
        </div>

        <div className="bulc-comparison__grid">
          {/* Card 1: Manual */}
          <div className="bulc-comparison__card">
            <div className="bulc-comparison__card-accent" />
            <div className="bulc-comparison__card-icon-wrap bulc-comparison__card-icon-wrap--muted">
              <Code2 className="bulc-comparison__card-icon" />
            </div>
            <h4 className="bulc-comparison__card-title">Manual Authoring</h4>
            <ul className="bulc-comparison__card-list">
              <li className="bulc-comparison__card-item">
                <X className="bulc-comparison__card-x" />
                Prone to syntax errors
              </li>
              <li className="bulc-comparison__card-item">
                <X className="bulc-comparison__card-x" />
                Weeks of setup time
              </li>
            </ul>
            <div className="bulc-comparison__card-divider" />
            <div className="bulc-comparison__card-solution">
              <p className="bulc-comparison__card-solution-title">
                <CheckCircle className="bulc-comparison__card-check" />
                AI-Assisted Workflow
              </p>
              <p className="bulc-comparison__card-solution-desc">
                BUL:C auto-generates FDS code from your CAD models instantly.
              </p>
            </div>
          </div>

          {/* Card 2: Speed (Highlighted) */}
          <div className="bulc-comparison__card bulc-comparison__card--featured">
            <div className="bulc-comparison__card-badge">Recommended</div>
            <div className="bulc-comparison__card-icon-wrap bulc-comparison__card-icon-wrap--accent">
              <Zap className="bulc-comparison__card-icon" />
            </div>
            <h4 className="bulc-comparison__card-title">Simulation Speed</h4>
            <ul className="bulc-comparison__card-list">
              <li className="bulc-comparison__card-item">
                <X className="bulc-comparison__card-x" />
                Days of CPU rendering
              </li>
              <li className="bulc-comparison__card-item">
                <X className="bulc-comparison__card-x" />
                Bottlenecked by hardware
              </li>
            </ul>
            <div className="bulc-comparison__card-divider" />
            <div className="bulc-comparison__card-solution">
              <p className="bulc-comparison__card-solution-title">
                <CheckCircle className="bulc-comparison__card-check" />
                GPU Acceleration
              </p>
              <p className="bulc-comparison__card-solution-desc">
                Leverage cloud GPUs to run simulations 100x faster than local
                CPUs.
              </p>
            </div>
          </div>

          {/* Card 3: Static Reporting */}
          <div className="bulc-comparison__card">
            <div className="bulc-comparison__card-accent" />
            <div className="bulc-comparison__card-icon-wrap bulc-comparison__card-icon-wrap--muted">
              <FileBarChart2 className="bulc-comparison__card-icon" />
            </div>
            <h4 className="bulc-comparison__card-title">Static Reporting</h4>
            <ul className="bulc-comparison__card-list">
              <li className="bulc-comparison__card-item">
                <X className="bulc-comparison__card-x" />
                Manual data extraction
              </li>
              <li className="bulc-comparison__card-item">
                <X className="bulc-comparison__card-x" />
                Static, non-interactive
              </li>
            </ul>
            <div className="bulc-comparison__card-divider" />
            <div className="bulc-comparison__card-solution">
              <p className="bulc-comparison__card-solution-title">
                <CheckCircle className="bulc-comparison__card-check" />
                Dynamic Reports
              </p>
              <p className="bulc-comparison__card-solution-desc">
                Generate interactive reports with embedded 3D visualizations
                instantly.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default ComparisonSection;
