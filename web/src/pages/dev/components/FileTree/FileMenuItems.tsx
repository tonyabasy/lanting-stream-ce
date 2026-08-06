import type { MenuProps } from 'antd';
import {
  IconFileCode,
  IconFolderPlus,
  IconCursorText,
  IconArrowsMove,
  IconTrash,
} from '@tabler/icons-react';
import TablerIcon from '@/components/TablerIcon';
import { CTX_ICON_SIZE } from './treeUtils';

/** 文件操作菜单 */
export const getFileMenuItems = (): MenuProps['items'] => [
  { key: 'new-file', label: '新建文件', icon: <TablerIcon icon={IconFileCode} size={CTX_ICON_SIZE} /> },
  { key: 'new-folder', label: '新建文件夹', icon: <TablerIcon icon={IconFolderPlus} size={CTX_ICON_SIZE} /> },
  { type: 'divider' },
  { key: 'rename', label: '重命名', icon: <TablerIcon icon={IconCursorText} size={CTX_ICON_SIZE} /> },
  { key: 'move', label: '移动', icon: <TablerIcon icon={IconArrowsMove} size={CTX_ICON_SIZE} /> },
  { type: 'divider' },
  { key: 'delete', label: '删除', icon: <TablerIcon icon={IconTrash} size={CTX_ICON_SIZE} />, danger: true },
];

/** 目录操作菜单 */
export const folderMenuItems: MenuProps['items'] = [
  { key: 'new-file', label: '新建文件', icon: <TablerIcon icon={IconFileCode} size={CTX_ICON_SIZE} /> },
  { key: 'new-folder', label: '新建文件夹', icon: <TablerIcon icon={IconFolderPlus} size={CTX_ICON_SIZE} /> },
  { type: 'divider' },
  { key: 'rename', label: '重命名', icon: <TablerIcon icon={IconCursorText} size={CTX_ICON_SIZE} /> },
  { key: 'move', label: '移动', icon: <TablerIcon icon={IconArrowsMove} size={CTX_ICON_SIZE} /> },
  { type: 'divider' },
  { key: 'delete', label: '删除', icon: <TablerIcon icon={IconTrash} size={CTX_ICON_SIZE} />, danger: true },
];
