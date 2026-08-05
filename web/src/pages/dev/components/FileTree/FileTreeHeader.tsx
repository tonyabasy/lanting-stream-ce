import { Button, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import Tooltip from '@/components/Tooltip';
import TablerIcon from '@/components/TablerIcon';
import {
  IconPlus,
  IconFocusCentered,
  IconArrowsMinimize,
  IconLayoutSidebarLeftCollapse,
  IconRefresh,
  IconChevronDown,
  IconFileCode,
  IconFolderPlus,
  IconFolder,
  IconDatabase,
} from '@tabler/icons-react';
import { CTX_ICON_SIZE } from './treeUtils';
import type { FileTreeViewKey } from '@/types/file';
import { FileTreeViews } from '@/models/fileTree';

export interface FileTreeHeaderProps {
  /** 当前视图（下拉显示当前项） */
  view: FileTreeViewKey;
  /** 视图下拉选项（Workspace/Project/Tables，带 icon） */
  viewOptions: MenuProps['items'];
  /** 下拉选中视图 */
  onViewChange: (view: FileTreeViewKey) => void;
  onRefresh: () => void;
  onAddFile: () => void;
  onAddFolder: () => void;
  onCollapseAll: () => void;
  /** 收起整个左栏 */
  onCollapsePanel: () => void;
}

const addMenuItems: MenuProps['items'] = [
  { key: 'file', label: '文件', icon: <TablerIcon icon={IconFileCode} size={CTX_ICON_SIZE} /> },
  { key: 'folder', label: '目录', icon: <TablerIcon icon={IconFolderPlus} size={CTX_ICON_SIZE} /> },
];

/** 由 FileTreeViews 生成下拉菜单项 */
export const buildViewOptions = (): MenuProps['items'] =>
  Object.values(FileTreeViews).map((v) => ({
    key: v.key,
    label: v.title,
  }));

const FileTreeHeader: React.FC<FileTreeHeaderProps> = ({
  view,
  viewOptions,
  onViewChange,
  onRefresh,
  onAddFile,
  onAddFolder,
  onCollapseAll,
  onCollapsePanel,
}) => {
  const handleAddMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'file') onAddFile();
    else if (key === 'folder') onAddFolder();
  };

  const handleViewClick: MenuProps['onClick'] = ({ key }) => {
    onViewChange(key as FileTreeViewKey);
  };

  return (
    <div className="lt-filetree-header">
      {/* 视图下拉（标题） */}
      <Dropdown menu={{ items: viewOptions, onClick: handleViewClick, selectable: true, selectedKeys: [view] }} trigger={['click']}>
        <Button color="default" variant="link" className="lt-filetree-header-title">
          <span>{FileTreeViews[view].title}</span>
          <IconChevronDown size={14} strokeWidth={1.5} />
        </Button>
      </Dropdown>
      <div className={"lt-filetree-header-btn-group"}>
        <Tooltip title="刷新">
          <Button type="text" className="lt-filetree-header-btn" icon={<TablerIcon icon={IconRefresh} size={16} />} onClick={onRefresh} />
        </Tooltip>
        <Dropdown menu={{ items: addMenuItems, onClick: handleAddMenuClick }} trigger={['click']}>
          <Button type="text" className="lt-filetree-header-btn" icon={<TablerIcon icon={IconPlus} size={16} />} />
        </Dropdown>
        <Tooltip title="定位">
          <Button type="text" className="lt-filetree-header-btn" icon={<TablerIcon icon={IconFocusCentered} size={16} />} />
        </Tooltip>
        <Tooltip title="收起全部">
          <Button type="text" className="lt-filetree-header-btn" icon={<TablerIcon icon={IconArrowsMinimize} size={16} />} onClick={onCollapseAll} />
        </Tooltip>
        <Tooltip title="收起面板">
          <Button type="text" className="lt-filetree-header-btn" icon={<TablerIcon icon={IconLayoutSidebarLeftCollapse} size={16} />} onClick={onCollapsePanel} />
        </Tooltip>
      </div>
    </div>
  );
};

export default FileTreeHeader;
