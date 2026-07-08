import React from 'react';

interface TextProps {
  content: string | number | boolean;
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | '2xl' | '3xl';
  weight?: 'normal' | 'medium' | 'semibold' | 'bold';
  color?: string;
  align?: 'left' | 'center' | 'right';
  opacity?: number;
}

export const Text: React.FC<TextProps> = ({
  content,
  size = 'md',
  weight = 'normal',
  color = '#e8e8f0',
  align = 'left',
  opacity = 1.0,
}) => {
  const sizeMap = {
    xs: '11px',
    sm: '13px',
    md: '15px',
    lg: '18px',
    xl: '21px',
    '2xl': '24px',
    '3xl': '32px',
  };

  const weightMap = {
    normal: 400,
    medium: 500,
    semibold: 600,
    bold: 700,
  };

  const style: React.CSSProperties = {
    fontSize: sizeMap[size] || '15px',
    fontWeight: weightMap[weight] || 400,
    color,
    textAlign: align,
    opacity,
    margin: 0,
    lineHeight: 1.4,
    fontFamily: "'Inter', sans-serif",
  };

  return <p style={style}>{String(content)}</p>;
};
