import React from 'react';

interface BadgeProps {
  text: string;
  color?: string;
  background?: string;
  size?: 'sm' | 'md';
}

export const Badge: React.FC<BadgeProps> = ({
  text,
  color = '#ffffff',
  background = '#7c3aed',
  size = 'md',
}) => {
  const paddingMap = {
    sm: '2px 8px',
    md: '4px 12px',
  };

  const fontMap = {
    sm: '11px',
    md: '13px',
  };

  const style: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: paddingMap[size] || '4px 12px',
    fontSize: fontMap[size] || '13px',
    fontWeight: 600,
    color,
    background,
    borderRadius: '9999px',
    width: 'fit-content',
    boxSizing: 'border-box',
    fontFamily: "'Inter', sans-serif",
  };

  return <span style={style}>{text}</span>;
};
