import type { MenuProps } from 'antd';
import {
  IconLock,
  IconFileCode,
  IconFolderPlus,
  IconCursorText,
  IconArrowsMove,
  IconTrash,
  IconLockOpen2,
} from '@tabler/icons-react';
import { CTX_ICON_SIZE } from './treeUtils';

/** 文件操作菜单（根据是否自己持锁切换「抢锁/释放锁」） */
export const getFileMenuItems = (isMyLock: boolean): MenuProps['items'] => [
  isMyLock
    ? { key: 'unlock', label: '释放锁', icon: <IconLockOpen2 size={CTX_ICON_SIZE} /> }
    : { key: 'lock', label: '抢锁', icon: <IconLock size={CTX_ICON_SIZE} /> },
  { type: 'divider' },
  { key: 'new-file', label: '新建文件', icon: <IconFileCode size={CTX_ICON_SIZE} /> },
  { key: 'new-folder', label: '新建文件夹', icon: <IconFolderPlus size={CTX_ICON_SIZE} /> },
  { type: 'divider' },
  { key: 'rename', label: '重命名', icon: <IconCursorText size={CTX_ICON_SIZE} /> },
  { key: 'move', label: '移动', icon: <IconArrowsMove size={CTX_ICON_SIZE} /> },
  { type: 'divider' },
  { key: 'delete', label: '删除', icon: <IconTrash size={CTX_ICON_SIZE} />, danger: true },
];

/** 目录操作菜单 */
export const folderMenuItems: MenuProps['items'] = [
  { key: 'new-file', label: '新建文件', icon: <IconFileCode size={CTX_ICON_SIZE} /> },
  { key: 'new-folder', label: '新建文件夹', icon: <IconFolderPlus size={CTX_ICON_SIZE} /> },
  { type: 'divider' },
  { key: 'rename', label: '重命名', icon: <IconCursorText size={CTX_ICON_SIZE} /> },
  { key: 'move', label: '移动', icon: <IconArrowsMove size={CTX_ICON_SIZE} /> },
  { type: 'divider' },
  { key: 'delete', label: '删除', icon: <IconTrash size={CTX_ICON_SIZE} />, danger: true },
];
