package com.bulc.homepage.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 모듈 경계를 테스트로 강제한다 (MDP-906).
 *
 * <p>모듈형 구조의 목표는 서버 이전·분할이 자유롭고 침해 시 폭발 반경이 모듈 안에서 끝나는 것이다.
 * 그런데 지금까지 경계가 유지된 것은 <b>규율이지 강제가 아니었다</b>. 모듈이 늘수록 규율만으로는
 * 반드시 샌다 — 실수 한 번이면 경계가 무너지고, 무너진 뒤에는 되돌리는 비용이 훨씬 크다.
 *
 * <p>여기 담은 규칙은 <b>모두 현재 코드에서 통과하는 것</b>이다. 통과하지 않는 규칙을 넣으면
 * 빨간불이 일상이 되어 아무도 보지 않게 된다. 아직 지키지 못하는 경계는 규칙으로 만들지 않고
 * 부채로 기록해 둔다(맨 아래 주석).
 *
 * <p><b>현재 모듈 간 참조 (2026-09-28 실측)</b>
 * <pre>
 *   licensing → payment.port   4회   의존성 역전 — 결제가 licensing 을 모르게 하려고 계약을 결제 쪽에 뒀다
 *   payment   → mail.api       2회   공개 계약 경유
 *   mail      → lead.api       2회   공개 계약 경유 (MDP-907 에서 repository 직접 조회를 걷어냈다)
 *   oauth2    → oauth          3회   같은 인증 축의 하위 모듈 (구현 직접 참조, 용인)
 *   lead      → (없음)                자족 모듈
 *   content   → (없음)                자족 모듈 (들어오는 참조도 0)
 *   → audit.api                3회   인증·결제·OAuth 가 기록. 전부 계약 경유 (MDP-924)
 *   licensing → catalog.api    4회   상품 조회(id·code·name). 전부 계약 경유 (MDP-925)
 *   catalog   → (없음)                자족 모듈
 * </pre>
 */
@DisplayName("모듈 경계")
class ModuleBoundaryTest {

    private static final String BASE = "com.bulc.homepage";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Nested
    @DisplayName("공개 계약만 통과한다")
    class PublishedContractOnly {

        /**
         * 메일 모듈의 공개 계약은 {@code mail.api} 다 (MailPort · EmailCategory).
         * 바깥이 {@code mail.service} 를 직접 부르면, 구현을 바꾸거나 별도 서비스로 떼어낼 때
         * 호출부가 전부 깨진다. MDP-895 에서 소비자 6곳을 모두 api 로 돌렸다.
         */
        @Test
        @DisplayName("mail 바깥은 mail.service 를 직접 참조하지 않는다")
        void mailServiceIsInternal() {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage(BASE + ".mail..")
                    .should().dependOnClassesThat().resideInAnyPackage(BASE + ".mail.service..")
                    .because("메일 모듈의 공개 계약은 mail.api 다. 구현(mail.service)은 내부다 — MDP-895");
            rule.check(classes);
        }

        /**
         * 리드/컨택은 비회원의 이메일·이름·소속을 들고 있다. <b>엔티티가 모듈 밖으로 나가지 않는 것
         * 자체가 목적</b>이다 — 침해가 나도 폭발 반경이 이 모듈 안에서 끝나야 한다.
         * 공개 계약은 {@code lead.api} 다 (LeadContactPort · MailingContact · MarketingConsent).
         */
        @Test
        @DisplayName("lead 바깥은 lead 내부(service · repository · domain)를 참조하지 않는다")
        void leadInternalsAreHidden() {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage(BASE + ".lead..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            BASE + ".lead.service..",
                            BASE + ".lead.repository..",
                            BASE + ".lead.domain..",
                            BASE + ".lead.dto..")
                    .because("개인정보를 담은 엔티티가 모듈 밖으로 나가면 안 된다. 공개 계약은 lead.api — MDP-907");
            rule.check(classes);
        }

        /**
         * 콘텐츠(팝업)는 들어오는 참조가 처음부터 0 이었다 (MDP-922). 공지·팝업은 화면에
         * 뿌리는 것이 전부라 다른 도메인이 알 이유가 없다. 그 상태를 규칙으로 고정해 둔다 —
         * 나중에 누가 "팝업 노출 여부를 결제에서 판단" 같은 결합을 만들면 여기서 걸린다.
         *
         * <p>공개 계약이 아직 없다. 소비자가 없어서다. 생기면 {@code content/api} 를 만든다.
         */
        @Test
        @DisplayName("content 바깥은 content 내부를 참조하지 않는다")
        void contentIsSelfContained() {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage(BASE + ".content..")
                    .should().dependOnClassesThat().resideInAnyPackage(BASE + ".content..")
                    .because("팝업·공지는 다른 도메인이 알 이유가 없다 — MDP-922");
            rule.check(classes);
        }

