import type { LeftTopTab } from '@/models/devPanels';
import FileTree from '../tree/FileTree';
import '../index.css';

interface ProjectPanelProps {
  active: LeftTopTab;
}

const ProjectPanel: React.FC<ProjectPanelProps> = ({ active }) => (
  <div className="lt-panel-base">
    {active === 'files' && (<FileTree />)}
    {active === 'tables' && <div>ProjectPanel（模型区）</div>}
    {active === 'changes' && <div>ChangesPanel（变更区）</div>}
  </div>
);

export default ProjectPanel;
