import React from 'react';
import { useTranslation } from 'react-i18next';
import { UserInfo } from '../types';

interface AccountPanelProps {
  userInfo: UserInfo;
  // 비밀번호 변경
  isEditingPassword: boolean;
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
  passwordError: string;
  showCurrentPassword: boolean;
  showNewPassword: boolean;
  showConfirmPassword: boolean;
  onStartEditPassword: () => void;
  onCurrentPasswordChange: (value: string) => void;
  onNewPasswordChange: (value: string) => void;
  onConfirmPasswordChange: (value: string) => void;
  onToggleShowCurrentPassword: () => void;
  onToggleShowNewPassword: () => void;
  onToggleShowConfirmPassword: () => void;
  onChangePassword: () => void;
  onCancelPassword: () => void;
  // 계정 삭제
  isDeleteModalOpen: boolean;
  deleteConfirmText: string;
  deleteModalError: string;
  isDeleting: boolean;
  onOpenDeleteModal: () => void;
  onCloseDeleteModal: () => void;
  onDeleteConfirmTextChange: (value: string) => void;
  onDeleteModalErrorClear: () => void;
  onDeleteAccount: () => void;
}

const EyeOpenIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
    <circle cx="12" cy="12" r="3"/>
  </svg>
);

const EyeClosedIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
    <line x1="1" y1="1" x2="23" y2="23"/>
  </svg>
);

