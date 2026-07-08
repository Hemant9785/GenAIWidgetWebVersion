import React from 'react';

interface ProgressBarProps {
  value: number;
  max?: number;
  color?: string;
  height?: number;
  label?: string;
  showValue?: boolean;
}

export const ProgressBar: React.FC<ProgressBarProps> = ({
  value,
  max = 100,
  color = '#7c3aed',
  height = 8,
  label = '',
  showValue = false,
}) => {
  const percentage = Math.min(Math.max((value / max) * 100, 0), 100);

  const containerStyle: React.CSSProperties = {
    width: '100%',
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
    boxSizing: 'border-box',
  };

  const labelRowStyle: React.CSSProperties = {
    display: 'flex',
    justifyContent: 'space-between',
    fontSize: '12px',
    fontWeight: 500,
    color: '#9898b0',
    fontFamily: "'Inter', sans-serif",
  };

  const trackStyle: React.CSSProperties = {
    width: '100%',
    height: `${height}px`,
    background: 'rgba(255, 255, 255, 0.08)',
    borderRadius: '999px',
    overflow: 'hidden',
    position: 'relative',
  };

  const fillStyle: React.CSSProperties = {
    width: `${percentage}%`,
    height: '100%',
    background: color,
    borderRadius: '999px',
    transition: 'width 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
  };

  return (
    <div style={containerStyle}>
      {(label || showValue) && (
        <div style={labelRowStyle}>
          {label && <span>{label}</span>}
          {showValue && <span>{Math.round(percentage)}%</span>}
        </div>
      )}
      <div style={trackStyle}>
        <div style={fillStyle} />
      </div>
    </div>
  );
};
