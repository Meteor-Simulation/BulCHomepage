import React from 'react';
import { ArrowRight, Play, Zap } from 'lucide-react';

interface HeroSectionProps {
  onPurchaseClick: () => void;
}

const HeroSection: React.FC<HeroSectionProps> = ({ onPurchaseClick }) => {
  return (
    <section id="hero" className="bulc-hero">
      {/* Background Effects */}
      <div className="bulc-hero__bg-pattern" />
      <div className="bulc-hero__bg-blob bulc-hero__bg-blob--right" />
      <div className="bulc-hero__bg-blob bulc-hero__bg-blob--left" />

      <div className="bulc-hero__container">
        <div className="bulc-hero__content">
          <div className="bulc-hero__badge">
            <span className="bulc-hero__badge-dot">
              <span className="bulc-hero__badge-ping" />
              <span className="bulc-hero__badge-core" />
            </span>
            New: AI-GPU Acceleration Enabled
          </div>

          <h1 className="bulc-hero__title">
            Automate Authoring to <br />
            <span className="bulc-hero__title-gradient">
              Simulation to Report
            </span>
          </h1>

          <p className="bulc-hero__description">
            Drastically reduce human error and gain full MCP control. BULC
            combines advanced FDS-based physics with AI automation for instant
            compliance reporting.
          </p>

          <div className="bulc-hero__actions">
            <button className="bulc-hero__btn bulc-hero__btn--primary" onClick={onPurchaseClick}>
              Start for Free
              <ArrowRight className="bulc-hero__btn-icon" />
            </button>
            <button className="bulc-hero__btn bulc-hero__btn--secondary">
              <Play className="bulc-hero__btn-icon bulc-hero__btn-icon--left" />
              Watch Demo
            </button>
          </div>
        </div>

        {/* Video Placeholder */}
        <div className="bulc-hero__video-wrapper">
          <div className="bulc-hero__video">
            {/* Overlay Gradient */}
            <div className="bulc-hero__video-overlay" />

            {/* Fake UI */}
            <div className="bulc-hero__video-ui">
              <div className="bulc-hero__video-grid">
                <div className="bulc-hero__video-col bulc-hero__video-col--sidebar" />
                <div className="bulc-hero__video-col bulc-hero__video-col--main">
                  <div className="bulc-hero__video-sim" />
                </div>
                <div className="bulc-hero__video-col bulc-hero__video-col--sidebar" />
              </div>
            </div>

            {/* Play Button */}
            <div className="bulc-hero__video-play">
              <div className="bulc-hero__video-play-btn">
                <Play className="bulc-hero__video-play-icon" />
              </div>
            </div>

            {/* Caption */}
            <div className="bulc-hero__video-caption">
              <p className="bulc-hero__video-caption-title">
                <Zap className="bulc-hero__video-caption-icon" />
                BULC Interface Walkthrough
              </p>
              <p className="bulc-hero__video-caption-sub">
                See how automation saves 40+ hours per project
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default HeroSection;
