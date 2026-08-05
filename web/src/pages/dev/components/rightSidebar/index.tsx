import { IconAdjustments, IconFlower } from '@tabler/icons-react';
import { Button } from 'antd';
import Tooltip from '@/components/Tooltip';
import TablerIcon from '@/components/TablerIcon';
import type { RightTab, RightKey } from '@/models/devPanels';
import '../../index.css';

interface RightSidebarProps {
  active: RightTab;
  onToggle: (key: RightKey) => void;
}

const RightSidebar: React.FC<RightSidebarProps> = ({ active, onToggle }) => (
  <div className="lt-sidebar">
    <div className="lt-sidebar-group">
      <Tooltip title="配置" placement="left">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={active === 'config'}
          icon={<TablerIcon icon={IconAdjustments} className="lt-sidebar-icon" />}
          onClick={() => onToggle('config')}
        />
      </Tooltip>
      <Tooltip title="AI" placement="left">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={active === 'ai'}
          icon={<TablerIcon icon={IconFlower} className="lt-sidebar-icon" />}
          onClick={() => onToggle('ai')}
        />
      </Tooltip>
    </div>
  </div>
);

export default RightSidebar;
