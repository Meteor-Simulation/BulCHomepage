import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/AuthContext';
import { API_URL } from '../utils/api';
import './EducationContactModal.css';

interface EducationContactModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const COUNTRY_OPTIONS_KO = ['대한민국', '미국', '기타'];
const COUNTRY_OPTIONS_EN = ['South Korea', 'United States', 'Other'];

const EducationContactModal: React.FC<EducationContactModalProps> = ({ isOpen, onClose }) => {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [organization, setOrganization] = useState('');
  const [department, setDepartment] = useState('');
  const [role, setRole] = useState('');
  const [country, setCountry] = useState('');
  const [purpose, setPurpose] = useState('');
  const [courseProject, setCourseProject] = useState('');
  const [seatCount, setSeatCount] = useState('');
  const [duration, setDuration] = useState('');
  const [durationCustom, setDurationCustom] = useState('');
  const [startDate, setStartDate] = useState('');
  const [message, setMessage] = useState('');

  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const isKo = i18n.language === 'ko';
  const countryOptions = isKo ? COUNTRY_OPTIONS_KO : COUNTRY_OPTIONS_EN;
  const roleOptions = t('bulc.price.educationContact.roleOptions', { returnObjects: true }) as string[];
  const purposeOptions = t('bulc.price.educationContact.purposeOptions', { returnObjects: true }) as string[];
  const durationOptions = t('bulc.price.educationContact.durationOptions', { returnObjects: true }) as string[];

  useEffect(() => {
    if (isOpen) {
      setName('');
      setEmail(user?.email || '');
      setOrganization('');
      setDepartment('');
      setRole('');
      setCountry('');
      setPurpose('');
      setCourseProject('');
      setSeatCount('');
      setDuration('');
      setDurationCustom('');
      setStartDate('');
      setMessage('');
      setError('');
      setSuccess(false);
      setIsLoading(false);
    }
  }, [isOpen, user]);

  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) {
      document.addEventListener('keydown', handleEsc);
      document.body.style.overflow = 'hidden';
    }
    return () => {
      document.removeEventListener('keydown', handleEsc);
      document.body.style.overflow = 'unset';
    };
  }, [isOpen, onClose]);

  const handleOverlayMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!name.trim() || !email.trim() || !organization.trim() || !role || !purpose) {
      setError(t('bulc.price.educationContact.required'));
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError(t('bulc.price.educationContact.invalidEmail'));
      return;
    }

    setIsLoading(true);

    const labelOrg = t('bulc.price.educationContact.organization');
    const labelDept = t('bulc.price.educationContact.department');
    const labelRole = t('bulc.price.educationContact.role');
    const labelCountry = t('bulc.price.educationContact.country');
    const labelPurpose = t('bulc.price.educationContact.purpose');
    const labelCourse = t('bulc.price.educationContact.courseProject');
    const labelSeat = t('bulc.price.educationContact.seatCount');
    const labelDuration = t('bulc.price.educationContact.duration');
    const labelStart = t('bulc.price.educationContact.startDate');

    const lastDurationOption = durationOptions[durationOptions.length - 1];
    const durationValue = duration === lastDurationOption ? durationCustom : duration;

    const bodyLines = [
      `${labelOrg}: ${organization}`,
      department ? `${labelDept}: ${department}` : '',
      `${labelRole}: ${role}`,
      country ? `${labelCountry}: ${country}` : '',
      `${labelPurpose}: ${purpose}`,
      courseProject ? `${labelCourse}: ${courseProject}` : '',
      seatCount ? `${labelSeat}: ${seatCount}${isKo ? '명' : ' seats'}` : '',
      durationValue ? `${labelDuration}: ${durationValue}` : '',
      startDate ? `${labelStart}: ${startDate}` : '',
      '',
      message || '',
    ].filter((line, idx) => line !== '' || idx >= 8).join('\n');

    const subject = isKo
      ? `[교육/연구 문의] ${organization} - ${name}`
      : `[Education Inquiry] ${organization} - ${name}`;

    try {
      const response = await fetch(`${API_URL}/api/contact`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email,
          subject,
          message: bodyLines,
          category: 'EDUCATION',
        }),
      });

      const result = await response.json();

      if (result.success) {
        setSuccess(true);
      } else {
        setError(result.message || 'Failed to send inquiry.');
      }
    } catch {
      setError('Failed to send inquiry.');
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onMouseDown={handleOverlayMouseDown}>
      <div className="modal-content education-contact-modal">
        <button className="modal-close-btn" onClick={onClose}>
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>

        <h2 className="modal-title">{t('bulc.price.educationContact.title')}</h2>

        {success ? (
          <div className="education-success">
            <svg className="success-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <polyline points="22,4 12,14.01 9,11.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            <p className="success-message">{t('bulc.price.educationContact.success')}</p>
            <p className="success-sub">{t('bulc.price.educationContact.successSub')}</p>
            <button className="modal-submit-btn" onClick={onClose}>
              {t('bulc.price.educationContact.close')}
            </button>
          </div>
        ) : (
          <>
            <p className="education-contact-subtitle">{t('bulc.price.educationContact.subtitle')}</p>
            <form className="education-contact-form" onSubmit={handleSubmit}>
              {/* Name */}
              <div className="education-field">
                <label>{t('bulc.price.educationContact.name')} <span className="required">*</span></label>
                <input
                  type="text"
                  className="modal-input"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder={t('bulc.price.educationContact.namePlaceholder')}
                  disabled={isLoading}
                />
              </div>

              {/* Email */}
              <div className="education-field">
                <label>{t('bulc.price.educationContact.email')} <span className="required">*</span></label>
                <input
                  type="email"
                  className={`modal-input ${user ? 'input-readonly' : ''}`}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder={t('bulc.price.educationContact.emailPlaceholder')}
                  disabled={isLoading}
                  readOnly={!!user}
                />
              </div>

              {/* Organization */}
              <div className="education-field">
                <label>{t('bulc.price.educationContact.organization')} <span className="required">*</span></label>
                <input
                  type="text"
                  className="modal-input"
                  value={organization}
                  onChange={(e) => setOrganization(e.target.value)}
                  placeholder={t('bulc.price.educationContact.organizationPlaceholder')}
                  disabled={isLoading}
                />
              </div>

              {/* Department */}
              <div className="education-field">
                <label>{t('bulc.price.educationContact.department')}</label>
                <input
                  type="text"
                  className="modal-input"
                  value={department}
                  onChange={(e) => setDepartment(e.target.value)}
                  placeholder={t('bulc.price.educationContact.departmentPlaceholder')}
                  disabled={isLoading}
                />
              </div>

              {/* Role */}
              <div className="education-field">
                <label>{t('bulc.price.educationContact.role')} <span className="required">*</span></label>
                <select
                  className="modal-input"
                  value={role}
                  onChange={(e) => setRole(e.target.value)}
                  disabled={isLoading}
                >
                  <option value="">—</option>
                  {roleOptions.map((opt, i) => (
                    <option key={i} value={opt}>{opt}</option>
                  ))}
                </select>
              </div>

              {/* Country */}
              <div className="education-field">
                <label>{t('bulc.price.educationContact.country')}</label>
                <select
                  className="modal-input"
                  value={country}
                  onChange={(e) => setCountry(e.target.value)}
                  disabled={isLoading}
                >
                  <option value="">—</option>
                  {countryOptions.map((opt, i) => (
                    <option key={i} value={opt}>{opt}</option>
                  ))}
                </select>
              </div>

              {/* Purpose */}
              <div className="education-field">
                <label>{t('bulc.price.educationContact.purpose')} <span className="required">*</span></label>
                <select
                  className="modal-input"
                  value={purpose}
                  onChange={(e) => setPurpose(e.target.value)}
                  disabled={isLoading}
                >
                  <option value="">—</option>
                  {purposeOptions.map((opt, i) => (
                    <option key={i} value={opt}>{opt}</option>
                  ))}
                </select>
              </div>

              {/* Course / Project */}
              <div className="education-field">
                <label>{t('bulc.price.educationContact.courseProject')}</label>
                <input
                  type="text"
                  className="modal-input"
                  value={courseProject}
                  onChange={(e) => setCourseProject(e.target.value)}
                  placeholder={t('bulc.price.educationContact.courseProjectPlaceholder')}
                  disabled={isLoading}
                />
              </div>

              {/* Seat Count */}
              <div className="education-field">
                <label>{t('bulc.price.educationContact.seatCount')}</label>
                <input
                  type="number"
                  className="modal-input"
                  value={seatCount}
                  onChange={(e) => setSeatCount(e.target.value)}
                  placeholder={t('bulc.price.educationContact.seatCountPlaceholder')}
                  disabled={isLoading}
                  min="1"
                />
              </div>

              {/* Duration */}
              <div className="education-field">
                <label>{t('bulc.price.educationContact.duration')}</label>
                <select
                  className="modal-input"
                  value={duration}
                  onChange={(e) => setDuration(e.target.value)}
                  disabled={isLoading}
                >
                  <option value="">—</option>
                  {durationOptions.map((opt, i) => (
                    <option key={i} value={opt}>{opt}</option>
                  ))}
                </select>
                {duration === durationOptions[durationOptions.length - 1] && (
                  <input
                    type="text"
                    className="modal-input"
                    value={durationCustom}
                    onChange={(e) => setDurationCustom(e.target.value)}
                    placeholder={t('bulc.price.educationContact.durationCustomPlaceholder')}
                    disabled={isLoading}
                    style={{ marginTop: 6 }}
                  />
                )}
              </div>

              {/* Start Date */}
              <div className="education-field">
                <label>{t('bulc.price.educationContact.startDate')}</label>
                <input
                  type="date"
                  className="modal-input"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  disabled={isLoading}
                />
              </div>

              {/* Message */}
              <div className="education-field education-field--full">
                <label>{t('bulc.price.educationContact.message')}</label>
                <textarea
                  className="modal-input"
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder={t('bulc.price.educationContact.messagePlaceholder')}
                  disabled={isLoading}
                  rows={3}
                />
              </div>

              {error && <p className="modal-error education-form-error">{error}</p>}

              <button type="submit" className="modal-submit-btn education-form-submit" disabled={isLoading}>
                {isLoading
                  ? t('bulc.price.educationContact.submitting')
                  : t('bulc.price.educationContact.submit')}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
};

export default EducationContactModal;
