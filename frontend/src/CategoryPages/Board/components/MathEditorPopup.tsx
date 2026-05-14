import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import 'mathlive';
import './MathEditorPopup.css';

interface MathEditorPopupProps {
  isOpen: boolean;
  initialLatex: string;
  onInsert: (latex: string) => void;
  onClose: () => void;
}

const MathEditorPopup: React.FC<MathEditorPopupProps> = ({
  isOpen, initialLatex, onInsert, onClose,
}) => {
  const { t } = useTranslation();
  const exampleFormulas = [
    { label: 'Σ', latex: '\\sum_{i=1}^{n} x_i', title: t('board.editor.mathPopup.examples.sigma') },
    { label: '∫', latex: '\\int_{0}^{\\infty} f(x)\\,dx', title: t('board.editor.mathPopup.examples.integral') },
    { label: '√', latex: '\\sqrt{x}', title: t('board.editor.mathPopup.examples.sqrt') },
    { label: 'a/b', latex: '\\frac{a}{b}', title: t('board.editor.mathPopup.examples.fraction') },
    { label: 'x²', latex: 'x^{2}', title: t('board.editor.mathPopup.examples.power') },
    { label: 'lim', latex: '\\lim_{x \\to 0}', title: t('board.editor.mathPopup.examples.limit') },
    { label: '≠', latex: '\\neq', title: t('board.editor.mathPopup.examples.neq') },
    { label: '≤', latex: '\\leq', title: t('board.editor.mathPopup.examples.leq') },
    { label: '≥', latex: '\\geq', title: t('board.editor.mathPopup.examples.geq') },
    { label: '∞', latex: '\\infty', title: t('board.editor.mathPopup.examples.infinity') },
    { label: 'α', latex: '\\alpha', title: t('board.editor.mathPopup.examples.alpha') },
    { label: 'β', latex: '\\beta', title: t('board.editor.mathPopup.examples.beta') },
    { label: 'π', latex: '\\pi', title: t('board.editor.mathPopup.examples.pi') },
    { label: 'θ', latex: '\\theta', title: t('board.editor.mathPopup.examples.theta') },
    { label: t('board.editor.mathPopup.matrixLabel'), latex: '\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}', title: t('board.editor.mathPopup.examples.matrix') },
  ];
  const mathFieldRef = useRef<any>(null);
  const popupRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState({ x: 100, y: 100 });
  const [isDragging, setIsDragging] = useState(false);
  const dragOffset = useRef({ x: 0, y: 0 });

  // 팝업 열릴 때 초기값 설정
  useEffect(() => {
    if (isOpen && mathFieldRef.current) {
      setTimeout(() => {
        if (mathFieldRef.current) {
          mathFieldRef.current.value = initialLatex || '';
          mathFieldRef.current.focus();
        }
      }, 100);
    }
  }, [isOpen, initialLatex]);

  // 팝업 위치 초기화
  useEffect(() => {
    if (isOpen) {
      setPosition({
        x: Math.max(50, (window.innerWidth - 420) / 2),
        y: Math.max(50, window.innerHeight * 0.2),
      });
    }
  }, [isOpen]);

  // 드래그 이동
  const handleDragStart = (e: React.MouseEvent) => {
    if ((e.target as HTMLElement).closest('.math-popup-header')) {
      setIsDragging(true);
      dragOffset.current = {
        x: e.clientX - position.x,
        y: e.clientY - position.y,
      };
    }
  };

  useEffect(() => {
    if (!isDragging) return;
    const handleMove = (e: MouseEvent) => {
      setPosition({
        x: e.clientX - dragOffset.current.x,
        y: e.clientY - dragOffset.current.y,
      });
    };
    const handleUp = () => setIsDragging(false);
    window.addEventListener('mousemove', handleMove);
    window.addEventListener('mouseup', handleUp);
    return () => {
      window.removeEventListener('mousemove', handleMove);
      window.removeEventListener('mouseup', handleUp);
    };
  }, [isDragging]);

  const handleInsert = () => {
    const latex = mathFieldRef.current?.value || '';
    if (latex.trim()) {
      onInsert(latex);
    }
    onClose();
  };

  const handleExample = (latex: string) => {
    if (mathFieldRef.current) {
      mathFieldRef.current.value = latex;
    }
  };

  if (!isOpen) return null;

  return (
    <div
      className="math-popup"
      ref={popupRef}
      style={{ left: position.x, top: position.y }}
      onMouseDown={handleDragStart}
    >
      <div className="math-popup-header">
        <span className="math-popup-title">{t('board.editor.mathPopup.title')}</span>
        <button className="math-popup-close" onClick={onClose}>&times;</button>
      </div>

      <div className="math-popup-body">
        <div className="math-examples">
          {exampleFormulas.map((f, i) => (
            <button
              key={i}
              className="math-example-btn"
              onClick={() => handleExample(f.latex)}
              title={f.title}
            >
              {f.label}
            </button>
          ))}
        </div>

        <math-field
          ref={mathFieldRef}
          id="math-editor-field"
          className="math-editor-field"
        />
      </div>

      <div className="math-popup-footer">
        <button className="math-cancel-btn" onClick={onClose}>{t('board.actions.cancel')}</button>
        <button className="math-insert-btn" onClick={handleInsert}>{t('board.editor.mathPopup.insert')}</button>
      </div>
    </div>
  );
};

export default MathEditorPopup;
