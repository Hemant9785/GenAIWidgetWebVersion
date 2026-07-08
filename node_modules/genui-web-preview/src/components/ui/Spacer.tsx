import React from 'react';

interface SpacerProps {
  size?: number;
}

export const Spacer: React.FC<SpacerProps> = ({ size = 16 }) => {
  return <div style={{ width: `${size}px`, height: `${size}px`, flexShrink: 0 }} />;
};
