import React from 'react';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';

/**
 * 运行时 ConfigProvider 兜底。
 * 部分组件无法被 .umirc.ts 的构建时 token 完全覆盖，需要运行时注入。
 */
export function rootContainer(container: React.ReactNode) {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#D97757',
          colorBgContainer: '#FAF9F6',
          colorText: '#1A1915',
          borderRadius: 6,
          fontFamily: 'system-ui, -apple-system, sans-serif',
        },
      }}
    >
      {container}
    </ConfigProvider>
  );
}
