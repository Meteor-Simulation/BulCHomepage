import React from 'react';
import { useTranslation } from 'react-i18next';

interface RedeemPanelProps {
  redeemCode: string;
  redeemResult: {
    success: boolean;
    licenseId?: string;
    licenseKey?: string;
    productName?: string;
    planName?: string;
    validUntil?: string;
    errorMessage?: string;
  } | null;
  isRedeeming: boolean;
  onRedeemCodeChange: (value: string) => void;
  onRedeemSubmit: () => void;
}

const RedeemPanel: React.FC<RedeemPanelProps> = ({
  redeemCode,
  redeemResult,
  isRedeeming,
  onRedeemCodeChange,
  onRedeemSubmit,
}) => {
  const { t } = useTranslation();
  return (
    <div className="info-card">
      <div className="card-header">
        <h2 className="card-title">{t('myPage.menu.redeem')}</h2>
      </div>
      <p style={{ color: '#666', marginBottom: '1.5rem', fontSize: '14px' }}>
        쿠폰 코드를 입력하여 라이선스를 등록하세요.
      </p>
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
        <input
          type="text"
          value={redeemCode}
          onChange={(e) => onRedeemCodeChange(e.target.value.toUpperCase())}
          onKeyDown={(e) => e.key === 'Enter' && onRedeemSubmit()}
          placeholder="XXXX-XXXX-XXXX-XXXX"
          style={{ flex: 1, padding: '0.75rem', borderRadius: '8px', border: '1px solid #d1d5db', fontSize: '16px', fontFamily: 'monospace', letterSpacing: '1px' }}
          disabled={isRedeeming}
        />
        <button
          onClick={onRedeemSubmit}
          disabled={isRedeeming || !redeemCode.trim()}
          style={{ padding: '0.75rem 1.5rem', borderRadius: '8px', background: '#C4320A', color: 'white', border: 'none', cursor: 'pointer', fontWeight: 600, opacity: isRedeeming || !redeemCode.trim() ? 0.5 : 1 }}
        >
          {isRedeeming ? '등록 중...' : '등록'}
        </button>
      </div>

      {redeemResult && (
        <div style={{ padding: '1rem', borderRadius: '8px', background: redeemResult.success ? '#f0fdf4' : '#fef2f2', border: `1px solid ${redeemResult.success ? '#86efac' : '#fecaca'}`, marginTop: '1rem' }}>
          {redeemResult.success ? (
            <div>
              <p style={{ color: '#166534', fontWeight: 'bold', marginBottom: '0.5rem' }}>라이선스가 성공적으로 등록되었습니다!</p>
              <div style={{ fontSize: '14px', color: '#333' }}>
                <p>제품: {redeemResult.productName}</p>
                <p>플랜: {redeemResult.planName}</p>
                <p>라이선스 키: <code style={{ background: '#e5e7eb', padding: '2px 6px', borderRadius: '4px' }}>{redeemResult.licenseKey}</code></p>
                {redeemResult.validUntil && (
                  <p>만료일: {new Date(redeemResult.validUntil).toLocaleDateString()}</p>
                )}
              </div>
            </div>
          ) : (
            <p style={{ color: '#991b1b' }}>{redeemResult.errorMessage}</p>
          )}
        </div>
      )}

      <div style={{ marginTop: '1.5rem', padding: '1rem', background: '#f9fafb', borderRadius: '8px', fontSize: '13px', color: '#6b7280' }}>
        <p style={{ fontWeight: 600, marginBottom: '0.5rem' }}>안내</p>
        <ul style={{ paddingLeft: '1.2rem', margin: 0 }}>
          <li>코드 입력 시 하이픈(-)과 공백은 자동으로 무시됩니다.</li>
          <li>코드는 대소문자를 구분하지 않습니다.</li>
          <li>분당 최대 5회까지 시도할 수 있습니다.</li>
        </ul>
      </div>
    </div>
  );
};

export default RedeemPanel;
