import React from 'react';
import { useTranslation } from 'react-i18next';
import '../Common/CategoryPages.css';
import './RefundPolicy.css';
import Header from '../../components/Header';
import Footer from '../../components/Footer';
import { POLICY_SECTIONS, PolicyLang } from '../../components/policyContent';

const RefundPolicyPage: React.FC = () => {
  const { t, i18n } = useTranslation();
  const lang: PolicyLang = i18n.language && i18n.language.startsWith('en') ? 'en' : 'ko';
  const sections = POLICY_SECTIONS.refund[lang];
  const effectiveLabel = lang === 'en' ? 'Effective Date: January 1, 2025' : '시행일: 2025년 1월 1일';

  return (
    <div className="app">
      <Header />

      <main className="main-content sub-page">
        <div className="refund-policy-container">
          <h1>{t('footer.refund')}</h1>
          <p className="policy-updated">{effectiveLabel}</p>

          {sections.map((section, i) => (
            <section key={i}>
              <h3>{section.title}</h3>
              {section.bodies.map((body, j) => (
                <p key={j} dangerouslySetInnerHTML={{ __html: body }} />
              ))}
            </section>
          ))}
        </div>
      </main>

      <Footer />
    </div>
  );
};

export default RefundPolicyPage;
