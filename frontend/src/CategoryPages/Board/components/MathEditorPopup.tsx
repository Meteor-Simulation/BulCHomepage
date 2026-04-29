import React, { useEffect, useRef, useState } from 'react';
import 'mathlive';
import './MathEditorPopup.css';

interface MathEditorPopupProps {
  isOpen: boolean;
  initialLatex: string;
  onInsert: (latex: string) => void;
  onClose: () => void;
}

const EXAMPLE_FORMULAS = [
  { label: 'Σ', latex: '\\sum_{i=1}^{n} x_i', title: '시그마' },
  { label: '∫', latex: '\\int_{0}^{\\infty} f(x)\\,dx', title: '적분' },
  { label: '√', latex: '\\sqrt{x}', title: '제곱근' },
  { label: 'a/b', latex: '\\frac{a}{b}', title: '분수' },
  { label: 'x²', latex: 'x^{2}', title: '거듭제곱' },
  { label: 'lim', latex: '\\lim_{x \\to 0}', title: '극한' },
  { label: '≠', latex: '\\neq', title: '같지 않음' },
  { label: '≤', latex: '\\leq', title: '이하' },
  { label: '≥', latex: '\\geq', title: '이상' },
  { label: '∞', latex: '\\infty', title: '무한대' },
  { label: 'α', latex: '\\alpha', title: '알파' },
  { label: 'β', latex: '\\beta', title: '베타' },
  { label: 'π', latex: '\\pi', title: '파이' },
  { label: 'θ', latex: '\\theta', title: '세타' },
  { label: '행렬', latex: '\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}', title: '행렬' },
];

const MathEditorPopup: React.FC<MathEditorPopupProps> = ({
  isOpen, initialLatex, onInsert, onClose,
}) => {
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
        <span className="math-popup-title">수식 편집기</span>
        <button className="math-popup-close" onClick={onClose}>&times;</button>
      </div>

      <div className="math-popup-body">
        <div className="math-examples">
          {EXAMPLE_FORMULAS.map((f, i) => (
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
        <button className="math-cancel-btn" onClick={onClose}>취소</button>
        <button className="math-insert-btn" onClick={handleInsert}>삽입</button>
      </div>
    </div>
  );
};

export default MathEditorPopup;
