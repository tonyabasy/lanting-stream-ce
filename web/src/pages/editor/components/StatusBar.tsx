import React from 'react';
import { useModel } from 'umi';
import '../index.css';

const StatusBar: React.FC = () => {
  const { selectedNode } = useModel('fileTree');
  const segments = selectedNode ? selectedNode.path.split('/') : [];

  return (
    <div className="lt-statusbar">
      {segments.map((segment, index) => (
        <React.Fragment key={index}>
          {index > 0 && <span>/</span>}
          <span className={index === segments.length - 1 ? 'lt-statusbar-current' : undefined}>
            {segment}
          </span>
        </React.Fragment>
      ))}
    </div>
  );
};

export default StatusBar;
