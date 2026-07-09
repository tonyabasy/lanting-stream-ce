import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Splitter } from 'antd';

export interface CollapsiblePanelProps {
  /** 面板唯一标识，用于记忆尺寸 */
  panelKey: string;
  /** 是否折叠（隐藏） */
  collapsed?: boolean;
  /** 初始尺寸；'auto' 表示由剩余空间自动填充 */
  defaultSize?: number | string | 'auto';
  min?: number | string;
  max?: number | string;
  resizable?: boolean;
  className?: string;
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export interface CollapsibleSplitterProps
  extends Omit<React.ComponentProps<typeof Splitter>, 'children' | 'onResizeEnd'> {
  children: React.ReactElement<CollapsiblePanelProps> | React.ReactElement<CollapsiblePanelProps>[];
  onResizeEnd?: (sizes: number[]) => void;
}

type CollapsibleSplitterComponent = React.FC<CollapsibleSplitterProps> & {
  Panel: typeof CollapsiblePanel;
};

/**
 * 可折叠的 Splitter 包装组件。
 *
 * 在 antd Splitter 基础上增加 `collapsed` 能力：
 * - `collapsed=true` 时面板隐藏，上次尺寸会被记忆
 * - `collapsed=false` 时面板恢复，使用记忆尺寸或 defaultSize
 * - 拖拽分隔条后，尺寸会被持久化
 * - `defaultSize="auto"` 的面板会自适应剩余空间
 */
export const CollapsiblePanel: React.FC<CollapsiblePanelProps> = () => null;

export const CollapsibleSplitter: CollapsibleSplitterComponent = ({
  children,
  onResizeEnd,
  ...splitterProps
}) => {
  const panels = useMemo(
    () =>
      React.Children.toArray(children).filter(
        (child): child is React.ReactElement<CollapsiblePanelProps> =>
          React.isValidElement<CollapsiblePanelProps>(child) && child.type === CollapsiblePanel,
      ),
    [children],
  );

  // 记忆每个面板的尺寸
  const [sizeMap, setSizeMap] = useState<Record<string, number | string | 'auto'>>({});

  // 初始化新面板的尺寸
  useEffect(() => {
    setSizeMap((prev) => {
      let changed = false;
      const next = { ...prev };
      panels.forEach((panel) => {
        const { panelKey, defaultSize = 'auto' } = panel.props;
        if (!(panelKey in next)) {
          next[panelKey] = defaultSize;
          changed = true;
        }
      });
      return changed ? next : prev;
    });
  }, [panels]);

  // 过滤掉折叠的面板
  const visiblePanels = useMemo(
    () => panels.filter((panel) => !panel.props.collapsed),
    [panels],
  );

  // 拖拽结束后同步尺寸
  const handleResizeEnd = useCallback(
    (sizes: number[]) => {
      setSizeMap((prev) => {
        const next = { ...prev };
        visiblePanels.forEach((panel, index) => {
          const { panelKey, defaultSize = 'auto' } = panel.props;
          // 只记录非 auto 面板的尺寸；auto 面板由剩余空间自适应
          if (defaultSize !== 'auto') {
            next[panelKey] = sizes[index];
          }
        });
        return next;
      });
      onResizeEnd?.(sizes);
    },
    [visiblePanels, onResizeEnd],
  );

  return (
    <Splitter {...splitterProps} onResizeEnd={handleResizeEnd}>
      {visiblePanels.map((panel) => {
        const {
          panelKey,
          collapsed,
          defaultSize = 'auto',
          children: panelChildren,
          ...restProps
        } = panel.props;

        const size = sizeMap[panelKey];

        return (
          <Splitter.Panel
            key={panelKey}
            {...(size !== 'auto' ? { size } : {})}
            {...restProps}
          >
            {panelChildren}
          </Splitter.Panel>
        );
      })}
    </Splitter>
  );
};

CollapsibleSplitter.Panel = CollapsiblePanel;

export default CollapsibleSplitter;
