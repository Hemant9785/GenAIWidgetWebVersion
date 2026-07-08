import React from 'react';

interface ColumnProps {
  gap?: number;
  align?: 'start' | 'center' | 'end' | 'stretch';
  justify?: 'start' | 'center' | 'end' | 'between' | 'around';
  padding?: number;
  children?: React.ReactNode;
}

export const Column: React.FC<ColumnProps> = ({
  gap = 8,
  align = 'stretch',
  justify = 'start',
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
    flexDirection: 'column',
    gap: `${gap}px`,
    alignItems: alignMap[align] || 'stretch',
    justifyContent: justifyMap[justify] || 'flex-start',
    padding: padding ? `${padding}px` : undefined,
    width: '100%',
    boxSizing: 'border-box',
  };

  return <div style={style}>{children}</div>;
};
