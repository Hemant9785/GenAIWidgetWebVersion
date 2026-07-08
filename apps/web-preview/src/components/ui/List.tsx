import React from 'react';

interface ListProps {
  direction?: 'vertical' | 'horizontal';
  gap?: number;
  children?: React.ReactNode;
}

export const List: React.FC<ListProps> = ({
  direction = 'vertical',
  gap = 8,
  children,
}) => {
  const style: React.CSSProperties = {
    display: 'flex',
    flexDirection: direction === 'vertical' ? 'column' : 'row',
    gap: `${gap}px`,
    width: '100%',
    boxSizing: 'border-box',
    overflowX: direction === 'horizontal' ? 'auto' : undefined,
    scrollbarWidth: direction === 'horizontal' ? 'none' : undefined, // Hide scrollbar for clean design
  };

  return <div style={style}>{children}</div>;
};
