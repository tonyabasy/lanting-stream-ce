import type { LeftBottomTab } from '../hooks/useEditorPanels';
import '../index.css';

interface TerminalPanelProps {
  active: LeftBottomTab;
}

const TerminalPanel: React.FC<TerminalPanelProps> = ({ active }) => (
  <div className="lt-panel-base">
    {active === 'terminal' && 'TerminalPanel（终端区）'}
    {active === 'git' && 'GitPanel（Git 区）'}
    {active === null && 'TerminalPanel（已关闭）'}
  </div>
);

export default TerminalPanel;
