import React from 'react';

interface ImageProps {
  src: string;
  width?: number | string;
  height?: number | string;
  fit?: 'cover' | 'contain' | 'fill';
  radius?: number;
}

export const Image: React.FC<ImageProps> = ({
  src,
  width = '100%',
  height = 'auto',
  fit = 'cover',
  radius = 0,
}) => {
  const style: React.CSSProperties = {
    src,
    width: typeof width === 'number' ? `${width}px` : width,
    height: typeof height === 'number' ? `${height}px` : height,
    objectFit: fit,
    borderRadius: radius ? `${radius}px` : undefined,
    display: 'block',
    boxSizing: 'border-box',
  };

  return <img src={src} alt="" style={style} />;
};
