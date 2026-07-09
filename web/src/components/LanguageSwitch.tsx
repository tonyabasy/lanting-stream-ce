import React from 'react';
import { setLocale, useIntl } from 'umi';
import type { LantingToken } from '@/themes';

interface LanguageSwitchProps {
  token: LantingToken;
}

const languages = [
  { label: '中', value: 'zh-CN' },
  { label: 'En', value: 'en-US' },
];

const LanguageSwitch: React.FC<LanguageSwitchProps> = ({ token }) => {
  const { locale } = useIntl();

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: token.spacingXS,
        fontSize: token.fontSizeCaption,
      }}
    >
      {languages.map(({ label, value }) => {
        const active = locale === value;
        return (
          <button
            key={value}
            type="button"
            onClick={() => setLocale(value, false)}
            style={{
              padding: `${token.spacingXS}px ${token.spacingSM}px`,
              borderRadius: token.borderRadius,
              border: 'none',
              background: active ? token.colorPrimary : 'transparent',
              color: active ? token.colorTextLightSolid : token.colorTextSecondary,
              cursor: 'pointer',
              fontSize: token.fontSizeCaption,
              lineHeight: 1,
              transition: 'all 0.2s',
            }}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
};

export default LanguageSwitch;
