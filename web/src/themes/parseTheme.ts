/**
 * 主题 Token 模型 —— 全局唯一样式数据源。
 * 所有组件通过 useModel('theme') 获取 token，禁止 hardcode 样式值。
 */
import type { ThemeConfig } from 'antd';

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

  // 间距（只允许这八个值）
  spacingXS:  number;  // 4
  spacingSM:  number;  // 8
  spacingMD:  number;  // 12
  spacingLG:  number;  // 16
  spacingXL:  number;  // 20
  spacing2XL: number;  // 24
  spacing3XL: number;  // 32
  spacing4XL: number;  // 48

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