const AccountPanel: React.FC<AccountPanelProps> = ({
  userInfo,
  isEditingPassword,
  currentPassword,
  newPassword,
  confirmPassword,
  passwordError,
  showCurrentPassword,
  showNewPassword,
  showConfirmPassword,
  onStartEditPassword,
  onCurrentPasswordChange,
  onNewPasswordChange,
  onConfirmPasswordChange,
  onToggleShowCurrentPassword,
  onToggleShowNewPassword,
  onToggleShowConfirmPassword,
  onChangePassword,
  onCancelPassword,
  isDeleteModalOpen,
  deleteConfirmText,
  deleteModalError,
  isDeleting,
  onOpenDeleteModal,
  onCloseDeleteModal,
  onDeleteConfirmTextChange,
  onDeleteModalErrorClear,
  onDeleteAccount,
}) => {
  const { t } = useTranslation();

  return (
    <>
      {/* 이메일 정보 */}
      <div className="info-card">
        <div className="card-header">
          <h2 className="card-title">{t('myPage.menu.account')}</h2>
        </div>
        <div className="info-list">
          <div className="info-row">
            <span className="info-label">{t('myPage.emailId')}</span>
            <span className="info-value">{userInfo.email}</span>
          </div>
        </div>

        {/* 비밀번호 변경 섹션 */}
        <div className="password-section">
          <div className="info-row">
            <span className="info-label">{t('myPage.password')}</span>
            {!isEditingPassword ? (
              <button className="password-change-btn" onClick={onStartEditPassword}>
                {t('myPage.changePassword')}
              </button>
            ) : (
              <span className="info-value">{t('myPage.loading')}</span>
            )}
          </div>
          {isEditingPassword && (
            <div className="password-edit-form">
              <div className="edit-form">
                <div className="form-group">
                  <label>{t('myPage.currentPassword')}</label>
                  <div className="password-input-wrapper">
                    <input
                      type={showCurrentPassword ? 'text' : 'password'}
                      value={currentPassword}
                      onChange={(e) => onCurrentPasswordChange(e.target.value)}
                      placeholder={t('myPage.currentPasswordPlaceholder')}
                    />
                    <button type="button" className="password-toggle-btn" onClick={onToggleShowCurrentPassword}>
                      {showCurrentPassword ? <EyeClosedIcon /> : <EyeOpenIcon />}
                    </button>
                  </div>
                </div>
                <div className="form-group">
                  <label>{t('myPage.newPassword')}</label>
                  <div className="password-input-wrapper">
                    <input
                      type={showNewPassword ? 'text' : 'password'}
                      value={newPassword}
                      onChange={(e) => onNewPasswordChange(e.target.value)}
                      placeholder={t('myPage.newPasswordPlaceholder')}
                    />
                    <button type="button" className="password-toggle-btn" onClick={onToggleShowNewPassword}>
                      {showNewPassword ? <EyeClosedIcon /> : <EyeOpenIcon />}
                    </button>
                  </div>
                </div>
                <div className="form-group">
                  <label>{t('myPage.confirmPassword')}</label>
                  <div className="password-input-wrapper">
                    <input
                      type={showConfirmPassword ? 'text' : 'password'}
                      value={confirmPassword}
                      onChange={(e) => onConfirmPasswordChange(e.target.value)}
                      placeholder={t('myPage.confirmPasswordPlaceholder')}
                    />
                    <button type="button" className="password-toggle-btn" onClick={onToggleShowConfirmPassword}>
                      {showConfirmPassword ? <EyeClosedIcon /> : <EyeOpenIcon />}
                    </button>
                  </div>
                </div>
                {passwordError && (
                  <div className="form-error">{passwordError}</div>
                )}
                <div className="form-actions">
                  <button className="save-btn" onClick={onChangePassword}>{t('myPage.changePasswordBtn')}</button>
                  <button className="cancel-btn" onClick={onCancelPassword}>{t('myPage.cancel')}</button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 계정 삭제 */}
      <div className="info-card delete-account-card">
        <div className="delete-account-section">
          <div className="delete-account-info">
            <span className="delete-label">{t('myPage.deleteAccount')}</span>
            <span className="delete-description">{t('myPage.deleteAccountDesc')}</span>
          </div>
          <button className="delete-account-btn" onClick={onOpenDeleteModal}>
            {t('myPage.deleteAccount')}
          </button>
        </div>
      </div>

      {/* 계정 삭제 확인 모달 */}
      {isDeleteModalOpen && (
        <div className="delete-modal-overlay" onClick={() => { onCloseDeleteModal(); onDeleteModalErrorClear(); }}>
          <div className="delete-modal" onClick={(e) => e.stopPropagation()}>
            <div className="delete-modal-header">
              <svg className="warning-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 9V13M12 17H12.01M5.07183 19H18.9282C20.4678 19 21.4301 17.3333 20.6603 16L13.7321 4C12.9623 2.66667 11.0377 2.66667 10.2679 4L3.33975 16C2.56995 17.3333 3.53223 19 5.07183 19Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              <h3>계정 삭제</h3>
            </div>
            <div className="delete-modal-body">
              <p className="warning-text">
                정말로 계정을 삭제하시겠습니까?<br />
                이 작업은 되돌릴 수 없습니다.
              </p>
              <ul className="delete-warning-list">
                <li>모든 개인 정보가 삭제됩니다</li>
                <li>보유한 라이선스가 모두 비활성화됩니다</li>
                <li>결제 내역은 법적 보관 기간 동안 유지됩니다</li>
              </ul>
              <div className="confirm-input-group">
                <label>확인을 위해 <strong>'계정삭제'</strong>를 입력해주세요</label>
                <input
                  type="text"
                  value={deleteConfirmText}
                  onChange={(e) => {
                    onDeleteConfirmTextChange(e.target.value);
                    onDeleteModalErrorClear();
                  }}
                  placeholder="계정삭제"
                  className="confirm-input"
                />
              </div>
              {deleteModalError && (
                <p className="delete-modal-error">{deleteModalError}</p>
              )}
            </div>
            <div className="delete-modal-footer">
              <button className="cancel-btn" onClick={() => { onCloseDeleteModal(); onDeleteModalErrorClear(); }}>
                취소
              </button>
              <button
                className="confirm-delete-btn"
                onClick={onDeleteAccount}
                disabled={deleteConfirmText !== '계정삭제' || isDeleting}
              >
                {isDeleting ? '삭제 중...' : '계정 삭제'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default AccountPanel;
