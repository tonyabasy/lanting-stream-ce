import { useState, useCallback } from 'react';

export type LeftTopTab = 'files' | 'tables' | 'changes' | null;
export type RightTab = 'config' | 'ai' | null;
export type LeftBottomTab = 'terminal' | 'git' | null;

export type LeftTopKey = NonNullable<LeftTopTab>;
export type RightKey = NonNullable<RightTab>;
export type LeftBottomKey = NonNullable<LeftBottomTab>;

/**
 * 研发面板状态 model。
 *
 * 管理三组侧边栏激活状态：
 * - 左栏上：控制 ProjectPanel 内容
 * - 右栏：控制 ConfigPanel 内容
 * - 左栏下：控制 TerminalPanel 内容
 *
 * 点击已激活图标会关闭对应面板，点击未激活图标会打开并切换内容。
 */
export default () => {
  const [leftTop, setLeftTop] = useState<LeftTopTab>('files');
  const [right, setRight] = useState<RightTab>(null);
  const [leftBottom, setLeftBottom] = useState<LeftBottomTab>(null);

  const toggleLeftTop = useCallback((key: LeftTopKey) => {
    setLeftTop((prev) => (prev === key ? null : key));
  }, []);

  const toggleRight = useCallback((key: RightKey) => {
    setRight((prev) => (prev === key ? null : key));
  }, []);

  const toggleLeftBottom = useCallback((key: LeftBottomKey) => {
    setLeftBottom((prev) => (prev === key ? null : key));
  }, []);

  return {
    leftTop,
    right,
    leftBottom,
    toggleLeftTop,
    toggleRight,
    toggleLeftBottom,
  };
};
