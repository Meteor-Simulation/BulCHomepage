/**
 * 전화번호 유틸리티 함수
 * - 표시: 010-2366-6455 형식
 * - 저장: 01023666455 (숫자만)
 */

/**
 * 전화번호에서 숫자만 추출 (DB 저장용)
 * @param phone 입력된 전화번호 (예: "010-2366-6455" 또는 "01023666455")
 * @returns 숫자만 포함된 문자열 (예: "01023666455")
 */
export const cleanPhoneNumber = (phone: string | null | undefined): string => {
  if (!phone) return '';
  return phone.replace(/[^0-9]/g, '');
};

/**
 * 전화번호 유효성 검사
 * 전화번호는 선택 항목이므로 미입력(빈 값)은 유효로 처리하고,
 * 값이 있을 때만 숫자 10~11자리(한국 휴대폰/유선)를 요구한다.
 * 과거 "010"처럼 앞자리만 입력된 불완전 값을 막기 위함. (백엔드 @ValidPhone과 일치)
 * @param phone 입력된 전화번호 (하이픈 포함 가능)
 * @returns 유효하면 true
 */
export const isValidPhone = (phone: string | null | undefined): boolean => {
  const cleaned = cleanPhoneNumber(phone);
  if (!cleaned) return true; // 미입력 허용
  return /^\d{10,11}$/.test(cleaned);
};

/**
 * 전화번호를 표시 형식으로 변환 (UI 표시용)
 * @param phone 전화번호 (숫자만 또는 하이픈 포함)
 * @returns 포맷된 전화번호 (예: "010-2366-6455")
 */
export const formatPhoneNumber = (phone: string | null | undefined): string => {
  if (!phone) return '';

  // 먼저 숫자만 추출
  const cleaned = cleanPhoneNumber(phone);

  // 빈 값이면 반환
  if (!cleaned) return '';

  // 한국 휴대폰 번호 형식 (010-XXXX-XXXX)
  if (cleaned.length === 11 && cleaned.startsWith('01')) {
    return `${cleaned.slice(0, 3)}-${cleaned.slice(3, 7)}-${cleaned.slice(7)}`;
  }

  // 한국 일반 전화 형식 (02-XXXX-XXXX 또는 0XX-XXX-XXXX)
  if (cleaned.length === 10) {
    if (cleaned.startsWith('02')) {
      // 서울 지역번호 (02-XXXX-XXXX)
      return `${cleaned.slice(0, 2)}-${cleaned.slice(2, 6)}-${cleaned.slice(6)}`;
    } else {
      // 기타 지역번호 (0XX-XXX-XXXX)
      return `${cleaned.slice(0, 3)}-${cleaned.slice(3, 6)}-${cleaned.slice(6)}`;
    }
  }

  // 한국 일반 전화 형식 (02-XXX-XXXX)
  if (cleaned.length === 9 && cleaned.startsWith('02')) {
    return `${cleaned.slice(0, 2)}-${cleaned.slice(2, 5)}-${cleaned.slice(5)}`;
  }

  // 그 외의 경우 원래 값 반환
  return phone;
};

/**
 * 전화번호 입력 시 자동 포맷팅 (실시간 입력용)
 * @param phone 입력 중인 전화번호
 * @returns 포맷된 전화번호
 */
export const formatPhoneNumberOnInput = (phone: string): string => {
  const cleaned = cleanPhoneNumber(phone);

  if (!cleaned) return '';

  // 휴대폰 번호 (01X)
  if (cleaned.startsWith('01')) {
    if (cleaned.length <= 3) {
      return cleaned;
    } else if (cleaned.length <= 7) {
      return `${cleaned.slice(0, 3)}-${cleaned.slice(3)}`;
    } else {
      return `${cleaned.slice(0, 3)}-${cleaned.slice(3, 7)}-${cleaned.slice(7, 11)}`;
    }
  }

  // 서울 지역번호 (02)
  if (cleaned.startsWith('02')) {
    if (cleaned.length <= 2) {
      return cleaned;
    } else if (cleaned.length <= 6) {
      return `${cleaned.slice(0, 2)}-${cleaned.slice(2)}`;
    } else {
      return `${cleaned.slice(0, 2)}-${cleaned.slice(2, 6)}-${cleaned.slice(6, 10)}`;
    }
  }

  // 기타 지역번호 (0XX)
  if (cleaned.startsWith('0')) {
    if (cleaned.length <= 3) {
      return cleaned;
    } else if (cleaned.length <= 6) {
      return `${cleaned.slice(0, 3)}-${cleaned.slice(3)}`;
    } else {
      return `${cleaned.slice(0, 3)}-${cleaned.slice(3, 6)}-${cleaned.slice(6, 10)}`;
    }
  }

  return cleaned;
};
