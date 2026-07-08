import React from 'react';

interface MiniChartProps {
  data: number[];
  type?: 'line' | 'bar';
  color?: string;
  height?: number;
  labels?: string[];
}

export const MiniChart: React.FC<MiniChartProps> = ({
  data = [],
  type = 'bar',
  color = '#3b82f6',
  height = 40,
  labels = [],
}) => {
  if (!data || data.length === 0) return null;

  const max = Math.max(...data, 1);
  const min = Math.min(...data, 0);
  const range = max - min;

  const containerStyle: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    width: '100%',
    boxSizing: 'border-box',
    gap: '4px',
  };

  const chartAreaStyle: React.CSSProperties = {
    height: `${height}px`,
    width: '100%',
    position: 'relative',
  };

  if (type === 'bar') {
    const barWidth = 100 / data.length;
    return (
      <div style={containerStyle}>
        <div style={{ ...chartAreaStyle, display: 'flex', alignItems: 'end', gap: '4px' }}>
          {data.map((val, idx) => {
            const hPct = ((val - min) / range) * 100;
            return (
              <div
                key={idx}
                style={{
                  flex: 1,
                  height: `${Math.max(hPct, 8)}%`, // At least a tiny height so it's visible
                  background: color,
                  borderRadius: '4px 4px 0 0',
                  opacity: 0.85,
                  transition: 'height 0.3s ease',
                  position: 'relative',
                }}
                title={val.toString()}
              />
            );
          })}
        </div>
        {labels && labels.length > 0 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '10px', color: '#686888', marginTop: '2px' }}>
            {labels.map((lbl, idx) => (
              <span key={idx} style={{ flex: 1, textAlign: 'center' }}>
                {lbl}
              </span>
            ))}
          </div>
        )}
      </div>
    );
  }

  // Line Chart (SVG Sparkline)
  const width = 300;
  const padding = 5;
  const points = data
    .map((val, idx) => {
      const x = padding + (idx * (width - padding * 2)) / (data.length - 1 || 1);
      const y = height - padding - ((val - min) / range) * (height - padding * 2);
      return `${x},${y}`;
    })
    .join(' ');

  return (
    <div style={containerStyle}>
      <div style={chartAreaStyle}>
        <svg
          viewBox={`0 0 ${width} ${height}`}
          style={{ width: '100%', height: '100%', display: 'block' }}
        >
          <polyline
            fill="none"
            stroke={color}
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            points={points}
          />
        </svg>
      </div>
      {labels && labels.length > 0 && (
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '10px', color: '#686888' }}>
          {labels.map((lbl, idx) => (
            <span key={idx}>{lbl}</span>
          ))}
        </div>
      )}
    </div>
  );
};
