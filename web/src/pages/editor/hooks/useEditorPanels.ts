import { useState } from 'react';

type LeftTopTab = 'folder' | 'changes' | null;
type RightTab = 'config' | 'ai' | null;
type LeftBottomTab = 'terminal' | 'git' | null;

export interface EditorPanelState {
  leftTop: LeftTopTab;
  right: RightTab;
  leftBottom: LeftBottomTab;
}

/**
 * 编辑器面板状态管理 Hook。
 * 管理三组侧边栏激活状态：
 * - 左栏上：控制 ProjectPanel 内容
 * - 右栏：控制 ConfigPanel 内容
 * - 左栏下：控制 TerminalPanel 内容
 *
 * 点击已激活图标会关闭对应面板，点击未激活图标会打开并切换内容。
 */
export function useEditorPanels() {
  const [leftTop, setLeftTop] = useState<LeftTopTab>('folder');
  const [right, setRight] = useState<RightTab>('config');
  const [leftBottom, setLeftBottom] = useState<LeftBottomTab>('terminal');

  const toggleLeftTop = (key: NonNullable<LeftTopTab>) => {
    setLeftTop((prev) => (prev === key ? null : key));
  };

  const toggleRight = (key: NonNullable<RightTab>) => {
    setRight((prev) => (prev === key ? null : key));
  };

  const toggleLeftBottom = (key: NonNullable<LeftBottomTab>) => {
    setLeftBottom((prev) => (prev === key ? null : key));
  };

  return {
    leftTop,
    right,
    leftBottom,
    toggleLeftTop,
    toggleRight,
    toggleLeftBottom,
  };
}
