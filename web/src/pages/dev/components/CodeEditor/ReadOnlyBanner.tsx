import { Button, message } from 'antd';
import { IconLock } from '@tabler/icons-react';
import type { FileTreeNode } from '@/types/file';

interface ReadOnlyBannerProps {
  activeTab: FileTreeNode;
  acquireLock: (fileId: number, path: string) => Promise<void>;
}

const ReadOnlyBanner: React.FC<ReadOnlyBannerProps> = ({ activeTab, acquireLock }) => (
  <div className="lt-editor-readonly-banner">
    <IconLock size={12} />
    <span>只读模式{activeTab.lockedBy ? ` · ${activeTab.lockedBy} 正在编辑` : ''}</span>
    <Button
      type="link"
      size="small"
      onClick={async () => {
        try {
          await acquireLock(activeTab.fileId, activeTab.path);
          message.success('抢锁成功，现在可以编辑了');
        } catch (err: any) {
          message.error(err?.message || '抢锁失败');
        }
      }}
    >
      抢锁
    </Button>
  </div>
);

export default ReadOnlyBanner;
