import React from 'react';
import { Flex } from 'antd';
import { ConfigProvider } from 'antd';
import { useModel } from 'umi';
import { toAntdTheme } from '@/themes';
import type { LantingToken } from '@/themes';
import CollapsibleSplitter from '@/components/CollapsibleSplitter';
import TopBar from './panels/TopBar';
import LeftSidebar from './leftSidebar/LeftSidebar';
import ProjectPanel from './panels/ProjectPanel';
import EditorPanel from './panels/EditorPanel';
import ConfigPanel from './panels/ConfigPanel';
import TerminalPanel from './terminal/TerminalPanel';
import RightSidebar from './rightSidebar/RightSidebar';
import StatusBar from './status/StatusBar';

const EditorPage: React.FC = () => {
  const token = useModel('theme') as LantingToken;
  const { leftTop, right, leftBottom, toggleLeftTop, toggleRight, toggleLeftBottom } = useModel('editorPanels');

  return (
    <ConfigProvider theme={toAntdTheme(token)}>
      <Flex
        vertical
        style={{
          height: '100%',
          background: token.colorBgSubtle,
          overflow: 'auto',
        }}
      >
        {/* 顶部栏 */}
        <TopBar />

        {/* 两侧边栏、底部状态栏、中央主区域 */}
        <Flex flex={1}>
          {/* 左侧边栏 */}
          <LeftSidebar
            activeTop={leftTop}
            activeBottom={leftBottom}
            onToggleTop={toggleLeftTop}
            onToggleBottom={toggleLeftBottom}
          />

          {/* 中央主区域 */}
          <CollapsibleSplitter vertical style={{ flex: 1 }}>
            {/* 项目区、编辑区、配置区 */}
            <CollapsibleSplitter.Panel panelKey="main" defaultSize="auto" min={24}>
              <CollapsibleSplitter>
                {/* 项目区 */}
                <CollapsibleSplitter.Panel
                  panelKey="project"
                  collapsed={!leftTop}
                  defaultSize="16%"
                  min={24}
                  style={{ paddingRight: token.sizeXS, paddingBottom: token.sizeXS }}
                >
                  <ProjectPanel active={leftTop} />
                </CollapsibleSplitter.Panel>

                {/* 编辑区 */}
                <CollapsibleSplitter.Panel
                  panelKey="editor"
                  defaultSize="auto"
                  style={{ paddingLeft: token.sizeXS, paddingRight: token.sizeXS, paddingBottom: token.sizeXS }}
                >
                  <EditorPanel />
                </CollapsibleSplitter.Panel>

                {/* 配置区 */}
                <CollapsibleSplitter.Panel
                  panelKey="config"
                  collapsed={!right}
                  defaultSize={240}
                  min={24}
                  style={{ paddingLeft: token.sizeXS, paddingBottom: token.sizeXS }}
                >
                  <ConfigPanel active={right} />
                </CollapsibleSplitter.Panel>
              </CollapsibleSplitter>
            </CollapsibleSplitter.Panel>

            {/* 终端区 */}
            <CollapsibleSplitter.Panel
              panelKey="terminal"
              collapsed={!leftBottom}
              defaultSize="24%"
              min={24}
              style={{ paddingTop: token.sizeXS }}
            >
              <TerminalPanel active={leftBottom} />
            </CollapsibleSplitter.Panel>
          </CollapsibleSplitter>

          {/* 右侧边栏 */}
          <RightSidebar active={right} onToggle={toggleRight} />
        </Flex>

        {/* 底部状态栏 */}
        <StatusBar />

      </Flex>
    </ConfigProvider>
  );
};

export default EditorPage;