import React from 'react';
import { Flex, theme } from 'antd';
import { useModel } from 'umi';
import CollapsibleSplitter from '@/components/CollapsibleSplitter';
import TopBar from './panels/TopBar';
import LeftSidebar from './components/leftSidebar';
import ProjectPanel from './panels/ProjectPanel';
import EditorPanel from './panels/EditorPanel';
import ConfigPanel from './panels/ConfigPanel';
import TerminalPanel from './panels/TerminalPanel';
import RightSidebar from './components/rightSidebar';
import StatusBar from './components/status';

const DevPage: React.FC = () => {
  const { token } = theme.useToken();
  const { leftTop, right, leftBottom, toggleLeftTop, toggleRight, toggleLeftBottom } = useModel('devPanels');

  return (
      <Flex
        vertical
        style={{
          height: '100%',
          background: token.colorBgLayout,
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
                  style={{ paddingRight: token.sizeXXS, paddingBottom: token.sizeXXS }}
                >
                  {leftTop && <ProjectPanel active={leftTop} />}
                </CollapsibleSplitter.Panel>

                {/* 编辑区 */}
                <CollapsibleSplitter.Panel
                  panelKey="editor"
                  defaultSize="auto"
                  style={{ paddingLeft: token.sizeXXS, paddingRight: token.sizeXXS, paddingBottom: token.sizeXXS }}
                >
                  <EditorPanel />
                </CollapsibleSplitter.Panel>

                {/* 配置区 */}
                <CollapsibleSplitter.Panel
                  panelKey="config"
                  collapsed={!right}
                  defaultSize={240}
                  min={24}
                  style={{ paddingLeft: token.sizeXXS, paddingBottom: token.sizeXXS }}
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
              style={{ paddingTop: token.sizeXXS }}
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
  );
};

export default DevPage;