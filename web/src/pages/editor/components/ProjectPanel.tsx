import '../index.css';

interface ProjectPanelProps {
  active: 'folder' | 'changes' | null;
}

const ProjectPanel: React.FC<ProjectPanelProps> = ({ active }) => (
  <div className="lt-panel-base">
    {active === 'folder' && 'ProjectPanel（目录区）'}
    {active === 'changes' && 'ChangesPanel（变更区）'}
    {active === null && 'ProjectPanel（已关闭）'}
  </div>
);

export default ProjectPanel;