        /**
         * 감사 로깅은 횡단 관심사다 — 인증·결제·OAuth 가 모두 기록을 남긴다. 그래서 제공자가
         * 계약을 공개하는 형태다 ({@code audit.api.ActivityLogPort}).
         *
         * <p>직전까지 소비자들이 {@code ActivityLogRepository} 와 엔티티를 직접 들고 썼다.
         * {@code AuthService} 는 엔티티를 손으로 빌드해 저장했다. 그러면 적재 방식을 바꿀 때
         * (별도 저장소 분리·비동기 전환) 호출부 전부를 고쳐야 한다. MDP-924 에서 3곳을 모두 api 로 돌렸다.
         */
        @Test
        @DisplayName("audit 바깥은 audit 내부(service · repository · domain)를 참조하지 않는다")
        void auditInternalsAreHidden() {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage(BASE + ".audit..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            BASE + ".audit.service..",
                            BASE + ".audit.repository..",
                            BASE + ".audit.domain..")
                    .because("감사 로그 적재 방식을 바꿀 때 호출부가 따라 깨지면 안 된다. 공개 계약은 audit.api — MDP-924");
            rule.check(classes);
        }

        @Test
        @DisplayName("mail 바깥은 mail.repository · mail.domain 을 참조하지 않는다")
        void mailInternalsAreHidden() {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage(BASE + ".mail..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            BASE + ".mail.repository..", BASE + ".mail.domain..")
                    .because("발송 이력(EmailLog)과 조회는 메일 모듈 내부 구현이다");
            rule.check(classes);
        }

        /**
         * MDP-831 의 의존성 역전. 결제가 licensing 을 모르게 하려고 계약을 결제 쪽에 뒀고,
         * licensing 의 어댑터가 그것을 구현한다. 그래서 결제 → licensing 방향은 0 이어야 한다.
         */
        @Test
        @DisplayName("payment 는 licensing 을 참조하지 않는다")
        void paymentDoesNotKnowLicensing() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE + ".payment..")
                    .should().dependOnClassesThat().resideInAnyPackage(BASE + ".licensing..")
                    .because("결제는 라이선스 발급자를 알지 않는다. LicenseIssuePort 로 뒤집었다 — MDP-831");
            rule.check(classes);
        }

