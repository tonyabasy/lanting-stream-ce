import '../index.css';

interface TerminalPanelProps {
  active: 'terminal' | 'git' | null;
}

const TerminalPanel: React.FC<TerminalPanelProps> = ({ active }) => (
  <div className="lt-panel-base">
    {active === 'terminal' && 'TerminalPanel（终端区）'}
    {active === 'git' && 'GitPanel（Git 区）'}
    {active === null && 'TerminalPanel（已关闭）'}
  </div>
);

export default TerminalPanel;
