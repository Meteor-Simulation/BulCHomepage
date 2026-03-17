import React from 'react';
import '../Common/CategoryPages.css';
import './RefundPolicy.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';

const RefundPolicyPage: React.FC = () => {
  return (
    <div className="app">
      <Header />

      <main className="main-content sub-page">
        <div className="refund-policy-container">
          <h1>환불정책</h1>
          <p className="policy-updated">시행일: 2025년 1월 1일</p>

          <section>
            <h3>제1조 (목적)</h3>
            <p>
              본 환불정책은 주식회사 메테오시뮬레이션(이하 "회사")이 제공하는
              소프트웨어 제품 및 서비스(이하 "서비스")의 결제 취소, 환불 및
              구독 해지에 관한 사항을 규정함을 목적으로 합니다.
            </p>
          </section>

          <section>
            <h3>제2조 (환불 가능 조건)</h3>
            <p>다음 각 호에 해당하는 경우 전액 환불이 가능합니다.</p>
            <ul>
              <li>결제일로부터 7일 이내에 환불을 요청한 경우</li>
              <li>구매한 라이선스를 활성화하지 않은 경우</li>
              <li>서비스의 중대한 하자로 인해 정상적인 이용이 불가능한 경우</li>
              <li>
                회사의 귀책사유로 서비스가 제공되지 못한 경우
              </li>
            </ul>
          </section>

          <section>
            <h3>제3조 (환불 불가 조건)</h3>
            <p>다음 각 호에 해당하는 경우 환불이 불가합니다.</p>
            <ul>
              <li>라이선스를 활성화하였거나 서비스를 이용한 이력이 있는 경우</li>
              <li>결제일로부터 7일이 경과한 경우</li>
              <li>무료 체험(Trial) 기간 중 또는 무료 체험 후 전환된 유료 결제의 경우</li>
              <li>이용약관에서 정한 환불 제한 사유에 해당하는 경우</li>
              <li>이용자의 단순 변심에 의한 경우 (라이선스 활성화 후)</li>
            </ul>
          </section>

          <section>
            <h3>제4조 (환불 절차)</h3>
            <p>환불을 요청하시는 경우 다음의 절차에 따라 처리됩니다.</p>
            <ol>
              <li>
                <strong>환불 신청:</strong> 회사 고객지원 이메일(
                <a href="mailto:support@msimul.com">support@msimul.com</a>)로
                환불 사유, 결제 정보(주문번호, 결제일, 결제 수단)를 기재하여 신청합니다.
              </li>
              <li>
                <strong>신청 접수 및 검토:</strong> 회사는 접수된 환불 요청을
                확인하고, 환불 가능 여부를 검토합니다.
              </li>
              <li>
                <strong>환불 처리:</strong> 검토 완료 후, 환불 승인 시 원래
                결제 수단으로 환불이 진행됩니다.
              </li>
            </ol>
          </section>

          <section>
            <h3>제5조 (환불 처리 기간)</h3>
            <p>
              환불은 접수일로부터 영업일 기준 5~7일 이내에 처리됩니다. 다만,
              결제 수단에 따라 환불 금액의 실제 반영 시점은 상이할 수 있습니다.
            </p>
            <ul>
              <li>신용카드: 카드사에 따라 취소 반영까지 3~7 영업일 소요</li>
              <li>계좌이체: 환불 승인 후 1~3 영업일 이내 입금</li>
              <li>기타 결제 수단: 해당 결제 서비스 제공자의 정책에 따름</li>
            </ul>
          </section>

          <section>
            <h3>제6조 (구독 취소)</h3>
            <p>
              구독형 서비스를 이용 중인 경우, 구독 취소 시 다음과 같이
              처리됩니다.
            </p>
            <ul>
              <li>
                구독 취소를 요청하면 다음 결제 예정일부터 자동갱신이
                중지됩니다.
              </li>
              <li>
                이미 결제된 현재 구독 기간의 남은 기간 동안은 서비스를 계속
                이용할 수 있습니다.
              </li>
              <li>
                구독 기간 중 중도 취소에 따른 일할 환불은 제공되지 않습니다.
              </li>
            </ul>
          </section>

          <section>
            <h3>제7조 (무료 체험)</h3>
            <p>
              무료 체험(Trial) 서비스는 무상으로 제공되는 것으로, 환불 대상에
              해당하지 않습니다. 무료 체험 기간 종료 후 유료 전환 시에는 본
              환불정책이 적용됩니다.
            </p>
          </section>

          <section>
            <h3>제8조 (정책 변경)</h3>
            <p>
              회사는 관련 법령의 변경 또는 서비스 운영상의 필요에 따라 본
              환불정책을 변경할 수 있습니다. 변경 시에는 시행일 7일 전에
              홈페이지를 통해 공지하며, 이용자에게 불리한 변경의 경우 30일
              전에 공지합니다.
            </p>
          </section>

          <section>
            <h3>부칙</h3>
            <p>본 환불정책은 2025년 1월 1일부터 시행합니다.</p>
          </section>
        </div>
      </main>

      <Footer />
    </div>
  );
};

export default RefundPolicyPage;
