export type PolicyType = 'terms' | 'privacy' | 'refund';
export type PolicyLang = 'ko' | 'en';

export interface PolicySection {
  title: string;
  bodies: string[]; // each entry rendered as <p> with innerHTML
}

export const POLICY_SECTIONS: Record<PolicyType, Record<PolicyLang, PolicySection[]>> = {
  terms: {
    ko: [
      {
        title: '제1조 (목적)',
        bodies: [
          '본 약관은 주식회사 메테오시뮬레이션(이하 "회사")이 제공하는 BULC 소프트웨어 및 관련 서비스(이하 "서비스")의 이용과 관련하여 회사와 이용자 간의 권리, 의무 및 책임사항, 기타 필요한 사항을 규정함을 목적으로 합니다.',
        ],
      },
      {
        title: '제2조 (정의)',
        bodies: [
          '1. "서비스"란 회사가 제공하는 화재 시뮬레이션 소프트웨어 BULC 및 이와 관련된 모든 서비스를 의미합니다.<br/>2. "이용자"란 본 약관에 따라 회사가 제공하는 서비스를 이용하는 회원 및 비회원을 말합니다.<br/>3. "회원"이란 회사에 개인정보를 제공하여 회원등록을 한 자로서, 회사의 정보를 지속적으로 제공받으며 서비스를 계속적으로 이용할 수 있는 자를 말합니다.<br/>4. "라이선스"란 서비스를 이용할 수 있는 권한을 의미합니다.',
        ],
      },
      {
        title: '제3조 (약관의 효력 및 변경)',
        bodies: [
          '1. 본 약관은 서비스를 이용하고자 하는 모든 이용자에게 적용됩니다.<br/>2. 회사는 필요하다고 인정되는 경우 본 약관을 변경할 수 있으며, 변경된 약관은 서비스 내 공지사항에 게시하거나 기타 방법으로 이용자에게 공지함으로써 효력이 발생합니다.<br/>3. 이용자가 변경된 약관에 동의하지 않는 경우 서비스 이용을 중단하고 이용계약을 해지할 수 있습니다.',
        ],
      },
      {
        title: '제4조 (서비스의 제공)',
        bodies: [
          '1. 회사는 다음과 같은 서비스를 제공합니다:<br/>&nbsp;&nbsp;- BULC 화재 시뮬레이션 소프트웨어<br/>&nbsp;&nbsp;- 소프트웨어 업데이트 및 기술 지원<br/>&nbsp;&nbsp;- 기타 회사가 추가 개발하거나 제휴 계약 등을 통해 이용자에게 제공하는 서비스<br/>2. 회사는 서비스의 품질 향상을 위해 서비스의 내용을 변경할 수 있습니다.',
        ],
      },
      {
        title: '제5조 (이용계약의 성립)',
        bodies: [
          '1. 이용계약은 이용자가 본 약관에 동의하고 회원가입을 완료한 후, 회사가 이를 승낙함으로써 성립합니다.<br/>2. 회사는 다음 각 호에 해당하는 경우 이용신청을 승낙하지 않을 수 있습니다:<br/>&nbsp;&nbsp;- 허위 정보를 기재한 경우<br/>&nbsp;&nbsp;- 타인의 명의를 도용한 경우<br/>&nbsp;&nbsp;- 기타 회사가 정한 이용신청 요건을 충족하지 못한 경우',
        ],
      },
      {
        title: '제6조 (회원의 의무)',
        bodies: [
          '1. 회원은 관계법령, 본 약관의 규정, 이용안내 및 서비스와 관련하여 공지한 주의사항을 준수하여야 합니다.<br/>2. 회원은 다음 행위를 하여서는 안 됩니다:<br/>&nbsp;&nbsp;- 타인의 정보 도용<br/>&nbsp;&nbsp;- 회사가 제공하는 서비스의 변경 또는 소프트웨어의 무단 복제, 배포<br/>&nbsp;&nbsp;- 회사 및 제3자의 저작권 등 지적재산권에 대한 침해<br/>&nbsp;&nbsp;- 회사 및 제3자의 명예를 손상시키거나 업무를 방해하는 행위<br/>&nbsp;&nbsp;- 기타 불법적이거나 부당한 행위',
        ],
      },
      {
        title: '제7조 (저작권의 귀속)',
        bodies: [
          '1. 서비스에 대한 저작권 및 지적재산권은 회사에 귀속됩니다.<br/>2. 이용자는 서비스를 이용함으로써 얻은 정보를 회사의 사전 승낙 없이 복제, 송신, 출판, 배포, 방송 기타 방법에 의하여 영리 목적으로 이용하거나 제3자에게 이용하게 하여서는 안 됩니다.',
        ],
      },
      {
        title: '제8조 (면책조항)',
        bodies: [
          '1. 회사는 천재지변 또는 이에 준하는 불가항력으로 인하여 서비스를 제공할 수 없는 경우에는 서비스 제공에 관한 책임이 면제됩니다.<br/>2. 회사는 이용자의 귀책사유로 인한 서비스 이용의 장애에 대하여 책임을 지지 않습니다.<br/>3. 회사는 이용자가 서비스를 이용하여 기대하는 수익을 상실한 것에 대하여 책임을 지지 않습니다.',
        ],
      },
      {
        title: '제9조 (분쟁해결)',
        bodies: [
          '1. 회사와 이용자 간에 발생한 분쟁에 관한 소송은 대한민국 법을 적용합니다.<br/>2. 회사와 이용자 간에 발생한 분쟁에 관한 소송은 회사의 본사 소재지를 관할하는 법원을 관할법원으로 합니다.',
        ],
      },
      {
        title: '부칙',
        bodies: ['본 약관은 2024년 1월 1일부터 시행합니다.'],
      },
    ],
    en: [
      {
        title: 'Article 1 (Purpose)',
        bodies: [
          'These Terms set forth the rights, obligations, and responsibilities of Meteor Simulation Co., Ltd. (the "Company") and users in connection with the use of the BULC software and related services (the "Service") provided by the Company, as well as other necessary matters.',
        ],
      },
      {
        title: 'Article 2 (Definitions)',
        bodies: [
          '1. "Service" means the BULC fire simulation software and all related services provided by the Company.<br/>2. "User" means a member or non-member who uses the Service provided by the Company under these Terms.<br/>3. "Member" means a person who has provided personal information to the Company and completed registration, and who may continuously receive information from the Company and use the Service.<br/>4. "License" means the right to use the Service.',
        ],
      },
      {
        title: 'Article 3 (Effect and Modification of Terms)',
        bodies: [
          '1. These Terms apply to all users who wish to use the Service.<br/>2. The Company may modify these Terms when deemed necessary, and the modified Terms take effect upon being posted in the Service notices or notified to users by other means.<br/>3. If a user does not agree to the modified Terms, the user may suspend use of the Service and terminate the service agreement.',
        ],
      },
      {
        title: 'Article 4 (Service Provision)',
        bodies: [
          '1. The Company provides the following services:<br/>&nbsp;&nbsp;- BULC fire simulation software<br/>&nbsp;&nbsp;- Software updates and technical support<br/>&nbsp;&nbsp;- Other services provided to users through additional development or partnerships<br/>2. The Company may modify the content of the Service to improve quality.',
        ],
      },
      {
        title: 'Article 5 (Establishment of Service Agreement)',
        bodies: [
          '1. The service agreement is formed when the user agrees to these Terms, completes registration, and the Company accepts the application.<br/>2. The Company may decline an application for use in any of the following cases:<br/>&nbsp;&nbsp;- If false information is provided<br/>&nbsp;&nbsp;- If another person\'s identity is used without authorization<br/>&nbsp;&nbsp;- If the application does not meet other requirements established by the Company',
        ],
      },
      {
        title: 'Article 6 (Obligations of Members)',
        bodies: [
          '1. Members must comply with applicable laws, the provisions of these Terms, service guides, and any notices related to the Service.<br/>2. Members must not engage in any of the following acts:<br/>&nbsp;&nbsp;- Misusing another person\'s information<br/>&nbsp;&nbsp;- Unauthorized modification, copying, or distribution of the Service or software<br/>&nbsp;&nbsp;- Infringement of the Company\'s or third parties\' intellectual property rights<br/>&nbsp;&nbsp;- Damaging the reputation of the Company or third parties, or interfering with their business<br/>&nbsp;&nbsp;- Any other illegal or improper acts',
        ],
      },
      {
        title: 'Article 7 (Copyright)',
        bodies: [
          '1. Copyrights and intellectual property rights in the Service belong to the Company.<br/>2. Users must not, without the Company\'s prior consent, use information obtained through the Service for commercial purposes by reproducing, transmitting, publishing, distributing, broadcasting, or otherwise enabling third parties to use it.',
        ],
      },
      {
        title: 'Article 8 (Disclaimer)',
        bodies: [
          '1. The Company is exempt from liability for failure to provide the Service due to force majeure events such as natural disasters.<br/>2. The Company is not liable for service disruptions caused by reasons attributable to the user.<br/>3. The Company is not liable for any loss of profits the user expected to obtain by using the Service.',
        ],
      },
      {
        title: 'Article 9 (Dispute Resolution)',
        bodies: [
          '1. Disputes between the Company and users shall be governed by the laws of the Republic of Korea.<br/>2. Lawsuits related to such disputes shall be filed in the court having jurisdiction over the Company\'s head office.',
        ],
      },
      {
        title: 'Supplementary Provisions',
        bodies: ['These Terms take effect on January 1, 2024.'],
      },
    ],
  },
  privacy: {
    ko: [
      {
        title: '1. 개인정보의 수집 및 이용 목적',
        bodies: [
          '주식회사 메테오시뮬레이션(이하 "회사")은 다음의 목적을 위하여 개인정보를 수집 및 이용합니다. 수집한 개인정보는 다음의 목적 이외의 용도로는 사용되지 않으며, 이용 목적이 변경될 시에는 사전동의를 구할 예정입니다.',
          '가. 서비스 제공<br/>&nbsp;&nbsp;- 콘텐츠 제공, 본인인증, 서비스 이용 및 결제<br/><br/>나. 회원 관리<br/>&nbsp;&nbsp;- 회원제 서비스 이용에 따른 본인확인, 개인식별, 불량회원의 부정 이용 방지와 비인가 사용 방지, 가입 의사 확인, 분쟁 조정을 위한 기록보존, 불만처리 등 민원처리, 고지사항 전달<br/><br/>다. 마케팅 및 광고에 활용 (선택 동의 시)<br/>&nbsp;&nbsp;- 신규 서비스 개발 및 맞춤 서비스 제공, 이벤트 및 광고성 정보 제공',
        ],
      },
      {
        title: '2. 수집하는 개인정보의 항목',
        bodies: [
          '회사는 회원가입, 서비스 이용 등을 위해 아래와 같은 개인정보를 수집하고 있습니다.',
          '가. 필수항목<br/>&nbsp;&nbsp;- 이메일 주소, 비밀번호, 이름<br/><br/>나. 선택항목<br/>&nbsp;&nbsp;- 전화번호, 회사명, 부서, 직책<br/><br/>다. 서비스 이용 과정에서 자동으로 수집되는 정보<br/>&nbsp;&nbsp;- IP주소, 쿠키, 방문일시, 서비스 이용기록, 기기정보(OS, 브라우저 등)',
        ],
      },
      {
        title: '3. 개인정보의 보유 및 이용기간',
        bodies: [
          '회사는 법령에 따른 개인정보 보유·이용기간 또는 정보주체로부터 개인정보 수집 시에 동의 받은 개인정보 보유·이용기간 내에서 개인정보를 처리·보유합니다.',
          '가. 회원 정보: 회원 탈퇴 시까지 (단, 관계 법령에 의해 보존할 필요가 있는 경우 해당 기간까지 보존)<br/><br/>나. 전자상거래에서의 계약 또는 청약철회 등에 관한 기록: 5년<br/><br/>다. 대금결제 및 재화 등의 공급에 관한 기록: 5년<br/><br/>라. 소비자의 불만 또는 분쟁처리에 관한 기록: 3년<br/><br/>마. 접속에 관한 기록: 3개월',
        ],
      },
      {
        title: '4. 개인정보의 제3자 제공',
        bodies: [
          '회사는 이용자의 개인정보를 원칙적으로 외부에 제공하지 않습니다. 다만, 아래의 경우에는 예외로 합니다.',
          '가. 이용자들이 사전에 동의한 경우<br/>나. 법령의 규정에 의거하거나, 수사 목적으로 법령에 정해진 절차와 방법에 따라 수사기관의 요구가 있는 경우',
        ],
      },
      {
        title: '5. 개인정보처리의 위탁',
        bodies: [
          '회사는 원활한 개인정보 업무처리를 위하여 다음과 같이 개인정보 처리업무를 위탁하고 있습니다.',
          '가. 결제처리<br/>&nbsp;&nbsp;- 위탁받는 자: 토스페이먼츠<br/>&nbsp;&nbsp;- 위탁하는 업무의 내용: 결제 처리 및 결제 정보 관리<br/><br/>나. 이메일 발송<br/>&nbsp;&nbsp;- 위탁받는 자: Google (Gmail SMTP)<br/>&nbsp;&nbsp;- 위탁하는 업무의 내용: 이메일 인증 및 알림 발송',
        ],
      },
      {
        title: '6. 정보주체의 권리·의무 및 행사방법',
        bodies: [
          '이용자는 개인정보주체로서 다음과 같은 권리를 행사할 수 있습니다.',
          '가. 개인정보 열람 요구<br/>나. 오류 등이 있을 경우 정정 요구<br/>다. 삭제 요구<br/>라. 처리정지 요구',
        ],
      },
      {
        title: '7. 개인정보의 파기',
        bodies: [
          '회사는 원칙적으로 개인정보 처리목적이 달성된 경우에는 지체없이 해당 개인정보를 파기합니다. 파기의 절차, 기한 및 방법은 다음과 같습니다.',
          '가. 파기절차: 이용자가 입력한 정보는 목적 달성 후 별도의 DB에 옮겨져 내부 방침 및 기타 관련 법령에 따라 일정기간 저장된 후 혹은 즉시 파기됩니다.<br/><br/>나. 파기방법: 전자적 파일 형태의 정보는 기록을 재생할 수 없는 기술적 방법을 사용합니다.',
        ],
      },
      {
        title: '8. 개인정보의 안전성 확보 조치',
        bodies: [
          '회사는 개인정보의 안전성 확보를 위해 다음과 같은 조치를 취하고 있습니다.',
          '가. 관리적 조치: 내부관리계획 수립·시행, 정기적 직원 교육<br/>나. 기술적 조치: 개인정보처리시스템 등의 접근권한 관리, 접근통제시스템 설치, 고유식별정보 등의 암호화, 보안프로그램 설치<br/>다. 물리적 조치: 전산실, 자료보관실 등의 접근통제',
        ],
      },
      {
        title: '9. 개인정보 보호책임자',
        bodies: [
          '회사는 개인정보 처리에 관한 업무를 총괄해서 책임지고, 개인정보 처리와 관련한 정보주체의 불만처리 및 피해구제 등을 위하여 아래와 같이 개인정보 보호책임자를 지정하고 있습니다.',
          '▶ 개인정보 보호책임자<br/>&nbsp;&nbsp;- 성명: 관리자<br/>&nbsp;&nbsp;- 연락처: support@meteor-simulation.com',
        ],
      },
      {
        title: '10. 개인정보처리방침 변경',
        bodies: [
          '이 개인정보처리방침은 2024년 1월 1일부터 적용되며, 법령 및 방침에 따른 변경내용의 추가, 삭제 및 정정이 있는 경우에는 변경사항의 시행 7일 전부터 공지사항을 통하여 고지할 것입니다.',
        ],
      },
    ],
    en: [
      {
        title: '1. Purpose of Collection and Use of Personal Information',
        bodies: [
          'Meteor Simulation Co., Ltd. (the "Company") collects and uses personal information for the following purposes. Collected personal information will not be used for any purpose other than those listed below, and prior consent will be obtained if the purpose of use changes.',
          'a. Service provision<br/>&nbsp;&nbsp;- Content delivery, identity verification, service use, and payment<br/><br/>b. Member management<br/>&nbsp;&nbsp;- Identity verification, individual identification, prevention of fraudulent or unauthorized use, confirmation of intent to register, record retention for dispute resolution, complaint handling, and delivery of notices<br/><br/>c. Marketing and advertising use (with optional consent)<br/>&nbsp;&nbsp;- Development of new services, personalized service offerings, and provision of event and promotional information',
        ],
      },
      {
        title: '2. Items of Personal Information Collected',
        bodies: [
          'The Company collects the following personal information for sign-up and service use.',
          'a. Required items<br/>&nbsp;&nbsp;- Email address, password, name<br/><br/>b. Optional items<br/>&nbsp;&nbsp;- Phone number, company name, department, position<br/><br/>c. Information automatically collected during service use<br/>&nbsp;&nbsp;- IP address, cookies, visit time, service usage history, device information (OS, browser, etc.)',
        ],
      },
      {
        title: '3. Retention and Use Period of Personal Information',
        bodies: [
          'The Company processes and retains personal information within the retention period required by law or the period consented to at the time of collection.',
          'a. Member information: Until membership is terminated (if retention is required by law, until the relevant period ends)<br/><br/>b. Records of contracts or withdrawal of orders in e-commerce: 5 years<br/><br/>c. Records of payment and supply of goods: 5 years<br/><br/>d. Records of consumer complaints or dispute resolution: 3 years<br/><br/>e. Access logs: 3 months',
        ],
      },
      {
        title: '4. Provision of Personal Information to Third Parties',
        bodies: [
          'As a rule, the Company does not provide users\' personal information to external parties. However, the following are exceptions.',
          'a. When users have consented in advance<br/>b. When required by law, or when requested by investigative authorities in accordance with procedures and methods prescribed by law for investigative purposes',
        ],
      },
      {
        title: '5. Outsourcing of Personal Information Processing',
        bodies: [
          'The Company outsources personal information processing tasks as follows for the smooth handling of personal information.',
          'a. Payment processing<br/>&nbsp;&nbsp;- Trustee: Toss Payments<br/>&nbsp;&nbsp;- Scope of outsourced work: Payment processing and payment information management<br/><br/>b. Email delivery<br/>&nbsp;&nbsp;- Trustee: Google (Gmail SMTP)<br/>&nbsp;&nbsp;- Scope of outsourced work: Email verification and notification delivery',
        ],
      },
      {
        title: '6. Rights and Obligations of Data Subjects and How to Exercise Them',
        bodies: [
          'As a data subject, the user may exercise the following rights.',
          'a. Request to access personal information<br/>b. Request to correct errors<br/>c. Request to delete<br/>d. Request to suspend processing',
        ],
      },
      {
        title: '7. Destruction of Personal Information',
        bodies: [
          'As a rule, the Company destroys personal information without delay once the purpose of processing has been achieved. The procedure, timing, and method of destruction are as follows.',
          'a. Destruction procedure: Information entered by the user is moved to a separate database after the purpose is achieved, retained for a period in accordance with internal policies and relevant laws, and then destroyed.<br/><br/>b. Destruction method: Electronic file information is destroyed using technical methods that prevent recovery.',
        ],
      },
      {
        title: '8. Measures to Ensure the Security of Personal Information',
        bodies: [
          'The Company takes the following measures to ensure the security of personal information.',
          'a. Administrative measures: Establishment and implementation of internal management plans, regular employee training<br/>b. Technical measures: Access control for personal information processing systems, installation of access control systems, encryption of unique identifying information, installation of security programs<br/>c. Physical measures: Access control for computer rooms and storage facilities',
        ],
      },
      {
        title: '9. Privacy Officer',
        bodies: [
          'The Company designates the following Privacy Officer to take overall responsibility for personal information processing and to handle complaints and damage relief related to personal information processing.',
          '▶ Privacy Officer<br/>&nbsp;&nbsp;- Name: Administrator<br/>&nbsp;&nbsp;- Contact: support@meteor-simulation.com',
        ],
      },
      {
        title: '10. Changes to This Privacy Policy',
        bodies: [
          'This Privacy Policy takes effect on January 1, 2024. If changes are made due to laws or policies — including additions, deletions, or corrections — the Company will provide notice through announcements at least 7 days before the changes take effect.',
        ],
      },
    ],
  },
  refund: {
    ko: [
      {
        title: '제1조 (목적)',
        bodies: [
          '본 환불정책은 주식회사 메테오시뮬레이션(이하 "회사")이 제공하는 소프트웨어 제품 및 서비스(이하 "서비스")의 결제 취소, 환불 및 구독 해지에 관한 사항을 규정함을 목적으로 합니다.',
        ],
      },
      {
        title: '제2조 (환불 가능 조건)',
        bodies: [
          '다음 각 호에 해당하는 경우 전액 환불이 가능합니다.',
          '1. 결제일로부터 7일 이내에 환불을 요청한 경우<br/>2. 구매한 라이선스를 활성화하지 않은 경우<br/>3. 서비스의 중대한 하자로 인해 정상적인 이용이 불가능한 경우<br/>4. 회사의 귀책사유로 서비스가 제공되지 못한 경우',
        ],
      },
      {
        title: '제3조 (환불 불가 조건)',
        bodies: [
          '다음 각 호에 해당하는 경우 환불이 불가합니다.',
          '1. 라이선스를 활성화하였거나 서비스를 이용한 이력이 있는 경우<br/>2. 결제일로부터 7일이 경과한 경우<br/>3. 무료 체험(Trial) 기간 중 또는 무료 체험 후 전환된 유료 결제의 경우<br/>4. 이용약관에서 정한 환불 제한 사유에 해당하는 경우<br/>5. 이용자의 단순 변심에 의한 경우 (라이선스 활성화 후)',
        ],
      },
      {
        title: '제4조 (환불 절차)',
        bodies: [
          '환불을 요청하시는 경우 다음의 절차에 따라 처리됩니다.',
          '1. <strong>환불 신청:</strong> 회사 고객지원 이메일(support@msimul.com)로 환불 사유, 결제 정보(주문번호, 결제일, 결제 수단)를 기재하여 신청합니다.<br/><br/>2. <strong>신청 접수 및 검토:</strong> 회사는 접수된 환불 요청을 확인하고, 환불 가능 여부를 검토합니다.<br/><br/>3. <strong>환불 처리:</strong> 검토 완료 후, 환불 승인 시 원래 결제 수단으로 환불이 진행됩니다.',
        ],
      },
      {
        title: '제5조 (환불 처리 기간)',
        bodies: [
          '환불은 접수일로부터 영업일 기준 5~7일 이내에 처리됩니다. 다만, 결제 수단에 따라 환불 금액의 실제 반영 시점은 상이할 수 있습니다.',
          '- 신용카드: 카드사에 따라 취소 반영까지 3~7 영업일 소요<br/>- 계좌이체: 환불 승인 후 1~3 영업일 이내 입금<br/>- 기타 결제 수단: 해당 결제 서비스 제공자의 정책에 따름',
        ],
      },
      {
        title: '제6조 (구독 취소)',
        bodies: [
          '구독형 서비스를 이용 중인 경우, 구독 취소 시 다음과 같이 처리됩니다.',
          '1. 구독 취소를 요청하면 다음 결제 예정일부터 자동갱신이 중지됩니다.<br/>2. 이미 결제된 현재 구독 기간의 남은 기간 동안은 서비스를 계속 이용할 수 있습니다.<br/>3. 구독 기간 중 중도 취소에 따른 일할 환불은 제공되지 않습니다.',
        ],
      },
      {
        title: '제7조 (무료 체험)',
        bodies: [
          '무료 체험(Trial) 서비스는 무상으로 제공되는 것으로, 환불 대상에 해당하지 않습니다. 무료 체험 기간 종료 후 유료 전환 시에는 본 환불정책이 적용됩니다.',
        ],
      },
      {
        title: '제8조 (정책 변경)',
        bodies: [
          '회사는 관련 법령의 변경 또는 서비스 운영상의 필요에 따라 본 환불정책을 변경할 수 있습니다. 변경 시에는 시행일 7일 전에 홈페이지를 통해 공지하며, 이용자에게 불리한 변경의 경우 30일 전에 공지합니다.',
        ],
      },
      {
        title: '부칙',
        bodies: ['본 환불정책은 2025년 1월 1일부터 시행합니다.'],
      },
    ],
    en: [
      {
        title: 'Article 1 (Purpose)',
        bodies: [
          'This Refund Policy sets forth matters relating to payment cancellation, refunds, and subscription termination for the software products and services (the "Service") provided by Meteor Simulation Co., Ltd. (the "Company").',
        ],
      },
      {
        title: 'Article 2 (Refund-eligible Conditions)',
        bodies: [
          'A full refund is available in any of the following cases.',
          '1. A refund is requested within 7 days from the date of payment<br/>2. The purchased license has not been activated<br/>3. Normal use is impossible due to a material defect in the Service<br/>4. The Service was not provided due to reasons attributable to the Company',
        ],
      },
      {
        title: 'Article 3 (Refund-ineligible Conditions)',
        bodies: [
          'Refunds are not available in any of the following cases.',
          '1. The license has been activated or there is a history of using the Service<br/>2. More than 7 days have passed since the date of payment<br/>3. During a free trial, or for a paid payment converted from a free trial<br/>4. The case falls under refund restrictions specified in the Terms of Service<br/>5. The user simply changes their mind (after license activation)',
        ],
      },
      {
        title: 'Article 4 (Refund Procedure)',
        bodies: [
          'If you request a refund, it will be processed according to the following procedure.',
          '1. <strong>Refund request:</strong> Submit your request to the Company\'s customer support email (support@msimul.com) with the reason for refund and payment information (order number, payment date, payment method).<br/><br/>2. <strong>Receipt and review:</strong> The Company verifies the received refund request and reviews whether a refund is possible.<br/><br/>3. <strong>Refund processing:</strong> After review, if approved, the refund is processed to the original payment method.',
        ],
      },
      {
        title: 'Article 5 (Refund Processing Period)',
        bodies: [
          'Refunds are processed within 5–7 business days from the date of receipt. However, the actual reflection time of the refund amount may vary depending on the payment method.',
          '- Credit card: 3–7 business days for the cancellation to be reflected, depending on the card issuer<br/>- Bank transfer: Deposited within 1–3 business days after refund approval<br/>- Other payment methods: According to the policies of the respective payment service provider',
        ],
      },
      {
        title: 'Article 6 (Subscription Cancellation)',
        bodies: [
          'If you are using a subscription service, cancellation is processed as follows.',
          '1. When you request cancellation, auto-renewal will be stopped from the next scheduled payment date.<br/>2. You may continue to use the Service for the remaining period of the current subscription that has already been paid for.<br/>3. Pro-rated refunds for early cancellation during the subscription period are not provided.',
        ],
      },
      {
        title: 'Article 7 (Free Trial)',
        bodies: [
          'Free Trial services are provided at no cost and are not eligible for refunds. This Refund Policy applies when converting to a paid plan after the free trial ends.',
        ],
      },
      {
        title: 'Article 8 (Policy Changes)',
        bodies: [
          'The Company may modify this Refund Policy in accordance with changes in relevant laws or operational needs. Changes will be announced on the website at least 7 days before the effective date, or at least 30 days in advance if the change is unfavorable to users.',
        ],
      },
      {
        title: 'Supplementary Provisions',
        bodies: ['This Refund Policy takes effect on January 1, 2025.'],
      },
    ],
  },
};
