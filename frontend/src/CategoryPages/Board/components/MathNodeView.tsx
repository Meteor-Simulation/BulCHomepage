import React, { useEffect, useRef, useState } from 'react';
import { NodeViewWrapper } from '@tiptap/react';
import katex from 'katex';
import 'katex/dist/katex.min.css';

const MathNodeView: React.FC<any> = ({ node, updateAttributes, selected }) => {
  const renderRef = useRef<HTMLSpanElement>(null);
  const [isEditing] = useState(false);
  const latex = node.attrs.latex || '';

  useEffect(() => {
    if (renderRef.current && latex && !isEditing) {
      try {
        katex.render(latex, renderRef.current, {
          throwOnError: false,
          displayMode: false,
        });
      } catch {
        renderRef.current.textContent = latex;
      }
    }
  }, [latex, isEditing]);

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    // 수식 클릭 시 이벤트를 부모(PostEditorPage)로 전달
    const event = new CustomEvent('edit-math', {
      detail: { latex, updateAttributes },
      bubbles: true,
    });
    e.currentTarget.dispatchEvent(event);
  };

  return (
    <NodeViewWrapper as="span" className="math-node-wrapper">
      <span
        ref={renderRef}
        className={`math-node-render ${selected ? 'selected' : ''}`}
        onClick={handleClick}
        title="클릭하여 수식 편집"
      />
    </NodeViewWrapper>
  );
};

export default MathNodeView;