        /**
         * licensing 이 결제를 보는 유일한 창은 {@code payment.port} 다.
         * 결제 내부(service · recovery · notification)를 보면 역전이 무의미해진다.
         */
        @Test
        @DisplayName("licensing 은 payment 내부를 참조하지 않는다 (port 만)")
        void licensingSeesOnlyPaymentPort() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE + ".licensing..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            BASE + ".payment.service..",
                            BASE + ".payment.recovery..",
                            BASE + ".payment.notification..")
                    .because("licensing 이 결제를 보는 창은 payment.port 뿐이다");
            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("모듈은 서로 독립이다")
    class ModuleIndependence {

        @Test
        @DisplayName("licensing 과 mail 은 서로를 참조하지 않는다")
        void licensingAndMailAreIndependent() {
            ArchRule licensingToMail = noClasses()
                    .that().resideInAPackage(BASE + ".licensing..")
                    .should().dependOnClassesThat().resideInAnyPackage(BASE + ".mail..")
                    .because("라이선스 통지는 스케줄러가 MailPort 로 보낸다. licensing 모듈 자체는 메일을 모른다");
            licensingToMail.check(classes);

            ArchRule mailToLicensing = noClasses()
                    .that().resideInAPackage(BASE + ".mail..")
                    .should().dependOnClassesThat().resideInAnyPackage(BASE + ".licensing..")
                    .because("메일 모듈에 도메인 문구를 두지 않는다 — MDP-895");
            mailToLicensing.check(classes);
        }

        /**
         * 리드/컨택은 다른 도메인 모듈을 부르지 않는다. 컨택을 등록·조회·해지하는 데
         * 라이선스·결제·메일을 알 필요가 없기 때문이다. 이 방향이 0 이면 모듈을 통째로 떼어낼 수 있다.
         *
         * <p>평면 계층의 {@code service.PublicFormRateLimiter} 는 예외로 남아 있다 —
         * 공개 폼 남용 방지라는 공용 인프라이고 도메인 모듈이 아니다. 인프라를 별도로 가를 때 재검토.
         */
        @Test
        @DisplayName("lead 는 다른 도메인 모듈을 참조하지 않는다")
        void leadIsSelfContained() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE + ".lead..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            BASE + ".licensing..", BASE + ".payment..",
                            BASE + ".mail..", BASE + ".oauth..", BASE + ".content..")
                    .because("컨택 관리에 라이선스·결제·메일을 알 필요가 없다 — MDP-907");
            rule.check(classes);
        }

        @Test
        @DisplayName("content 는 다른 도메인 모듈을 참조하지 않는다")
        void contentDoesNotKnowOtherModules() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE + ".content..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            BASE + ".licensing..", BASE + ".payment..",
                            BASE + ".mail..", BASE + ".lead..", BASE + ".oauth..")
                    .because("팝업 노출에 라이선스·결제를 알 필요가 없다 — MDP-922");
            rule.check(classes);
        }

        @Test
        @DisplayName("mail 은 payment 를 참조하지 않는다")
        void mailDoesNotKnowPayment() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE + ".mail..")
                    .should().dependOnClassesThat().resideInAnyPackage(BASE + ".payment..")
                    .because("발급 완료 문구는 결제가 소유한다 — MDP-895");
            rule.check(classes);
        }

        /**
         * 순환이 생기면 두 모듈을 따로 떼어낼 수 없다 — 하나를 옮기려면 다른 하나도 따라와야 한다.
         * 지금은 없으므로 지금 고정한다.
         */
        @Test
        @DisplayName("모듈 사이에 순환 의존이 없다")
        void noCyclesBetweenModules() {
            ArchRule rule = slices()
                    .matching(BASE + ".(licensing|payment|mail|lead|content|audit).(*)..")
                    .should().beFreeOfCycles()
                    .because("순환이 있으면 모듈을 따로 떼어낼 수 없다");
            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("계층 규칙")
    class LayerRules {

        @Test
        @DisplayName("엔티티·도메인은 컨트롤러를 참조하지 않는다")
        void domainDoesNotDependOnControllers() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage(
                            BASE + ".entity..", BASE + ".licensing.domain..", BASE + ".mail.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            BASE + "..controller..")
                    .because("도메인은 바깥 세상(HTTP)을 알지 않는다");
            rule.check(classes);
        }

        @Test
        @DisplayName("리포지토리는 서비스를 참조하지 않는다")
        void repositoriesDoNotDependOnServices() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage(BASE + "..repository..")
                    .should().dependOnClassesThat().resideInAnyPackage(BASE + "..service..")
                    .because("데이터 접근이 업무 로직을 거꾸로 부르면 계층이 무의미해진다");
            rule.check(classes);
        }
    }

    /*
     * ── 아직 규칙으로 만들지 못한 부채 ────────────────────────────────────
     *
     * 0. 결제 → catalog.domain (의도적 용인, MDP-925)
     *    Payment.pricePlan · Subscription.product · Subscription.pricePlan 이 JPA 관계로
     *    카탈로그 엔티티를 참조한다. 끊으려면 포트 조회로 바꿔야 하는데 호출부가 14곳이고
     *    N+1 조회 위험이 생긴다. 얻는 것(이론적 분리)보다 잃는 것(복잡도·성능)이 크다.
     *    대신 방향을 한쪽으로 고정했다 — catalog → payment 는 규칙으로 금지한다.
     *    즉 카탈로그는 독립이고, 결제를 떼어낼 때만 카탈로그가 함께 간다.
     *
     *    AdminController(695줄)도 catalog.domain·repository 를 직접 쓴다. 이 컨트롤러는
     *    여러 도메인을 겸하는 관리자 접착부라 분해가 선행 조건이다.
     *
     * 1. mail → entity/repository (회원 쪽만 남음)
     *    OperationalMailService.resolveRecipients 가 UserRepository 를 직접 조회한다
     *    (관리자 '전체 회원 발송'). 컨택 쪽은 MDP-907 에서 LeadContactPort 로 갚았고,
     *    회원 쪽은 회원 모듈이 서는 마지막 단계에 같은 형태로 갚는다.
     *
     * 2. licensing → entity (UserRepository · Product)
     *    회원/카탈로그 모듈이 아직 평면 계층에 있어 참조 자체를 막을 수 없다.
     *    해당 모듈이 서면 계약 경유로 바꾸고 규칙 추가.
     *
     * 3. audit → entity.User · repository.UserRepository (각 1곳)
     *    ActivityLogService.logLoginActivity / logSignupActivity 가 User 를 인자로 받는다.
     *    회원 모듈이 서면 userId 기반으로 바꾼다. 엔티티의 @ManyToOne User 관계는 MDP-924 에서 제거했다.
     *
     * 4. content → repository.UserRepository (1곳)
     *    AdminPopupController 가 작성자 이름 표시를 위해 조회한다. 회원 모듈이 서면 계약 경유로 바꾼다.
     *
     * 5. oauth2 → oauth 구현 직접 참조 (3곳)
     *    같은 인증 축의 하위 모듈이라 용인 중. 회원/인증 모듈화(마지막 단계) 때 재검토.
     *
     * 6. 평면 계층
     *    모듈이 아니라 규칙을 걸 대상이 없다. 모듈화가 진행되는 만큼 규칙을 늘린다.
     */
}
