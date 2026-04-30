import React from 'react';

const logosRow1 = [
  { src: '/logos/samsung.png', alt: 'Samsung' },
  { src: '/logos/LH.jpg', alt: 'LH' },
  { src: '/logos/GS_logo_(South_Korean_company).svg.png', alt: 'GS' },
  { src: '/logos/seoul.jpg', alt: 'Seoul' },
  { src: '/logos/inchen.jpg', alt: 'Incheon' },
  { src: '/logos/hB_FNC.png', alt: 'hB FNC' },
  { src: '/logos/fire_buster.png', alt: 'Fire Buster', dark: true },
  { src: '/logos/filk.png', alt: 'FILK' },
] as const;

const logosRow2 = [
  { src: '/logos/deffence.jpg', alt: 'Defence' },
  { src: '/logos/sobang.png', alt: 'Sobang' },
  { src: '/logos/shinhwa.jpg', alt: 'Shinhwa' },
  { src: '/logos/sea.png', alt: 'Sea' },
  { src: '/logos/japan.jpg', alt: 'Japan' },
  { src: '/logos/woosuk.jpg', alt: 'Woosuk' },
  { src: '/logos/mv_step1_txt1.png', alt: 'MV' },
];

const LogoMarqueeSection: React.FC = () => {
  return (
    <section id="logo-marquee" className="bulc-comparison">
      <div className="bulc-comparison__container">
        <div className="bulc-comparison__marquee-wrap">
          <div className="bulc-comparison__marquee-fade" />
          <div className="bulc-comparison__marquee bulc-comparison__marquee--left">
            <div className="bulc-comparison__marquee-track">
              {[...logosRow1, ...logosRow1].map((logo, i) => (
                <div className={`bulc-comparison__marquee-item${'dark' in logo && logo.dark ? ' bulc-comparison__marquee-item--dark' : ''}`} key={i}>
                  <img src={logo.src} alt={logo.alt} />
                </div>
              ))}
            </div>
          </div>
          <div className="bulc-comparison__marquee bulc-comparison__marquee--right">
            <div className="bulc-comparison__marquee-track">
              {[...logosRow2, ...logosRow2].map((logo, i) => (
                <div className="bulc-comparison__marquee-item" key={i}>
                  <img src={logo.src} alt={logo.alt} />
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default LogoMarqueeSection;
