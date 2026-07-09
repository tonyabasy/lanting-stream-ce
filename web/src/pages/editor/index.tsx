import React from 'react';
import { Flex } from 'antd';
import { ConfigProvider } from 'antd';
import { useModel } from 'umi';
import { toAntdTheme } from '@/themes';
import type { LantingToken } from '@/themes';
import CollapsibleSplitter from '@/components/CollapsibleSplitter';
import TopBar from './components/TopBar';
import LeftSidebar from './components/LeftSidebar';
import ProjectPanel from './components/ProjectPanel';
import EditorPanel from './components/EditorPanel';
import ConfigPanel from './components/ConfigPanel';
import TerminalPanel from './components/TerminalPanel';
import RightSidebar from './components/RightSidebar';
import StatusBar from './components/StatusBar';
import { useEditorPanels } from './hooks/useEditorPanels';

const EditorPage: React.FC = () => {
  const token = useModel('theme') as LantingToken;
  const { leftTop, right, leftBottom, toggleLeftTop, toggleRight, toggleLeftBottom } = useEditorPanels();

  return (
    <ConfigProvider theme={toAntdTheme(token)}>
      <Flex
        vertical
        style={{
          height: '100%',
          background: token.colorBgLayout,
          overflow: 'auto',
        }}
      >
        <TopBar />

        <Flex flex={1}>
          <LeftSidebar
            activeTop={leftTop}
            activeBottom={leftBottom}
            onToggleTop={toggleLeftTop}
            onToggleBottom={toggleLeftBottom}
          />

          <CollapsibleSplitter vertical style={{ flex: 1 }}>
            <CollapsibleSplitter.Panel panelKey="main" defaultSize="auto" min={24}>
              <CollapsibleSplitter style={{ height: '100%' }}>
                <CollapsibleSplitter.Panel
                  panelKey="project"
                  collapsed={!leftTop}
                  defaultSize="20%"
                  min={24}
                  style={{ paddingRight: token.sizeXS, paddingBottom: token.sizeXS }}
                >
                  <ProjectPanel active={leftTop} />
                </CollapsibleSplitter.Panel>

                <CollapsibleSplitter.Panel
                  panelKey="editor"
                  defaultSize="auto"
                  style={{ paddingLeft: token.sizeXS, paddingRight: token.sizeXS, paddingBottom: token.sizeXS }}
                >
                  <EditorPanel />
                </CollapsibleSplitter.Panel>

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

            <CollapsibleSplitter.Panel
              panelKey="terminal"
              collapsed={!leftBottom}
              defaultSize="20%"
              min={24}
              style={{ paddingTop: token.sizeXS }}
            >
              <TerminalPanel active={leftBottom} />
            </CollapsibleSplitter.Panel>
          </CollapsibleSplitter>

          <RightSidebar active={right} onToggle={toggleRight} />
        </Flex>

        <StatusBar />

      </Flex>
    </ConfigProvider>
  );
};

export default EditorPage;