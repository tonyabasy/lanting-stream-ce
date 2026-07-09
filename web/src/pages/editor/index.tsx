import React from 'react';
import { Flex, Splitter } from 'antd';
import { ConfigProvider } from 'antd';
import { useModel } from 'umi';
import { toAntdTheme } from '@/themes';
import type { LantingToken } from '@/themes';
import TopBar from './components/TopBar';
import LeftSidebar from './components/LeftSidebar';
import ProjectPanel from './components/ProjectPanel';
import EditorPanel from './components/EditorPanel';
import ConfigPanel from './components/ConfigPanel';
import TerminalPanel from './/components/TerminalPanel';
import RightSidebar from './components/RightSidebar';
import StatusBar from './components/StatusBar';

const EditorPage: React.FC = () => {
  const t = useModel('theme') as LantingToken;

  return (
    <ConfigProvider theme={toAntdTheme(t)}>
      <Flex
        vertical
        style={{
          height: '100%',
          background: t.colorBgSubtle,
          overflow: 'auto',
        }}
      >
        {/* 顶部工具栏 */}
        <TopBar />

        {/* 中间工作区 */}
        <Flex flex={1}>
          {/* 左侧边栏 */}
          <LeftSidebar />

          {/* Body：ProjectPanel / EditorPanel / ConfigPanel / TerminalPanel */}
          <Splitter
            vertical
            style={{ flex: 1 }}
          >
            {/* 上半区：横向 ProjectPanel / EditorPanel / ConfigPanel */}
            <Splitter.Panel defaultSize="80%" min="4%">
              <Splitter style={{ height: '100%' }}>

                {/* 目录区 */}
                <Splitter.Panel defaultSize="15%" min="2%" style={{ paddingRight: t.spacingXS, paddingBottom: t.spacingXS }}>
                  <ProjectPanel />
                </Splitter.Panel>

                {/* 编辑区 */}
                <Splitter.Panel defaultSize="70%" min="2%" style={{ paddingLeft: t.spacingXS,paddingRight: t.spacingXS, paddingBottom: t.spacingXS }}>
                  <EditorPanel />
                </Splitter.Panel>

                {/* 配置区 */}
                <Splitter.Panel defaultSize="15%" min="2%" style={{ paddingLeft: t.spacingXS, paddingBottom: t.spacingXS }}>
                  <ConfigPanel />
                </Splitter.Panel>

              </Splitter>
            </Splitter.Panel>

            {/* 下半区：终端区 */}
            <Splitter.Panel defaultSize="20%" min="4%" style={{ paddingTop: t.spacingXS }}>
              <TerminalPanel />
            </Splitter.Panel>
          </Splitter>

          {/* 右侧边栏 */}
          <RightSidebar />
        </Flex>

        {/* 底部状态栏 */}
        <StatusBar />
        
      </Flex>
    </ConfigProvider>
  );
};

export default EditorPage;
