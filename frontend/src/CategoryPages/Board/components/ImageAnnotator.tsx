import React, { useState, useRef } from 'react';
import './ImageAnnotator.css';

export type MarkerType = 'pin' | 'rect' | 'ellipse';

export interface Marker {
  number: number;
  type: MarkerType;
  x: number; // 퍼센트 (0~100)
  y: number; // 퍼센트 (0~100)
  width?: number;  // 퍼센트 (rect/ellipse)
  height?: number; // 퍼센트 (rect/ellipse)
  label: string;
  description: string;
}

export interface AnnotatedImage {
  imageUrl: string;
  file?: File;
  markers: Marker[];
}

interface ImageAnnotatorProps {
  image: AnnotatedImage;
  index: number;
  readOnly?: boolean;
  onChange?: (image: AnnotatedImage) => void;
  onDelete?: () => void;
}

const ImageAnnotator: React.FC<ImageAnnotatorProps> = ({
  image, index, readOnly = false, onChange, onDelete,
}) => {
  const imageRef = useRef<HTMLDivElement>(null);
  const [draggingMarker, setDraggingMarker] = useState<number | null>(null);
  const [activeMarkerType, setActiveMarkerType] = useState<MarkerType>('pin');
  const [drawingMarker, setDrawingMarker] = useState<{ startX: number; startY: number } | null>(null);

  // 이미지 클릭/드래그 → 마커 추가
  const handleImageMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    if (readOnly || !onChange || !imageRef.current) return;
    if ((e.target as HTMLElement).closest('.marker-pin, .marker-shape')) return;

    const rect = imageRef.current.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * 100;
    const y = ((e.clientY - rect.top) / rect.height) * 100;

    if (activeMarkerType === 'pin') {
      const newMarker: Marker = {
        number: image.markers.length + 1,
        type: 'pin',
        x, y,
        label: '',
        description: '',
      };
      onChange({ ...image, markers: [...image.markers, newMarker] });
    } else {
      // rect/ellipse → 드래그로 영역 지정 시작
      setDrawingMarker({ startX: x, startY: y });
    }
  };

  const handleImageMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!drawingMarker || !imageRef.current) return;
    // 드래그 중 프리뷰는 CSS로 처리 (추후 확장 가능)
  };

  const handleImageMouseUp = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!drawingMarker || !onChange || !imageRef.current) return;

    const rect = imageRef.current.getBoundingClientRect();
    const endX = ((e.clientX - rect.left) / rect.width) * 100;
    const endY = ((e.clientY - rect.top) / rect.height) * 100;

    const x = Math.min(drawingMarker.startX, endX);
    const y = Math.min(drawingMarker.startY, endY);
    const width = Math.abs(endX - drawingMarker.startX);
    const height = Math.abs(endY - drawingMarker.startY);

    // 너무 작으면 무시 (실수 클릭 방지)
    if (width < 3 && height < 3) {
      setDrawingMarker(null);
      return;
    }

    const newMarker: Marker = {
      number: image.markers.length + 1,
      type: activeMarkerType,
      x, y, width, height,
      label: '',
      description: '',
    };
    onChange({ ...image, markers: [...image.markers, newMarker] });
    setDrawingMarker(null);
  };

  // 마커 드래그
  const handleMarkerMouseDown = (e: React.MouseEvent, markerNumber: number) => {
    if (readOnly) return;
    e.stopPropagation();
    setDraggingMarker(markerNumber);

    const handleMouseMove = (moveEvent: MouseEvent) => {
      if (!imageRef.current || !onChange) return;
      const rect = imageRef.current.getBoundingClientRect();
      const x = Math.max(0, Math.min(100, ((moveEvent.clientX - rect.left) / rect.width) * 100));
      const y = Math.max(0, Math.min(100, ((moveEvent.clientY - rect.top) / rect.height) * 100));

      const updated = image.markers.map(m =>
        m.number === markerNumber ? { ...m, x, y } : m
      );
      onChange({ ...image, markers: updated });
    };

    const handleMouseUp = () => {
      setDraggingMarker(null);
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
  };

  // 마커 설명 변경
  const handleMarkerChange = (number: number, field: 'label' | 'description', value: string) => {
    if (!onChange) return;
    const updated = image.markers.map(m =>
      m.number === number ? { ...m, [field]: value } : m
    );
    onChange({ ...image, markers: updated });
  };

  // 마커 삭제 + 번호 재정렬
  const handleDeleteMarker = (number: number) => {
    if (!onChange) return;
    const filtered = image.markers
      .filter(m => m.number !== number)
      .map((m, i) => ({ ...m, number: i + 1 }));
    onChange({ ...image, markers: filtered });
  };

  return (
    <div className="annotator-container">
      <div className="annotator-header">
        <span className="annotator-label">이미지 {index + 1}</span>
        {!readOnly && onDelete && (
          <button className="annotator-delete-btn" onClick={onDelete} title="이미지 제거">
            &times;
          </button>
        )}
      </div>

      {/* 마커 타입 선택 (편집 모드) */}
      {!readOnly && (
        <div className="annotator-tools">
          <button
            className={`tool-btn ${activeMarkerType === 'pin' ? 'active' : ''}`}
            onClick={() => setActiveMarkerType('pin')}
            title="포인트 마커"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="8"/></svg>
          </button>
          <button
            className={`tool-btn ${activeMarkerType === 'rect' ? 'active' : ''}`}
            onClick={() => setActiveMarkerType('rect')}
            title="사각형 영역"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><rect x="3" y="3" width="18" height="18" rx="2"/></svg>
          </button>
          <button
            className={`tool-btn ${activeMarkerType === 'ellipse' ? 'active' : ''}`}
            onClick={() => setActiveMarkerType('ellipse')}
            title="타원 영역"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><ellipse cx="12" cy="12" rx="10" ry="7"/></svg>
          </button>
        </div>
      )}

      {/* 이미지 + 마커 영역 */}
      <div
        className="annotator-image-area"
        ref={imageRef}
        onMouseDown={handleImageMouseDown}
        onMouseMove={handleImageMouseMove}
        onMouseUp={handleImageMouseUp}
      >
        <img src={image.imageUrl} alt={`이미지 ${index + 1}`} draggable={false} />
        {!readOnly && image.markers.length === 0 && (
          <div className="annotator-hint">
            {activeMarkerType === 'pin' ? '이미지를 클릭하여 마커를 추가하세요' : '이미지 위에서 드래그하여 영역을 지정하세요'}
          </div>
        )}
        {image.markers.map((marker) => (
          marker.type === 'pin' || !marker.type ? (
            <div
              key={marker.number}
              className={`marker-pin ${draggingMarker === marker.number ? 'dragging' : ''}`}
              style={{ left: `${marker.x}%`, top: `${marker.y}%` }}
              onMouseDown={(e) => handleMarkerMouseDown(e, marker.number)}
            >
              {marker.number}
            </div>
          ) : (
            <div
              key={marker.number}
              className={`marker-shape ${marker.type} ${draggingMarker === marker.number ? 'dragging' : ''}`}
              style={{
                left: `${marker.x}%`,
                top: `${marker.y}%`,
                width: `${marker.width || 10}%`,
                height: `${marker.height || 10}%`,
                borderRadius: marker.type === 'ellipse' ? '50%' : '4px',
              }}
              onMouseDown={(e) => handleMarkerMouseDown(e, marker.number)}
            >
              <span className="shape-number">{marker.number}</span>
            </div>
          )
        ))}
      </div>

      {/* 마커 설명 목록 */}
      {image.markers.length > 0 && (
        <div className="annotator-descriptions">
          {image.markers.map((marker) => (
            <div key={marker.number} className="annotator-desc-row">
              <span className="desc-number">{marker.number}</span>
              {readOnly ? (
                <div className="desc-readonly">
                  {marker.label && <strong>{marker.label}: </strong>}
                  {marker.description}
                </div>
              ) : (
                <>
                  <input
                    className="desc-label-input"
                    placeholder="항목명"
                    value={marker.label}
                    onChange={(e) => handleMarkerChange(marker.number, 'label', e.target.value)}
                  />
                  <input
                    className="desc-description-input"
                    placeholder="설명을 입력하세요"
                    value={marker.description}
                    onChange={(e) => handleMarkerChange(marker.number, 'description', e.target.value)}
                  />
                  <button
                    className="desc-delete-btn"
                    onClick={() => handleDeleteMarker(marker.number)}
                    title="마커 삭제"
                  >
                    &times;
                  </button>
                </>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default ImageAnnotator;
