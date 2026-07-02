import type { ThemeConfig } from 'antd';

interface LantingToken extends NonNullable<ThemeConfig['token']> {
  colorBgSubtle: string;
  colorBgMuted: string;
  colorNeutral: string;
  fontFamilyCode: string;
}

export interface LantingTheme extends Omit<ThemeConfig, 'token'> {
  key: string;
  name: string;
  token: LantingToken;
}

/** 宝蓝主题 — 唯一主题 */
export const theme: LantingTheme = {
  key: 'baoLan',
  name: '宝蓝',
  token: {
    // 品牌色
    colorPrimary: '#2A5CA0',
    colorPrimaryHover: '#234B85',
    colorPrimaryBg: '#2A5CA014',
    colorPrimaryBorder: '#6089C4',
    colorLink: '#2A5CA0',
    colorLinkHover: '#234B85',

    // 背景
    colorBgLayout: '#F8F9FA',
    colorBgContainer: '#F0F2F5',
    colorBgElevated: '#F0F2F5',
    colorBgSubtle: '#E6E8EB',
    colorBgMuted: '#DCDEE2',

    // 边框
    colorBorder: '#E5E5E5',
    colorBorderSecondary: '#D4D4D4',

    // 文字
    colorText: '#111111',
    colorTextSecondary: '#555555',
    colorTextDescription: '#999999',
    colorTextDisabled: '#CCCCCC',
    colorTextLightSolid: '#fff',

    // 语义色
    colorSuccess: '#2F9A45',
    colorSuccessBg: '#EAF3DE',
    colorWarning: '#A97A1D',
    colorWarningBg: '#FAEEDA',
    colorError: '#9C2E24',
    colorNeutral: '#B4B2A9',

    // 字体
    fontFamily: 'system-ui, -apple-system, sans-serif',
    fontFamilyCode: "ui-monospace, 'SF Mono', Consolas, monospace",

    // 字号 / 圆角 / 阴影
    fontSize: 14,
    borderRadius: 6,
    borderRadiusLG: 12,
    boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
    boxShadowSecondary: '0 8px 32px rgba(0,0,0,0.12)',
  },
  components: {
    Table: { headerBg: '#E6E8EB', rowHoverBg: '#2A5CA014', borderColor: '#E5E5E5' },
    Modal: { borderRadiusLG: 12 },
    Menu: { itemBorderRadius: 6, itemSelectedBg: '#2A5CA014', itemSelectedColor: '#2A5CA0' },
    Input: { borderRadius: 6, activeBorderColor: '#2A5CA0', hoverBorderColor: '#D4D4D4' },
    Button: { borderRadius: 6, primaryShadow: 'none', defaultShadow: 'none' },
    Select: { borderRadius: 6 },
    Tag: { borderRadiusSM: 4 },
  },
};

/** 启动时调用一次：将主题 token 注入为 CSS 变量 */
export function injectCSS(t: LantingToken = theme.token): void {
  const root = document.documentElement;
  const set = (name: string, val: string | number) => root.style.setProperty(name, String(val));
  set('--color-primary', t.colorPrimary!);
  set('--color-primary-hover', t.colorPrimaryHover!);
  set('--color-primary-bg', t.colorPrimaryBg!);
  set('--color-primary-border', t.colorPrimaryBorder!);
  set('--color-bg-layout', t.colorBgLayout!);
  set('--color-bg-container', t.colorBgContainer!);
  set('--color-bg-subtle', t.colorBgSubtle);
  set('--color-bg-muted', t.colorBgMuted);
  set('--color-border', t.colorBorder!);
  set('--color-border-secondary', t.colorBorderSecondary!);
  set('--color-text', t.colorText!);
  set('--color-text-secondary', t.colorTextSecondary!);
  set('--color-text-description', t.colorTextDescription!);
  set('--color-text-disabled', t.colorTextDisabled!);
  set('--color-text-light-solid', t.colorTextLightSolid!);
  set('--color-success', t.colorSuccess!);
  set('--color-success-bg', t.colorSuccessBg!);
  set('--color-warning', t.colorWarning!);
  set('--color-warning-bg', t.colorWarningBg!);
  set('--color-error', t.colorError!);
  set('--color-neutral', t.colorNeutral);
  set('--font-family', t.fontFamily!);
  set('--font-family-code', t.fontFamilyCode);
}
