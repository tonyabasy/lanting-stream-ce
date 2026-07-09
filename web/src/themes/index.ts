/**
 * 主题 Token 模型 —— 全局唯一样式数据源。
 * 所有组件通过 useModel('theme') 获取 token，禁止 hardcode 样式值。
 */
import type { ThemeConfig } from 'antd';
import defaultRaw from './theme-default-light.json';

export type RawTheme = typeof import('@/themes/theme-default-light.json');

export interface LantingToken {
  // 品牌色
  colorPrimary:       string;
  colorPrimaryHover:  string;
  colorPrimaryActive: string;
  colorPrimaryBg:     string;
  colorPrimaryBorder: string;
  colorLink:          string;
  colorLinkHover:     string;

  // 背景
  colorBgLayout:    string;
  colorBgContainer: string;
  colorBgElevated:  string;
  colorBgSubtle:    string;
  colorBgMuted:     string;
  colorBgActive:    string;

  // 边框
  colorBorder:          string;
  colorBorderSecondary: string;
  colorSeparator:       string;

  // 文字
  colorText:            string;
  colorTextSecondary:   string;
  colorTextDescription: string;
  colorTextDisabled:    string;
  colorTextLightSolid:  string;

  // 语义色
  colorSuccess:   string;
  colorSuccessBg: string;
  colorWarning:   string;
  colorWarningBg: string;
  colorError:     string;
  colorErrorBg:   string;

  // 字体
  fontFamily:     string;
  fontFamilyCode: string;

  // 字号（只允许这四个值）
  fontSizeCaption: number;  // 12
  fontSizeBody:    number;  // 14
  fontSizeTitle:   number;  // 16
  fontSizeHeading: number;  // 20

  // 字重（只允许这两个值）
  fontWeightRegular: number;  // 400
  fontWeightMedium:  number;  // 500

  // 圆角
  borderRadiusSM: number;  // 4
  borderRadius:   number;  // 6
  borderRadiusMD: number;  // 8
  borderRadiusLG: number;  // 12
  borderRadiusXL: number;  // 16

  // 尺寸（只允许这八个值）
  sizeXS:  number;  // 4
  sizeSM:  number;  // 8
  sizeMD:  number;  // 12
  sizeLG:  number;  // 16
  sizeXL:  number;  // 20
  size2XL: number;  // 24
  size3XL: number;  // 32
  size4XL: number;  // 48

  // 阴影
  boxShadow:          string;
  boxShadowSecondary: string;
  boxShadowCard:      string;
}

// 嵌套 JSON → 扁平 LantingToken
export function flattenTheme(raw: RawTheme): LantingToken {
  return {
    ...raw.brand,
    ...raw.background,
    ...raw.border,
    ...raw.text,
    ...raw.semantic,
    ...raw.font,
    ...raw.fontSize,
    ...raw.fontWeight,
    ...raw.borderRadius,
    ...raw.spacing,
    ...raw.boxShadow,
  } as LantingToken;
}

