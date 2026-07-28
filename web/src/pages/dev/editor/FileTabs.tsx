import { Tabs } from 'antd';
import { IconLock } from '@tabler/icons-react';
import { useModel } from 'umi';
import type { FileTreeNode } from '../types/file';

export interface FileTabsProps {
  tabs: FileTreeNode[];
  activeTabId: number | null;
  onSwitch: (fileId: number) => void;
  onClose: (fileId: number) => void;
}

const FileTabs: React.FC<FileTabsProps> = ({ tabs, activeTabId, onSwitch, onClose }) => {

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
        return {
          key: String(tab.fileId),
          label: (
            <span className="lt-editor-tab-label">
              <span>{tab.name}</span>
            </span>
          ),
          closable: true,
        };
      })}
      className="lt-editor-tabs"
    />
  );
};

export default FileTabs;
