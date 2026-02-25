import React from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { License, Activation, Subscription, BillingKey } from '../types';

interface SubscriptionPanelProps {
  // 라이선스
  licenses: License[];
  isLoadingLicenses: boolean;
  expandedLicenseId: string | null;
  activations: Record<string, Activation[]>;
  isLoadingActivations: string | null;
  onToggleDeviceList: (licenseId: string) => void;
  onDeactivateDevice: (licenseId: string, deviceFingerprint: string) => void;
  // 구독
  subscriptions: Subscription[];
  isLoadingSubscriptions: boolean;
  billingKeys: BillingKey[];
  onToggleAutoRenew: (subscriptionId: number, currentState: boolean) => void;
  formatPrice: (price: number, currency: string) => string;
  // 테스트 모드
  isAdmin: boolean;
  isTestMode: boolean;
  testLoading: string | null;
  onSimulateNearExpiry: (subscriptionId: number, days: number) => void;
  onMakeDueNow: (subscriptionId: number) => void;
}

const SubscriptionPanel: React.FC<SubscriptionPanelProps> = ({
  licenses,
  isLoadingLicenses,
  expandedLicenseId,
  activations,
  isLoadingActivations,
  onToggleDeviceList,
  onDeactivateDevice,
  subscriptions,
  isLoadingSubscriptions,
  billingKeys,
  onToggleAutoRenew,
  formatPrice,
  isAdmin,
  isTestMode,
  testLoading,
  onSimulateNearExpiry,
  onMakeDueNow,
}) => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <>
      {/* 라이선스 정보 */}
      <div className="info-card license-card">
        <div className="card-header">
          <h2 className="card-title">{t('myPage.license')}</h2>
        </div>
        {isLoadingLicenses ? (
          <div className="loading-text">{t('myPage.loading')}</div>
        ) : licenses.length === 0 ? (
          <div className="empty-licenses">
            <svg className="empty-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M9 12L11 14L15 10M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <p>{t('myPage.noLicense')}</p>
            <button className="purchase-btn" onClick={() => navigate('/payment')}>
              {t('myPage.purchaseLicense')}
            </button>
          </div>
        ) : (
          <div className="license-list">
            {licenses.map((license) => (
              <div key={license.id} className={`license-item ${license.status.toLowerCase()}`}>
                <div className="license-header">
                  <span className="license-product">
                    {license.productName || license.planName || 'BULC'}
                  </span>
                  <span className={`license-status status-${license.status.toLowerCase()}`}>
                    {license.status === 'ACTIVE' ? '활성' :
                     license.status === 'PENDING' ? '대기' :
                     license.status === 'EXPIRED_GRACE' ? '만료 유예' :
                     license.status === 'EXPIRED_HARD' ? '만료됨' :
                     license.status === 'SUSPENDED' ? '정지됨' :
                     license.status === 'REVOKED' ? '취소됨' : license.status}
                  </span>
                </div>
                <div className="license-details">
                  <div className="license-detail-row">
                    <span className="detail-label">라이선스 유형</span>
                    <span className="detail-value">
                      {license.licenseType === 'SUBSCRIPTION' ? '구독형' :
                       license.licenseType === 'PERPETUAL' ? '영구' :
                       license.licenseType === 'TRIAL' ? '체험판' : license.licenseType}
                    </span>
                  </div>
                  <div className="license-detail-row">
                    <span className="detail-label">유효 기간</span>
                    <span className="detail-value">
                      {license.validFrom ? new Date(license.validFrom).toLocaleDateString('ko-KR') : '-'}
                      {' ~ '}
                      {license.validUntil ? new Date(license.validUntil).toLocaleDateString('ko-KR') : '무제한'}
                    </span>
                  </div>
                  <div
                    className="license-detail-row device-toggle-row"
                    onClick={() => onToggleDeviceList(license.id)}
                  >
                    <span className="detail-label">기기 등록</span>
                    <span className="detail-value device-toggle-value">
                      {license.usedActivations} / {license.maxActivations}대
                      <span className={`device-toggle-icon ${expandedLicenseId === license.id ? 'expanded' : ''}`}>
                        ▼
                      </span>
                    </span>
                  </div>
                  {expandedLicenseId === license.id && (
                    <div className="device-list">
                      {isLoadingActivations === license.id ? (
                        <div className="device-loading">기기 목록을 불러오는 중...</div>
                      ) : !activations[license.id] || activations[license.id].length === 0 ? (
                        <div className="device-empty">등록된 기기가 없습니다.</div>
                      ) : (
                        activations[license.id].map((activation) => (
                          <div key={activation.id} className="device-item">
                            <div className="device-info">
                              <span className="device-name">
                                {activation.deviceDisplayName || activation.clientOs || '알 수 없는 기기'}
                              </span>
                              <span className="device-meta">
                                {activation.clientOs && <span className="device-os">{activation.clientOs}</span>}
                                {activation.clientVersion && <span className="device-version"> v{activation.clientVersion}</span>}
                              </span>
                              <span className="device-meta">
                                마지막 접속: {activation.lastSeenAt
                                  ? new Date(activation.lastSeenAt).toLocaleString('ko-KR')
                                  : '-'}
                              </span>
                            </div>
                            <div className="device-actions">
                              <span className={`device-status-badge status-${activation.status.toLowerCase()}`}>
                                {activation.status === 'ACTIVE' ? '활성' :
                                 activation.status === 'STALE' ? '비활성' :
                                 activation.status === 'DEACTIVATED' ? '해제됨' :
                                 activation.status === 'EXPIRED' ? '만료' : activation.status}
                              </span>
                              {(activation.status === 'ACTIVE' || activation.status === 'STALE') && (
                                <button
                                  className="device-deactivate-btn"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    onDeactivateDevice(license.id, activation.deviceFingerprint);
                                  }}
                                >
                                  해제
                                </button>
                              )}
                            </div>
                          </div>
                        ))
                      )}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 구독 관리 */}
      <div className="info-card subscription-card">
        <div className="card-header">
          <h2 className="card-title">{t('myPage.subscription')}</h2>
        </div>
        {isLoadingSubscriptions ? (
          <div className="loading-text">{t('myPage.loading')}</div>
        ) : subscriptions.length === 0 ? (
          <div className="empty-subscriptions">
            <svg className="empty-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M4 4H20C21.1 4 22 4.9 22 6V18C22 19.1 21.1 20 20 20H4C2.9 20 2 19.1 2 18V6C2 4.9 2.9 4 4 4Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M22 6L12 13L2 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <p>{t('myPage.noSubscription')}</p>
          </div>
        ) : (
          <div className="subscription-list">
            {subscriptions.map((sub) => (
              <div key={sub.id} className={`subscription-item status-${sub.status.toLowerCase()}`}>
                <div className="subscription-header">
                  <span className="subscription-product">
                    {sub.productName || sub.pricePlanName || '구독'}
                  </span>
                  <span className={`subscription-status ${sub.status === 'A' ? 'active' : sub.status === 'E' ? 'expired' : 'canceled'}`}>
                    {sub.status === 'A' ? '활성' : sub.status === 'E' ? '만료됨' : '취소됨'}
                  </span>
                </div>
                <div className="subscription-details">
                  <div className="subscription-detail-row">
                    <span className="detail-label">요금</span>
                    <span className="detail-value">{formatPrice(sub.price, sub.currency)}</span>
                  </div>
                  <div className="subscription-detail-row">
                    <span className="detail-label">구독 기간</span>
                    <span className="detail-value">
                      {new Date(sub.startDate).toLocaleDateString('ko-KR')} ~ {new Date(sub.endDate).toLocaleDateString('ko-KR')}
                    </span>
                  </div>
                  {sub.status === 'A' && (
                    <div className="subscription-detail-row auto-renew-row">
                      <span className="detail-label">자동 갱신</span>
                      <div className="auto-renew-toggle">
                        <label className="toggle-switch">
                          <input
                            type="checkbox"
                            checked={sub.autoRenew}
                            onChange={() => onToggleAutoRenew(sub.id, sub.autoRenew)}
                            disabled={billingKeys.length === 0}
                          />
                          <span className="toggle-slider"></span>
                        </label>
                        <span className="toggle-label">{sub.autoRenew ? 'ON' : 'OFF'}</span>
                      </div>
                    </div>
                  )}
                  {sub.autoRenew && sub.nextBillingDate && (
                    <div className="subscription-detail-row">
                      <span className="detail-label">다음 결제일</span>
                      <span className="detail-value next-billing">
                        {new Date(sub.nextBillingDate).toLocaleDateString('ko-KR')}
                      </span>
                    </div>
                  )}
                  {/* 개발 테스트 버튼 (관리자 전용) */}
                  {isAdmin && isTestMode && (
                    <div className="subscription-test-actions">
                      <div className="test-label">테스트 액션</div>
                      <div className="test-buttons">
                        <button
                          className="test-btn"
                          onClick={() => onSimulateNearExpiry(sub.id, 3)}
                          disabled={testLoading !== null}
                        >
                          {testLoading === `simulate-${sub.id}` ? '처리중...' : '3일 후 만료'}
                        </button>
                        <button
                          className="test-btn"
                          onClick={() => onMakeDueNow(sub.id)}
                          disabled={testLoading !== null || !sub.autoRenew}
                          title={!sub.autoRenew ? '자동 갱신이 활성화되어야 합니다' : ''}
                        >
                          {testLoading === `due-${sub.id}` ? '처리중...' : '즉시 갱신 대상'}
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
};

export default SubscriptionPanel;
