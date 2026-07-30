import { Button, Tooltip, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import {
  IconPlus,
  IconFocusCentered,
  IconArrowsMinimize,
  IconLayoutSidebarLeftCollapse,
  IconRefresh,
  IconFileCode,
  IconFolderPlus,
} from '@tabler/icons-react';
import { CTX_ICON_SIZE } from './treeUtils';

export interface FileTreeHeaderProps {
  onRefresh: () => void;
  onAddFile: () => void;
  onAddFolder: () => void;
  onCollapseAll: () => void;
  onCollapsePanel: () => void;
}

const addMenuItems: MenuProps['items'] = [
  { key: 'file', label: '文件', icon: <IconFileCode size={CTX_ICON_SIZE} /> },
  { key: 'folder', label: '目录', icon: <IconFolderPlus size={CTX_ICON_SIZE} /> },
];

const FileTreeHeader: React.FC<FileTreeHeaderProps> = ({
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

  return (
    <div className="lt-filetree-header">
      {/* 目录快捷操作按钮 */}
      <span className="lt-filetree-header-title">Project</span>
      <Tooltip title="刷新">
        <Button type="text" className="lt-filetree-header-btn" icon={<IconRefresh size={16} strokeWidth={1.5} />} onClick={onRefresh} />
      </Tooltip>
      <Dropdown menu={{ items: addMenuItems, onClick: handleAddMenuClick }} trigger={['click']}>
        <Button type="text" className="lt-filetree-header-btn" icon={<IconPlus size={16} strokeWidth={1.5} />} />
      </Dropdown>
      <Tooltip title="定位">
        <Button type="text" className="lt-filetree-header-btn" icon={<IconFocusCentered size={16} strokeWidth={1.5} />} />
      </Tooltip>
      <Tooltip title="收起全部">
        <Button type="text" className="lt-filetree-header-btn" icon={<IconArrowsMinimize size={16} strokeWidth={1.5} />} onClick={onCollapseAll} />
      </Tooltip>
      <Tooltip title="收起面板">
        <Button type="text" className="lt-filetree-header-btn" icon={<IconLayoutSidebarLeftCollapse size={16} strokeWidth={1.5} />} onClick={onCollapsePanel} />
      </Tooltip>
    </div>
  );
};

export default FileTreeHeader;
