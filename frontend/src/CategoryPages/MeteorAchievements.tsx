import React, { useState } from 'react';
import './MeteorPages.css';

type TabType = 'projects' | 'services' | 'papers';

const MeteorAchievements: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabType>('projects');

  return (
    <section className="meteor-section meteor-achievements">
      <div className="meteor-container">
        <div className="section-header">
          <div className="section-eyebrow">TRACK RECORD</div>
          <h2 className="section-title">주요 성과</h2>
          <p className="section-description">
            정부 과제, 기업 프로젝트, 학술 연구를 통해 검증된 기술력을 보유하고 있습니다.
          </p>
        </div>

        <div className="tabs">
          <button
            className={`tab ${activeTab === 'projects' ? 'active' : ''}`}
            onClick={() => setActiveTab('projects')}
          >
            연구개발 과제
          </button>
          <button
            className={`tab ${activeTab === 'services' ? 'active' : ''}`}
            onClick={() => setActiveTab('services')}
          >
            용역 프로젝트
          </button>
          <button
            className={`tab ${activeTab === 'papers' ? 'active' : ''}`}
            onClick={() => setActiveTab('papers')}
          >
            연구 논문
          </button>
        </div>

        {/* Projects Tab */}
        {activeTab === 'projects' && (
          <div className="tabpanel">
            <table className="data-table">
              <colgroup>
                <col style={{ width: '50%' }} />
                <col style={{ width: '20%' }} />
                <col style={{ width: '20%' }} />
                <col style={{ width: '10%' }} />
              </colgroup>
              <thead>
                <tr>
                  <th>과제명</th>
                  <th>기간</th>
                  <th>부처</th>
                  <th>상태</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>배터리/ESS/전기자동차 화재 확산과 피해 예측을 위한 인공지능 응용형 시뮬레이션 프로그램 개발</td>
                  <td>2023.04~2027.03</td>
                  <td>중소벤처기업부</td>
                  <td className="status-active">진행중</td>
                </tr>
                <tr>
                  <td>CO 흡입위치 감지 기능을 갖춘 연기감지시스템(ASD)를 통한 ESS 화재징후 조기 탐지 및 대응 기술의 개발</td>
                  <td>2023.07~2025.07</td>
                  <td>중소벤처기업부</td>
                  <td className="status-active">진행중</td>
                </tr>
                <tr>
                  <td>소방 성능 위주 설계용 전기차 화재 시뮬레이션 서비스 개발</td>
                  <td>2024.08~2025.07</td>
                  <td>중소벤처기업부</td>
                  <td className="status-active">진행중</td>
                </tr>
                <tr>
                  <td>빅데이터 플랫폼 기반 분석서비스 지원</td>
                  <td>2025.04~2025.12</td>
                  <td>화재보험협회</td>
                  <td className="status-active">진행중</td>
                </tr>
              </tbody>
            </table>
          </div>
        )}

        {/* Services Tab */}
        {activeTab === 'services' && (
          <div className="tabpanel">
            <table className="data-table">
              <colgroup>
                <col style={{ width: '70%' }} />
                <col style={{ width: '30%' }} />
              </colgroup>
              <thead>
                <tr>
                  <th>프로젝트명</th>
                  <th>발주처</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>디지털트윈 기술을 활용한 지하철 역사 화재 및 피난 대응 서비스</td>
                  <td>인천스타트업파크(인천교통공사)</td>
                </tr>
                <tr>
                  <td>전기차 및 ESS 리튬 배터리 화재진압을 위한 전용 소화약제 개발 조사 및 분석</td>
                  <td>(주)한국방염기술</td>
                </tr>
                <tr>
                  <td>열차용 배터리 화재 시뮬레이션 서비스</td>
                  <td>서울교통공사</td>
                </tr>
                <tr>
                  <td>전기차 화재 확산 방지 및 건축물 연기 확산</td>
                  <td>삼성전자 EHS</td>
                </tr>
                <tr>
                  <td>성능위주 설계를 위한 시뮬레이션 교육 및 컨설팅</td>
                  <td>신화엔지니어링</td>
                </tr>
                <tr>
                  <td>성수 2지구 AI Agent 이용한 화재 시뮬레이션</td>
                  <td>GS 건설</td>
                </tr>
              </tbody>
            </table>
          </div>
        )}

        {/* Papers Tab */}
        {activeTab === 'papers' && (
          <div className="tabpanel">
            <table className="data-table">
              <colgroup>
                <col style={{ width: '10%' }} />
                <col style={{ width: '60%' }} />
                <col style={{ width: '30%' }} />
              </colgroup>
              <thead>
                <tr>
                  <th>연도</th>
                  <th>논문제목</th>
                  <th>저널</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>2023</td>
                  <td>Development of thermal runaway propagation model considering vent gas combustion for electric vehicles</td>
                  <td>Journal of Energy Storage</td>
                </tr>
                <tr>
                  <td>2022</td>
                  <td>Predicting the Fire Source Location by Using the Pipe Hole Network in Aspirating Smoke Detection System</td>
                  <td>Applied Sciences</td>
                </tr>
                <tr>
                  <td>2021</td>
                  <td>Experimental Study on Effect of Tunnel Slope on Heat Release Rate with Heat Feedback Mechanism</td>
                  <td>Fire Technology</td>
                </tr>
                <tr>
                  <td>2020</td>
                  <td>A numerical analysis of the fire characteristics after sprinkler activation in the compartment fire</td>
                  <td>Energies</td>
                </tr>
                <tr>
                  <td>2020</td>
                  <td>Numerical study of the effects of the jet fan speed, heat release rate and aspect ratio on smoke movement in tunnel fires</td>
                  <td>Energies</td>
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
};

export default MeteorAchievements;
