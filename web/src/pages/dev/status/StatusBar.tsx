import React from 'react';
import { useModel } from 'umi';
import { IconLock, IconLockOpen } from '@tabler/icons-react';
import '../index.css';

const StatusBar: React.FC = () => {
  const { selectedNode } = useModel('fileTree');
  const { openTabs, activeTabId, isFileEditable } = useModel('editor');

  const segments = selectedNode ? selectedNode.path.split('/') : [];
  const editable = activeTabId !== null && isFileEditable(activeTabId);

  return (
    <div className="lt-statusbar">
      <div className="lt-statusbar-breadcrumb">
        {segments.map((segment, index) => (
          <React.Fragment key={index}>
            {index > 0 && <span className="lt-statusbar-sep">/</span>}
            <span className={index === segments.length - 1 ? 'lt-statusbar-current' : undefined}>
              {segment}
            </span>
          </React.Fragment>
        ))}
      </div>
      <div className="lt-statusbar-right">
        {activeTabId !== null && (
          <span className="lt-statusbar-lock">
            {editable ? (
              <><IconLockOpen size={10} /> 编辑中</>
            ) : (
              <><IconLock size={10} /> 只读</>
            )}
          </span>
        )}
      </div>
    </div>
  );
};

export default StatusBar;
