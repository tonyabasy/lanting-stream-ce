import { Tabs } from 'antd';
import type { FileTreeNode } from '@/types/file';
import './index.css';

export interface FileTabsProps {
  tabs: FileTreeNode[];
  activeTabId: number | null;
  dirtyFlags: Record<number, boolean>;
  onSwitch: (fileId: number) => void;
  onClose: (fileId: number) => void;
}

const FileTabs: React.FC<FileTabsProps> = ({ tabs, activeTabId, dirtyFlags, onSwitch, onClose }) => {
  if (tabs.length === 0) return null;

  return (
    <Tabs
      type="editable-card"
      hideAdd
      size="small"
      activeKey={activeTabId !== null ? String(activeTabId) : undefined}
      onChange={(key) => onSwitch(Number(key))}
      onEdit={(key, action) => {
        if (action === 'remove') onClose(Number(key));
      }}
      items={tabs.map((tab) => {
        const isDirty = dirtyFlags[tab.fileId];
        return {
          key: String(tab.fileId),
          label: (
            <span className="lt-editor-tab-label">
              <span>{tab.name}</span>
              {isDirty && <span className="lt-editor-tab-dirty" />}
            </span>
          ),
          closable: true,
        };
      })}
      classNames={{
        root: 'lt-editor-tab',
        header: 'lt-editor-tab-header',
        item: 'lt-editor-tab-item',
      }}
    />
  );
};

export default FileTabs;
