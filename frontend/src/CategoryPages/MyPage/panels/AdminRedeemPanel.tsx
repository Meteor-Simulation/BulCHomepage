import React from 'react';
import { useTranslation } from 'react-i18next';
import { RedeemCampaign, RedeemCodeItem, Product, LicensePlan } from '../types';

interface AdminRedeemPanelProps {
  isLoading: boolean;
  redeemCampaigns: RedeemCampaign[];
  products: Product[];
  filteredLicensePlansForRedeem: LicensePlan[];
  // 캠페인 모달
  isRedeemCampaignModalOpen: boolean;
  editingCampaign: RedeemCampaign | null;
  redeemCampaignForm: {
    name: string; description: string; productId: string; licensePlanId: string;
    usageCategory: string; seatLimit: string; perUserLimit: string;
    validFrom: string; validUntil: string;
  };
  onOpenCampaignModal: (campaign?: RedeemCampaign) => void;
  onCloseCampaignModal: () => void;
  onCampaignFormChange: (form: AdminRedeemPanelProps['redeemCampaignForm']) => void;
  onCampaignSubmit: () => void;
  onCampaignStatusChange: (campaignId: string, action: 'pause' | 'end' | 'resume') => void;
  // 코드 목록
  selectedCampaignForCodes: RedeemCampaign | null;
  redeemCodes: RedeemCodeItem[];
  onSelectCampaignForCodes: (campaign: RedeemCampaign) => void;
  onFetchRedeemCodes: (campaignId: string) => void;
  onDeactivateCode: (codeId: string) => void;
  // 코드 생성 모달
  isCodeGenerateModalOpen: boolean;
  codeGenerateForm: {
    codeType: 'RANDOM' | 'CUSTOM';
    customCode: string; count: string; maxRedemptions: string; expiresAt: string;
    allowedEmailDomain: string;
  };
  onOpenCodeGenerateModal: (campaign: RedeemCampaign) => void;
  onCloseCodeGenerateModal: () => void;
  onCodeGenerateFormChange: (form: AdminRedeemPanelProps['codeGenerateForm']) => void;
  onGenerateCodes: () => void;
  // 생성된 코드 결과 모달
  isGeneratedCodesModalOpen: boolean;
  generatedCodes: string[];
  onCloseGeneratedCodesModal: () => void;
  onCopyGeneratedCodes: () => void;
  onDownloadCodesAsCsv: () => void;
}

const isValidDomain = (domain: string): boolean => {
  if (!domain) return true; // 빈 값은 유효 (제한 없음)
  const domainRegex = /^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}$/;
  return domainRegex.test(domain);
};

