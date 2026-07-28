import type { LeftTopTab } from '@/models/devPanels';
import FileTree from '../tree/FileTree';
import '../index.css';

interface ProjectPanelProps {
  active: LeftTopTab;
}

const ProjectPanel: React.FC<ProjectPanelProps> = ({ active }) => (
  <div className="lt-panel-base">
    <div style={{ display: active === 'files' ? undefined : 'none', width: '100%', height: '100%' }}>
      <FileTree />
    </div>
    <div style={{ display: active === 'tables' ? undefined : 'none' }}>ProjectPanel（模型区）</div>
    <div style={{ display: active === 'changes' ? undefined : 'none' }}>ChangesPanel（变更区）</div>
    <div style={{ display: active === null ? undefined : 'none' }}>ProjectPanel（已关闭）</div>
  </div>
);

export default ProjectPanel;
