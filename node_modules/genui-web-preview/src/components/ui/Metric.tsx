import React from 'react';

interface MetricProps {
  value: string | number;
  unit?: string;
  label: string;
  size?: 'sm' | 'md' | 'lg';
  valueColor?: string;
}

export const Metric: React.FC<MetricProps> = ({
  value,
  unit = '',
  label,
  size = 'md',
  valueColor = '#e8e8f0',
}) => {
  const valueSizeMap = {
    sm: '20px',
    md: '28px',
    lg: '40px',
  };

  const labelSizeMap = {
    sm: '10px',
    md: '12px',
    lg: '14px',
  };

  const unitSizeMap = {
    sm: '12px',
    md: '16px',
    lg: '22px',
  };

  const valueStyle: React.CSSProperties = {
    fontSize: valueSizeMap[size] || '28px',
    fontWeight: 700,
    color: valueColor,
    lineHeight: 1.1,
    margin: 0,
    fontFamily: "'Inter', sans-serif",
    display: 'flex',
    alignItems: 'baseline',
  };

  const unitStyle: React.CSSProperties = {
    fontSize: unitSizeMap[size] || '16px',
    fontWeight: 500,
    marginLeft: '2px',
    opacity: 0.8,
  };

  const labelStyle: React.CSSProperties = {
    fontSize: labelSizeMap[size] || '12px',
    fontWeight: 500,
    color: '#9898b0',
    marginTop: '4px',
    textTransform: 'uppercase',
    letterSpacing: '0.5px',
    margin: 0,
    fontFamily: "'Inter', sans-serif",
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', boxSizing: 'border-box' }}>
      <div style={valueStyle}>
        {value}
        {unit && <span style={unitStyle}>{unit}</span>}
      </div>
      <div style={labelStyle}>{label}</div>
    </div>
  );
};
