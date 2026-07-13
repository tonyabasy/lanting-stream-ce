import { IconFolder, IconGitCommit, IconTerminal2, IconGitBranch, IconTable } from '@tabler/icons-react';
import { Button, Tooltip } from 'antd';
import type { LeftTopKey, LeftBottomKey, LeftTopTab, LeftBottomTab } from '../hooks/useEditorPanels';
import '../index.css';

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
      <Tooltip title="SQL文件" placement="right">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={activeTop === 'files'}
          icon={<IconFolder className="lt-sidebar-icon" />}
          onClick={() => onToggleTop('files')}
        />
      </Tooltip>
      <Tooltip title="表模型" placement="right">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={activeTop === 'tables'}
          icon={<IconTable className="lt-sidebar-icon" />}
          onClick={() => onToggleTop('tables')}
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
