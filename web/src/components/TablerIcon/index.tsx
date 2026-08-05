import React from 'react';
import Icon from '@ant-design/icons';

export interface TablerIconProps extends Omit<React.ComponentProps<typeof Icon>, 'component' | 'size'> {
  /** Tabler Icons 组件，如 IconTable、IconCodeblock */
  icon: React.ElementType;
  /** SVG 尺寸，默认继承外层字体大小 */
  size?: number | string;
  /** 描边粗细 */
  strokeWidth?: number;
}

/**
 * 将 @tabler/icons-react 图标包装成 Ant Design Icon 风格。
 * 用于需要 Ant Design 图标对齐行为的场景（Segmented / Button / Menu / Tree 等）。
 */
const TablerIcon: React.FC<TablerIconProps> = ({
  icon: TablerIconComponent,
  size = '1em',
  strokeWidth = 2,
  className,
  ...rest
}) => {
  const SvgComponent: React.FC = () => (
    <TablerIconComponent size={size} strokeWidth={strokeWidth} className={className} />
  );
  return <Icon component={SvgComponent} {...rest} />;
};

export default TablerIcon;
