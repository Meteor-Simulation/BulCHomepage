import React, { useState } from 'react';
import './MeteorPages.css';

const BulCTutorial: React.FC = () => {
  const [showAll, setShowAll] = useState(false);

  const tutorials = [
    { id: 1, url: 'https://www.youtube.com/embed/pykuJxTP-Yo', title: 'BULC 튜토리얼 1' },
    { id: 2, url: 'https://www.youtube.com/embed/bjMh2Rz1_ss', title: 'BULC 튜토리얼 2' },
    { id: 3, url: 'https://www.youtube.com/embed/85P_ZNZ3dOE', title: 'BULC 튜토리얼 3' },
    { id: 4, url: 'https://www.youtube.com/embed/colbQGQYNmU', title: 'BULC 튜토리얼 4' },
  ];

  const visibleTutorials = showAll ? tutorials : tutorials.slice(0, 3);

  return (
    <section className="bulc-tutorial-section">
      <div className="meteor-container">
        <div className="section-header">
          <div className="section-eyebrow">LEARN BULC</div>
          <h2 className="section-title">BULC 튜토리얼</h2>
          <p className="section-description">
            BULC 사용 방법을 단계별로 배우고 화재 시뮬레이션 전문가가 되어보세요.
          </p>
        </div>

        <div className="tutorial-grid">
          {visibleTutorials.map((tutorial) => (
            <div key={tutorial.id} className="tutorial-item">
              <iframe
                src={tutorial.url}
                title={tutorial.title}
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowFullScreen
              ></iframe>
            </div>
          ))}
        </div>

        {!showAll && tutorials.length > 3 && (
          <div className="tutorial-more">
            <button className="btn-more" onClick={() => setShowAll(true)}>
              더 많은 튜토리얼 보기
            </button>
          </div>
        )}
      </div>
    </section>
  );
};

export default BulCTutorial;
