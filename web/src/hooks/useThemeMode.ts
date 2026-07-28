import { useState, useMemo } from 'react';
import { theme } from 'antd';
import type { ThemeConfig } from 'antd';

export type ThemeMode = 'light' | 'dark';

const THEME_CONFIGS: Record<ThemeMode, ThemeConfig> = {
  light: {
    cssVar: {},
    algorithm: theme.defaultAlgorithm,
    token: {
      colorPrimary: '#1677ff',
      fontFamilyCode: "'SF Mono', Consolas, 'Liberation Mono', Menlo, monospace",
    },
  },
  dark: {
    cssVar: {},
    algorithm: theme.darkAlgorithm,
    token: {
      colorPrimary: '#1677ff',
      fontFamilyCode: "'SF Mono', Consolas, 'Liberation Mono', Menlo, monospace",
    },
  },
};

export function useThemeMode() {
  const [mode, setMode] = useState<ThemeMode>('light');

  const themeConfig = useMemo(() => THEME_CONFIGS[mode], [mode]);

  return { mode, themeConfig, setMode };
}
