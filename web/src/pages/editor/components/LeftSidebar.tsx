import { IconFolder, IconGitCommit, IconTerminal2, IconGitBranch } from '@tabler/icons-react';
import { Button, Tooltip } from 'antd';
import '../index.css';

interface LeftSidebarProps {
  activeTop: 'folder' | 'changes' | null;
  activeBottom: 'terminal' | 'git' | null;
  onToggleTop: (key: 'folder' | 'changes') => void;
  onToggleBottom: (key: 'terminal' | 'git') => void;
}

const LeftSidebar: React.FC<LeftSidebarProps> = ({
  activeTop,
  activeBottom,
  onToggleTop,
  onToggleBottom,
}) => (
  <div className="lt-sidebar">
    <div className="lt-sidebar-group">
      <Tooltip title="项目" placement="right">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={activeTop === 'folder'}
          icon={<IconFolder className="lt-sidebar-icon" />}
          onClick={() => onToggleTop('folder')}
        />
      </Tooltip>
      <Tooltip title="变更" placement="right">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={activeTop === 'changes'}
          icon={<IconGitCommit className="lt-sidebar-icon" />}
          onClick={() => onToggleTop('changes')}
        />
      </Tooltip>
    </div>

    <div className="lt-sidebar-spacer" />

    <div className="lt-sidebar-group">
      <Tooltip title="终端" placement="right">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={activeBottom === 'terminal'}
          icon={<IconTerminal2 className="lt-sidebar-icon" />}
          onClick={() => onToggleBottom('terminal')}
        />
      </Tooltip>
      <Tooltip title="Git" placement="right">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={activeBottom === 'git'}
          icon={<IconGitBranch className="lt-sidebar-icon" />}
          onClick={() => onToggleBottom('git')}
        />
      </Tooltip>
    </div>
  </div>
);

export default LeftSidebar;
