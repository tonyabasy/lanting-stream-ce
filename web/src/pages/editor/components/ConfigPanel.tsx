import type { RightTab } from '../hooks/useEditorPanels';
import '../index.css';

interface ConfigPanelProps {
  active: RightTab;
}

const ConfigPanel: React.FC<ConfigPanelProps> = ({ active }) => (
  <div className="lt-panel-base">
    {active === 'config' && 'ConfigPanel（配置区）'}
    {active === 'ai' && 'AIPanel（AI 区）'}
    {active === null && 'ConfigPanel（已关闭）'}
  </div>
);

export default ConfigPanel;
