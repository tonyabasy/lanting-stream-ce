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
      <span className="lt-filetree-title">SQL 文件</span>
      <Tooltip title="刷新">
        <Button type="text" className="lt-filetree-hbtn" icon={<IconRefresh size={13} />} onClick={onRefresh} />
      </Tooltip>
      <Dropdown menu={{ items: addMenuItems, onClick: handleAddMenuClick }} trigger={['click']}>
        <Button type="text" className="lt-filetree-hbtn" icon={<IconPlus size={13} />} />
      </Dropdown>
      <Tooltip title="定位">
        <Button type="text" className="lt-filetree-hbtn" icon={<IconFocusCentered size={13} />} />
      </Tooltip>
      <Tooltip title="收起全部">
        <Button type="text" className="lt-filetree-hbtn" icon={<IconArrowsMinimize size={13} />} onClick={onCollapseAll} />
      </Tooltip>
      <Tooltip title="收起面板">
        <Button type="text" className="lt-filetree-hbtn" icon={<IconLayoutSidebarLeftCollapse size={13} />} onClick={onCollapsePanel} />
      </Tooltip>
    </div>
  );
};

export default FileTreeHeader;
