import React from 'react';
import { setLocale, useIntl } from 'umi';
import { theme } from 'antd';

const languages = [
  { label: '中', value: 'zh-CN' },
  { label: 'En', value: 'en-US' },
];

const LanguageSwitch: React.FC = () => {
  const { locale } = useIntl();
  const { token } = theme.useToken();

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: token.sizeXXS,
        fontSize: token.fontSizeSM,
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
              padding: `${token.sizeXXS}px ${token.sizeXS}px`,
              borderRadius: token.borderRadius,
              border: 'none',
              background: active ? token.colorPrimary : 'transparent',
              color: active ? token.colorTextLightSolid : token.colorTextSecondary,
              cursor: 'pointer',
              fontSize: token.fontSizeSM,
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
