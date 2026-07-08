import React from 'react';

interface RowProps {
  gap?: number;
  align?: 'start' | 'center' | 'end' | 'stretch';
  justify?: 'start' | 'center' | 'end' | 'between' | 'around';
  wrap?: boolean;
  padding?: number;
  children?: React.ReactNode;
}

export const Row: React.FC<RowProps> = ({
  gap = 8,
  align = 'center',
  justify = 'start',
  wrap = false,
  padding = 0,
  children,
}) => {
  const justifyMap = {
    start: 'flex-start',
    center: 'center',
    end: 'flex-end',
    between: 'space-between',
    around: 'space-around',
  };

  const alignMap = {
    start: 'flex-start',
    center: 'center',
    end: 'flex-end',
    stretch: 'stretch',
  };

  const style: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'row',
    gap: `${gap}px`,
    alignItems: alignMap[align] || 'center',
    justifyContent: justifyMap[justify] || 'flex-start',
    flexWrap: wrap ? 'wrap' : 'nowrap',
    padding: padding ? `${padding}px` : undefined,
    width: '100%',
    boxSizing: 'border-box',
  };

  return <div style={style}>{children}</div>;
};