// 将 LantingToken 注入为 CSS 自定义属性
export function injectCSSVars(t: LantingToken): void {
  const root = document.documentElement;
  const px = (val: number) => `${val}px`;
  const set = (name: string, val: string) =>
    root.style.setProperty(name, val);

  // ── 品牌色 ──
  set('--lt-color-primary',        t.colorPrimary);
  set('--lt-color-primary-hover',  t.colorPrimaryHover);
  set('--lt-color-primary-active', t.colorPrimaryActive);
  set('--lt-color-primary-bg',     t.colorPrimaryBg);
  set('--lt-color-primary-border', t.colorPrimaryBorder);
  set('--lt-color-link',           t.colorLink);
  set('--lt-color-link-hover',     t.colorLinkHover);

  // ── 背景 ──
  set('--lt-color-bg-layout',    t.colorBgLayout);
  set('--lt-color-bg-container', t.colorBgContainer);
  set('--lt-color-bg-elevated',  t.colorBgElevated);
  set('--lt-color-bg-subtle',    t.colorBgSubtle);
  set('--lt-color-bg-muted',     t.colorBgMuted);
  set('--lt-color-bg-active',    t.colorBgActive);

  // ── 边框 ──
  set('--lt-color-border',           t.colorBorder);
  set('--lt-color-border-secondary', t.colorBorderSecondary);
  set('--lt-color-separator',        t.colorSeparator);

  // ── 文字 ──
  set('--lt-color-text',             t.colorText);
  set('--lt-color-text-secondary',   t.colorTextSecondary);
  set('--lt-color-text-description', t.colorTextDescription);
  set('--lt-color-text-disabled',    t.colorTextDisabled);
  set('--lt-color-text-solid',       t.colorTextLightSolid);

  // ── 语义色 ──
  set('--lt-color-success',    t.colorSuccess);
  set('--lt-color-success-bg', t.colorSuccessBg);
  set('--lt-color-warning',    t.colorWarning);
  set('--lt-color-warning-bg', t.colorWarningBg);
  set('--lt-color-error',      t.colorError);
  set('--lt-color-error-bg',   t.colorErrorBg);

  // ── 字体 ──
  set('--lt-font-family',      t.fontFamily);
  set('--lt-font-family-code', t.fontFamilyCode);

  // ── 字号 ──
  set('--lt-font-size-caption', px(t.fontSizeCaption));
  set('--lt-font-size-body',    px(t.fontSizeBody));
  set('--lt-font-size-title',   px(t.fontSizeTitle));
  set('--lt-font-size-heading', px(t.fontSizeHeading));

  // ── 字重 ──
  set('--lt-font-weight-regular', String(t.fontWeightRegular));
  set('--lt-font-weight-medium',  String(t.fontWeightMedium));

  // ── 圆角 ──
  set('--lt-radius-sm', px(t.borderRadiusSM));
  set('--lt-radius',    px(t.borderRadius));
  set('--lt-radius-md', px(t.borderRadiusMD));
  set('--lt-radius-lg', px(t.borderRadiusLG));
  set('--lt-radius-xl', px(t.borderRadiusXL));

  // ── 尺寸基准 ──
  set('--lt-size-xs',  px(t.sizeXS));
  set('--lt-size-sm',  px(t.sizeSM));
  set('--lt-size-md',  px(t.sizeMD));
  set('--lt-size-lg',  px(t.sizeLG));
  set('--lt-size-xl',  px(t.sizeXL));
  set('--lt-size-2xl', px(t.size2XL));
  set('--lt-size-3xl', px(t.size3XL));
  set('--lt-size-4xl', px(t.size4XL));

  // ── 阴影 ──
  set('--lt-shadow',      t.boxShadow);
  set('--lt-shadow-lg',   t.boxShadowSecondary);
  set('--lt-shadow-card', t.boxShadowCard);
}

// LantingToken → antd ThemeConfig
export function toAntdTheme(t: LantingToken): ThemeConfig {
  return {
    token: {
      colorPrimary:         t.colorPrimary,
      colorLink:            t.colorLink,
      colorLinkHover:       t.colorLinkHover,
      colorSuccess:         t.colorSuccess,
      colorWarning:         t.colorWarning,
      colorError:           t.colorError,
      colorBgLayout:        t.colorBgLayout,
      colorBgContainer:     t.colorBgContainer,
      colorBgElevated:      t.colorBgElevated,
      colorText:            t.colorText,
      colorTextSecondary:   t.colorTextSecondary,
      colorTextDescription: t.colorTextDescription,
      colorTextDisabled:    t.colorTextDisabled,
      colorBorder:          t.colorBorder,
      colorBorderSecondary: t.colorBorderSecondary,
      fontFamily:           t.fontFamily,
      fontFamilyCode:       t.fontFamilyCode,
      fontSize:             t.fontSizeBody,
      borderRadius:         t.borderRadius,
      borderRadiusSM:       t.borderRadiusSM,
      borderRadiusLG:       t.borderRadiusLG,
      boxShadow:            t.boxShadow,
      boxShadowSecondary:   t.boxShadowSecondary,
    },
    components: {
      Button: {
        primaryShadow: 'none',
        defaultShadow: 'none',
        dangerShadow:  'none',
      },
      Input: {
        activeBorderColor: t.colorPrimary,
        hoverBorderColor:  t.colorBorderSecondary,
      },
      Select: {
        borderRadius: t.borderRadius,
      },
      Table: {
        headerBg:    t.colorBgSubtle,
        rowHoverBg:  t.colorPrimaryBg,
        borderColor: t.colorBorder,
      },
      Menu: {
        itemSelectedBg:    t.colorPrimaryBg,
        itemSelectedColor: t.colorPrimary,
        itemHoverBg:       t.colorBgSubtle,
        itemBorderRadius:  t.borderRadius,
      },
      Modal: {
        borderRadiusLG: t.borderRadiusLG,
      },
      Card: {
        borderRadius: t.borderRadiusLG,
      },
    },
  };
}

// ─── 模块加载时注入默认值 ──────────────────────────────

export const DEFAULT_TOKEN: LantingToken = flattenTheme(defaultRaw as RawTheme);
injectCSSVars(DEFAULT_TOKEN);