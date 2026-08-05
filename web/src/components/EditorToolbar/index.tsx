import React from 'react';
import './index.css';

export interface ToolbarProps {
  /** 左侧内容：通常放动作按钮（验证 / 格式化 / Pull） */
  left?: React.ReactNode;
  /** 右侧内容：通常放模式切换、视图切换 */
  right?: React.ReactNode;
  className?: string;
}

const Toolbar: React.FC<ToolbarProps> = ({ left, right, className = '' }) => {
  return (
    <div className={`lt-editor-toolbar ${className}`}>
      <div className="lt-editor-toolbar-left">{left}</div>
      <div className="lt-editor-toolbar-right">{right}</div>
    </div>
  );
};

export default Toolbar;