const AdminRedeemPanel: React.FC<AdminRedeemPanelProps> = ({
  isLoading,
  redeemCampaigns,
  products,
  filteredLicensePlansForRedeem,
  isRedeemCampaignModalOpen,
  editingCampaign,
  redeemCampaignForm,
  onOpenCampaignModal,
  onCloseCampaignModal,
  onCampaignFormChange,
  onCampaignSubmit,
  onCampaignStatusChange,
  selectedCampaignForCodes,
  redeemCodes,
  onSelectCampaignForCodes,
  onFetchRedeemCodes,
  onDeactivateCode,
  isCodeGenerateModalOpen,
  codeGenerateForm,
  onOpenCodeGenerateModal,
  onCloseCodeGenerateModal,
  onCodeGenerateFormChange,
  onGenerateCodes,
  isGeneratedCodesModalOpen,
  generatedCodes,
  onCloseGeneratedCodesModal,
  onCopyGeneratedCodes,
  onDownloadCodesAsCsv,
}) => {
  const { t } = useTranslation();

  return (
    <>
      <div className="info-card admin-section-card wide">
        <div className="card-header">
          <h2 className="card-title">{t('myPage.menu.adminRedeem')}</h2>
          <button className="btn-action btn-edit" onClick={() => onOpenCampaignModal()} style={{ marginLeft: 'auto' }}>+ 캠페인 추가</button>
        </div>
        {isLoading ? (
          <div className="admin-loading">데이터 로딩 중...</div>
        ) : (
          <>
            <div className="admin-table-wrapper">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>캠페인명</th>
                    <th>상품</th>
                    <th>플랜</th>
                    <th>좌석</th>
                    <th>코드</th>
                    <th>상태</th>
                    <th>유효기간</th>
                    <th>관리</th>
                  </tr>
                </thead>
                <tbody>
                  {redeemCampaigns.length > 0 ? (
                    redeemCampaigns.map((campaign) => (
                      <tr key={campaign.id}>
                        <td>{campaign.name}</td>
                        <td>{campaign.productName}</td>
                        <td>{campaign.planName}</td>
                        <td>{campaign.seatsUsed}{campaign.seatLimit ? `/${campaign.seatLimit}` : '/∞'}</td>
                        <td>{campaign.codeCount}개</td>
                        <td>
                          <span className={`status-badge status-${campaign.status.toLowerCase()}`}>
                            {campaign.status === 'ACTIVE' ? '활성' : campaign.status === 'PAUSED' ? '일시정지' : '종료'}
                          </span>
                        </td>
                        <td>
                          {campaign.validFrom ? new Date(campaign.validFrom).toLocaleDateString() : '-'}
                          {' ~ '}
                          {campaign.validUntil ? new Date(campaign.validUntil).toLocaleDateString() : '무제한'}
                        </td>
                        <td>
                          <div className="action-buttons">
                            <button className="btn-action btn-edit" onClick={() => onOpenCampaignModal(campaign)}>수정</button>
                            <button className="btn-action btn-info" onClick={() => onOpenCodeGenerateModal(campaign)}>코드 생성</button>
                            <button className="btn-action btn-info" onClick={() => { onSelectCampaignForCodes(campaign); onFetchRedeemCodes(campaign.id); }}>코드 목록</button>
                            {campaign.status === 'ACTIVE' && (
                              <>
                                <button className="btn-action btn-warning" onClick={() => onCampaignStatusChange(campaign.id, 'pause')}>일시정지</button>
                                <button className="btn-action btn-danger" onClick={() => onCampaignStatusChange(campaign.id, 'end')}>종료</button>
                              </>
                            )}
                            {campaign.status === 'PAUSED' && (
                              <>
                                <button className="btn-action btn-edit" onClick={() => onCampaignStatusChange(campaign.id, 'resume')}>재개</button>
                                <button className="btn-action btn-danger" onClick={() => onCampaignStatusChange(campaign.id, 'end')}>종료</button>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr><td colSpan={8} className="empty-row">등록된 캠페인이 없습니다.</td></tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* 코드 목록 (캠페인 선택 시) */}
            {selectedCampaignForCodes && redeemCodes.length > 0 && (
              <div style={{ marginTop: '2rem' }}>
                <div className="card-header">
                  <h2 className="card-title">{selectedCampaignForCodes.name} - 코드 목록</h2>
                </div>
                <div className="admin-table-wrapper">
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>유형</th>
                        <th>사용횟수</th>
                        <th>활성</th>
                        <th>도메인 제한</th>
                        <th>만료일</th>
                        <th>생성일</th>
                        <th>관리</th>
                      </tr>
                    </thead>
                    <tbody>
                      {redeemCodes.map((code) => (
                        <tr key={code.id}>
                          <td>{code.codeType}</td>
                          <td>{code.currentRedemptions}/{code.maxRedemptions}</td>
                          <td>{code.active ? '활성' : '비활성'}</td>
                          <td>{code.allowedEmailDomain ? `@${code.allowedEmailDomain}` : '-'}</td>
                          <td>{code.expiresAt ? new Date(code.expiresAt).toLocaleString() : '-'}</td>
                          <td>{new Date(code.createdAt).toLocaleString()}</td>
                          <td>
                            {code.active && (
                              <button className="btn-action btn-danger" onClick={() => onDeactivateCode(code.id)}>비활성화</button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* 리딤 캠페인 모달 */}
      {isRedeemCampaignModalOpen && (
        <div className="delete-modal-overlay" onClick={onCloseCampaignModal}>
          <div className="delete-modal license-plan-modal" onClick={(e) => e.stopPropagation()}>
            <div className="delete-modal-header">
              <h3>{editingCampaign ? '캠페인 수정' : '캠페인 추가'}</h3>
            </div>
            <div className="delete-modal-body">
              <div className="form-group vertical">
                <label>캠페인명 <span style={{ color: '#dc3545' }}>*</span></label>
                <input type="text" value={redeemCampaignForm.name}
                  onChange={(e) => onCampaignFormChange({ ...redeemCampaignForm, name: e.target.value })}
                  placeholder="예: 2025 신년 프로모션"
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
              </div>
              <div className="form-group vertical" style={{ marginTop: '12px' }}>
                <label>설명</label>
                <textarea value={redeemCampaignForm.description}
                  onChange={(e) => onCampaignFormChange({ ...redeemCampaignForm, description: e.target.value })}
                  placeholder="캠페인 설명" rows={2}
                  className="license-plan-textarea" />
              </div>
              <div className="license-plan-form-row">
                <div className="form-group vertical">
                  <label>상품 <span style={{ color: '#dc3545' }}>*</span></label>
                  <select value={redeemCampaignForm.productId}
                    onChange={(e) => onCampaignFormChange({ ...redeemCampaignForm, productId: e.target.value, licensePlanId: '' })}
                    disabled={!!editingCampaign}
                    style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px' }}>
                    <option value="">상품 선택</option>
                    {products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                  </select>
                </div>
                <div className="form-group vertical">
                  <label>라이선스 플랜 <span style={{ color: '#dc3545' }}>*</span></label>
                  <select value={redeemCampaignForm.licensePlanId}
                    onChange={(e) => onCampaignFormChange({ ...redeemCampaignForm, licensePlanId: e.target.value })}
                    disabled={!!editingCampaign || !redeemCampaignForm.productId}
                    style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px' }}>
                    <option value="">플랜 선택</option>
                    {filteredLicensePlansForRedeem.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.licenseType})</option>)}
                  </select>
                </div>
              </div>
              <div className="license-plan-form-row">
                <div className="form-group vertical">
                  <label>좌석 한도</label>
                  <input type="number" value={redeemCampaignForm.seatLimit}
                    onChange={(e) => onCampaignFormChange({ ...redeemCampaignForm, seatLimit: e.target.value })}
                    placeholder="무제한" min={0}
                    style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
                </div>
                <div className="form-group vertical">
                  <label>사용자별 한도</label>
                  <input type="number" value={redeemCampaignForm.perUserLimit}
                    onChange={(e) => onCampaignFormChange({ ...redeemCampaignForm, perUserLimit: e.target.value })}
                    min={1}
                    style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
                </div>
              </div>
              <div className="license-plan-form-row">
                <div className="form-group vertical">
                  <label>시작일</label>
                  <input type="datetime-local" value={redeemCampaignForm.validFrom}
                    onChange={(e) => onCampaignFormChange({ ...redeemCampaignForm, validFrom: e.target.value })}
                    style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
                </div>
                <div className="form-group vertical">
                  <label>종료일</label>
                  <input type="datetime-local" value={redeemCampaignForm.validUntil}
                    onChange={(e) => onCampaignFormChange({ ...redeemCampaignForm, validUntil: e.target.value })}
                    style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
                </div>
              </div>
              <div className="form-group vertical" style={{ marginTop: '12px' }}>
                <label>사용 용도</label>
                <select value={redeemCampaignForm.usageCategory}
                  onChange={(e) => onCampaignFormChange({ ...redeemCampaignForm, usageCategory: e.target.value })}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px' }}>
                  <option value="COMMERCIAL">상업용</option>
                  <option value="RESEARCH_NON_COMMERCIAL">비상업 연구용</option>
                  <option value="EDUCATION">교육용</option>
                  <option value="INTERNAL_EVAL">내부 평가용</option>
                </select>
              </div>
            </div>
            <div className="delete-modal-footer">
              <button className="cancel-btn" onClick={onCloseCampaignModal}>취소</button>
              <button className="save-btn" onClick={onCampaignSubmit}>
                {editingCampaign ? '수정' : '등록'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 코드 생성 모달 */}
      {isCodeGenerateModalOpen && selectedCampaignForCodes && (
        <div className="delete-modal-overlay" onClick={onCloseCodeGenerateModal}>
          <div className="delete-modal license-plan-modal" onClick={(e) => e.stopPropagation()}>
            <div className="delete-modal-header">
              <h3>리딤 코드 생성 - {selectedCampaignForCodes.name}</h3>
            </div>
            <div className="delete-modal-body">
              <div className="form-group vertical">
                <label>코드 타입</label>
                <div style={{ display: 'flex', gap: '1rem', marginTop: '4px' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '4px', cursor: 'pointer' }}>
                    <input type="radio" value="RANDOM" checked={codeGenerateForm.codeType === 'RANDOM'}
                      onChange={() => onCodeGenerateFormChange({ ...codeGenerateForm, codeType: 'RANDOM' })} /> 랜덤 코드
                  </label>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '4px', cursor: 'pointer' }}>
                    <input type="radio" value="CUSTOM" checked={codeGenerateForm.codeType === 'CUSTOM'}
                      onChange={() => onCodeGenerateFormChange({ ...codeGenerateForm, codeType: 'CUSTOM' })} /> 커스텀 코드
                  </label>
                </div>
              </div>
              {codeGenerateForm.codeType === 'RANDOM' ? (
                <div className="form-group vertical" style={{ marginTop: '12px' }}>
                  <label>생성 수량</label>
                  <input type="number" value={codeGenerateForm.count}
                    onChange={(e) => onCodeGenerateFormChange({ ...codeGenerateForm, count: e.target.value })}
                    min={1} max={1000}
                    style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
                </div>
              ) : (
                <div className="form-group vertical" style={{ marginTop: '12px' }}>
                  <label>커스텀 코드</label>
                  <input type="text" value={codeGenerateForm.customCode}
                    onChange={(e) => onCodeGenerateFormChange({ ...codeGenerateForm, customCode: e.target.value.toUpperCase() })}
                    placeholder="영문 대문자, 숫자 8~64자"
                    style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
                </div>
              )}
              <div className="license-plan-form-row">
                <div className="form-group vertical">
                  <label>코드당 최대 사용횟수</label>
                  <input type="number" value={codeGenerateForm.maxRedemptions}
                    onChange={(e) => onCodeGenerateFormChange({ ...codeGenerateForm, maxRedemptions: e.target.value })}
                    min={1}
                    style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
                </div>
                <div className="form-group vertical">
                  <label>코드 만료일</label>
                  <input type="datetime-local" value={codeGenerateForm.expiresAt}
                    onChange={(e) => onCodeGenerateFormChange({ ...codeGenerateForm, expiresAt: e.target.value })}
                    style={{ width: '100%', padding: '10px 12px', border: '1px solid #e0e0e0', borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box' }} />
                </div>
              </div>
              <div className="form-group vertical" style={{ marginTop: '12px' }}>
                <label>이메일 도메인 제한</label>
                <input type="text" value={codeGenerateForm.allowedEmailDomain}
                  onChange={(e) => onCodeGenerateFormChange({ ...codeGenerateForm, allowedEmailDomain: e.target.value.toLowerCase().trim() })}
                  placeholder="예: univ.ac.kr (비워두면 제한 없음)"
                  style={{
                    width: '100%', padding: '10px 12px',
                    border: `1px solid ${codeGenerateForm.allowedEmailDomain && !isValidDomain(codeGenerateForm.allowedEmailDomain) ? '#dc3545' : '#e0e0e0'}`,
                    borderRadius: '8px', fontSize: '14px', boxSizing: 'border-box',
                  }} />
                {codeGenerateForm.allowedEmailDomain && !isValidDomain(codeGenerateForm.allowedEmailDomain) && (
                  <span style={{ color: '#dc3545', fontSize: '12px', marginTop: '4px' }}>
                    올바른 도메인 형식이 아닙니다 (예: example.ac.kr)
                  </span>
                )}
                {codeGenerateForm.allowedEmailDomain && isValidDomain(codeGenerateForm.allowedEmailDomain) && (
                  <span style={{ color: '#666', fontSize: '12px', marginTop: '4px' }}>
                    @{codeGenerateForm.allowedEmailDomain} 이메일만 사용 가능
                  </span>
                )}
              </div>
            </div>
            <div className="delete-modal-footer">
              <button className="cancel-btn" onClick={onCloseCodeGenerateModal}>취소</button>
              <button className="save-btn" onClick={onGenerateCodes}
                disabled={!!(codeGenerateForm.allowedEmailDomain && !isValidDomain(codeGenerateForm.allowedEmailDomain))}>생성</button>
            </div>
          </div>
        </div>
      )}

      {/* 생성된 코드 결과 모달 */}
      {isGeneratedCodesModalOpen && (
        <div className="delete-modal-overlay" onClick={onCloseGeneratedCodesModal}>
          <div className="delete-modal license-plan-modal" onClick={(e) => e.stopPropagation()}>
            <div className="delete-modal-header">
              <h3>생성된 리딤 코드</h3>
            </div>
            <div className="delete-modal-body">
              <p style={{ color: '#e53e3e', fontWeight: 'bold', marginBottom: '1rem' }}>
                이 코드는 이 화면에서만 확인할 수 있습니다. 반드시 복사하거나 다운로드하세요.
              </p>
              <div style={{ maxHeight: '300px', overflow: 'auto', background: '#f5f5f5', padding: '1rem', borderRadius: '8px', fontFamily: 'monospace', fontSize: '14px' }}>
                {generatedCodes.map((code, i) => <div key={i}>{code}</div>)}
              </div>
              <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
                <button className="save-btn" onClick={onCopyGeneratedCodes}>전체 복사</button>
                <button className="save-btn" onClick={onDownloadCodesAsCsv}>CSV 다운로드</button>
              </div>
            </div>
            <div className="delete-modal-footer">
              <button className="cancel-btn" onClick={onCloseGeneratedCodesModal}>닫기</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default AdminRedeemPanel;
