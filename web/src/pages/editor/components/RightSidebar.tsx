import { IconAdjustments, IconFlower } from '@tabler/icons-react';
import { Button, Tooltip } from 'antd';
import '../index.css';

interface RightSidebarProps {
  active: 'config' | 'ai' | null;
  onToggle: (key: 'config' | 'ai') => void;
}

const RightSidebar: React.FC<RightSidebarProps> = ({ active, onToggle }) => (
  <div className="lt-sidebar">
    <div className="lt-sidebar-group">
      <Tooltip title="配置" placement="left">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={active === 'config'}
          icon={<IconAdjustments className="lt-sidebar-icon" />}
          onClick={() => onToggle('config')}
        />
      </Tooltip>
      <Tooltip title="AI" placement="left">
        <Button
          className="lt-sidebar-btn"
          type="text"
          data-active={active === 'ai'}
          icon={<IconFlower className="lt-sidebar-icon" />}
          onClick={() => onToggle('ai')}
        />
      </Tooltip>
    </div>
  </div>
);

export default RightSidebar;
