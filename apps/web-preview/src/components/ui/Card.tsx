import React from 'react';

interface CardProps {
  padding?: number;
  radius?: number;
  background?: string;
  border?: string;
  shadow?: string;
  children?: React.ReactNode;
}

export const Card: React.FC<CardProps> = ({
  padding = 16,
  radius = 16,
  background = 'rgba(26, 26, 46, 0.8)',
  border = '1px solid rgba(255, 255, 255, 0.06)',
  shadow = '0 8px 32px 0 rgba(0, 0, 0, 0.37)',
  children,
}) => {
  const style: React.CSSProperties = {
    padding: `${padding}px`,
    borderRadius: `${radius}px`,
    background,
    border,
    boxShadow: shadow,
    backdropFilter: 'blur(12px)',
    WebkitBackdropFilter: 'blur(12px)',
    width: '100%',
    boxSizing: 'border-box',
    color: '#e8e8f0',
  };

  return <div style={style}>{children}</div>;
};
