import React from 'react';
import { Tooltip as AntdTooltip, TooltipProps as AntdTooltipProps } from 'antd';

export interface TooltipProps extends AntdTooltipProps {}

const DEFAULT_MOUSE_ENTER_DELAY = 0.6;
const DEFAULT_MOUSE_LEAVE_DELAY = 0.1;

const Tooltip: React.FC<TooltipProps> = ({
  mouseEnterDelay = DEFAULT_MOUSE_ENTER_DELAY,
  mouseLeaveDelay = DEFAULT_MOUSE_LEAVE_DELAY,
  ...rest
}) => (
  <AntdTooltip
    mouseEnterDelay={mouseEnterDelay}
    mouseLeaveDelay={mouseLeaveDelay}
    {...rest}
  />
);

export default Tooltip;
