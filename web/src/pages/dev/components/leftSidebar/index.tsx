import { IconFolder, IconGitCommit, IconTerminal2, IconGitBranch } from '@tabler/icons-react';
import { Button } from 'antd';
import Tooltip from '@/components/Tooltip';
import TablerIcon from '@/components/TablerIcon';
import type { LeftTopKey, LeftBottomKey, LeftTopTab, LeftBottomTab } from '@/models/devPanels';
import '../../index.css';

interface LeftSidebarProps {
  activeTop: LeftTopTab;
  activeBottom: LeftBottomTab;
  onToggleTop: (key: LeftTopKey) => void;
  onToggleBottom: (key: LeftBottomKey) => void;
}

const LeftSidebar: React.FC<LeftSidebarProps> = ({
  activeTop,
  activeBottom,
  onToggleTop,
  onToggleBottom,
}) => (
  <div className="lt-sidebar">
    <div className="lt-sidebar-group">
      <Tooltip title="Project" placement="right">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={activeTop === 'files'}
          icon={<TablerIcon icon={IconFolder} className="lt-sidebar-icon" />}
          onClick={() => onToggleTop('files')}
        />
      </Tooltip>
      <Tooltip title="变更" placement="right">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={activeTop === 'changes'}
          icon={<TablerIcon icon={IconGitCommit} className="lt-sidebar-icon" />}
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
          icon={<TablerIcon icon={IconTerminal2} className="lt-sidebar-icon" />}
          onClick={() => onToggleBottom('terminal')}
        />
      </Tooltip>
      <Tooltip title="Git" placement="right">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={activeBottom === 'git'}
          icon={<TablerIcon icon={IconGitBranch} className="lt-sidebar-icon" />}
          onClick={() => onToggleBottom('git')}
        />
      </Tooltip>
    </div>
  </div>
);

export default LeftSidebar;
