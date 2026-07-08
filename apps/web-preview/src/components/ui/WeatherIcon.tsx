import React from 'react';

interface WeatherIconProps {
  code: number;
  size?: number;
  isDay?: boolean;
}

export const WeatherIcon: React.FC<WeatherIconProps> = ({
  code,
  size = 24,
  isDay = true,
}) => {
  const getIconAndLabel = (c: number): { icon: string; label: string } => {
    switch (c) {
      case 0:
        return { icon: isDay ? '☀️' : '🌙', label: 'Clear sky' };
      case 1:
        return { icon: isDay ? '🌤️' : '🌙', label: 'Mainly clear' };
      case 2:
        return { icon: isDay ? '⛅' : '☁️', label: 'Partly cloudy' };
      case 3:
        return { icon: '☁️', label: 'Overcast' };
      case 45:
      case 48:
        return { icon: '🌫️', label: 'Fog' };
      case 51:
      case 53:
      case 55:
        return { icon: '🌦️', label: 'Drizzle' };
      case 56:
      case 57:
        return { icon: '🌨️', label: 'Freezing drizzle' };
      case 61:
      case 63:
      case 65:
        return { icon: '🌧️', label: 'Rain' };
      case 66:
      case 67:
        return { icon: '🌨️', label: 'Freezing rain' };
      case 71:
      case 73:
      case 75:
        return { icon: '❄️', label: 'Snow fall' };
      case 77:
        return { icon: '❄️', label: 'Snow grains' };
      case 80:
      case 81:
      case 82:
        return { icon: '🌧️', label: 'Rain showers' };
      case 85:
      case 86:
        return { icon: '🌨️', label: 'Snow showers' };
      case 95:
        return { icon: '⛈️', label: 'Thunderstorm' };
      case 96:
      case 99:
        return { icon: '⛈️', label: 'Thunderstorm with hail' };
      default:
        return { icon: '🌡️', label: 'Weather' };
    }
  };

  const { icon, label } = getIconAndLabel(Number(code));

  const style: React.CSSProperties = {
    fontSize: `${size}px`,
    lineHeight: 1,
    display: 'inline-block',
    userSelect: 'none',
  };

  return (
    <span style={style} title={label} role="img" aria-label={label}>
      {icon}
    </span>
  );
};
