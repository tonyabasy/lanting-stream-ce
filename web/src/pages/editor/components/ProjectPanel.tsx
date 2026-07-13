import type { LeftTopTab } from '../hooks/useEditorPanels';
import FileTree from './FileTree';
import '../index.css';

interface ProjectPanelProps {
  active: LeftTopTab;
}

const ProjectPanel: React.FC<ProjectPanelProps> = ({ active }) => (
  <div className="lt-panel-base">
    {active === 'files' && <FileTree />}
    {active === 'tables' && 'ProjectPanel（模型区）'}
    {active === 'changes' && 'ChangesPanel（变更区）'}
    {active === null && 'ProjectPanel（已关闭）'}
  </div>
);

export default ProjectPanel;
