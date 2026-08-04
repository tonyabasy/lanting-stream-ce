import type { LeftTopTab } from '@/models/devPanels';
import FileTree from '@/pages/dev/components/FileTree';
import '../index.css';

interface ProjectPanelProps {
  active: LeftTopTab;
}

const ProjectPanel: React.FC<ProjectPanelProps> = ({ active }) => {
  if (!active) return <div className="lt-panel-base" />;
  // 变更面板单独模块，此处占位
  if (active === 'changes') {
    return <div className="lt-panel-base">变更面板待实现</div>;
  }
  // 文件树面板：视图由 fileTree model 的 viewKey 决定，下拉切换
  return (
    <div className="lt-panel-base">
      <FileTree />
    </div>
  );
};

export default ProjectPanel;
