import React from 'react';
import { ShieldCheck, Check, Server, Workflow } from 'lucide-react';

const CoreValuesSection: React.FC = () => {
  return (
    <section id="core-values" className="bulc-values">
      <div className="bulc-values__container">
        <div className="bulc-values__header">
          <h2 className="bulc-values__title">Built on 3 Core Values</h2>
        </div>

        <div className="bulc-values__list">
          {/* Value 1: Forensic Accuracy */}
          <div className="bulc-values__item">
            <div className="bulc-values__image-wrap">
              <div className="bulc-values__image-bg bulc-values__image-bg--blue" />
              <img
                src="https://picsum.photos/seed/security/600/400"
                alt="Forensic Accuracy"
                className="bulc-values__image"
              />
            </div>
            <div className="bulc-values__text">
              <div className="bulc-values__icon-row">
                <div className="bulc-values__icon-wrap">
                  <ShieldCheck className="bulc-values__icon" />
                </div>
                <h3 className="bulc-values__name">Forensic Accuracy</h3>
              </div>
              <p className="bulc-values__desc">
                Precision is non-negotiable when lives are at stake. BULC
                utilizes validated FDS physics engines, ensuring every simulation
                meets rigorous international safety standards (ISO/ASTM).
              </p>
              <ul className="bulc-values__checks">
                <li className="bulc-values__check-item">
                  <Check className="bulc-values__check-icon" />
                  Validated against real-world fire tests
                </li>
                <li className="bulc-values__check-item">
                  <Check className="bulc-values__check-icon" />
                  Precise smoke and heat propagation models
                </li>
              </ul>
            </div>
          </div>

          {/* Value 2: Unrivaled Speed */}
          <div className="bulc-values__item bulc-values__item--reverse">
            <div className="bulc-values__image-wrap">
              <div className="bulc-values__image-bg bulc-values__image-bg--orange" />
              <img
                src="https://picsum.photos/seed/server/600/400"
                alt="Unrivaled Speed"
                className="bulc-values__image"
              />
            </div>
            <div className="bulc-values__text">
              <div className="bulc-values__icon-row">
                <div className="bulc-values__icon-wrap">
                  <Server className="bulc-values__icon" />
                </div>
                <h3 className="bulc-values__name">Unrivaled Speed</h3>
              </div>
              <p className="bulc-values__desc">
                Time is money. By offloading complex calculations to our cloud
                GPU cluster, what used to take days now takes hours. Iterate
                faster and deliver results sooner.
              </p>
              <ul className="bulc-values__checks">
                <li className="bulc-values__check-item">
                  <Check className="bulc-values__check-icon" />
                  Parallel processing on 1000+ cores
                </li>
                <li className="bulc-values__check-item">
                  <Check className="bulc-values__check-icon" />
                  Real-time preview rendering
                </li>
              </ul>
            </div>
          </div>

          {/* Value 3: Automation */}
          <div className="bulc-values__item">
            <div className="bulc-values__image-wrap">
              <div className="bulc-values__image-bg bulc-values__image-bg--slate" />
              <img
                src="https://picsum.photos/seed/robot/600/400"
                alt="End-to-End Automation"
                className="bulc-values__image"
              />
            </div>
            <div className="bulc-values__text">
              <div className="bulc-values__icon-row">
                <div className="bulc-values__icon-wrap">
                  <Workflow className="bulc-values__icon" />
                </div>
                <h3 className="bulc-values__name">End-to-End Automation</h3>
              </div>
              <p className="bulc-values__desc">
                From importing the CAD drawing to generating the final PDF
                compliance report, the entire pipeline is automated. Focus on
                engineering, not paperwork.
              </p>
              <ul className="bulc-values__checks">
                <li className="bulc-values__check-item">
                  <Check className="bulc-values__check-icon" />
                  One-click report generation
                </li>
                <li className="bulc-values__check-item">
                  <Check className="bulc-values__check-icon" />
                  Automated evacuation path analysis
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default CoreValuesSection;
