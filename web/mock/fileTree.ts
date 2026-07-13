const now = Date.now();

interface MockFileTreeNode {
  name: string;
  path: string;
  type: 'file' | 'folder';
  mtime: number;
}

const childrenOf = (parentPath: string): MockFileTreeNode[] | undefined => {
  const map: Record<string, MockFileTreeNode[]> = {
    '': [
      { name: 'docs', path: 'docs', type: 'folder', mtime: now },
      { name: 'config', path: 'config', type: 'folder', mtime: now },
      { name: 'sql', path: 'sql', type: 'folder', mtime: now },
      { name: 'ddl', path: 'ddl', type: 'folder', mtime: now },
      { name: 'README.md', path: 'README.md', type: 'file', mtime: now },
      { name: 'project.json', path: 'project.json', type: 'file', mtime: now },
    ],
    docs: [
      { name: 'architecture.md', path: 'docs/architecture.md', type: 'file', mtime: now },
      { name: 'data_model_design.html', path: 'docs/data_model_design.html', type: 'file', mtime: now },
      { name: 'etl_process.md', path: 'docs/etl_process.md', type: 'file', mtime: now },
      { name: 'api_spec.md', path: 'docs/api_spec.md', type: 'file', mtime: now },
      { name: 'requirements.html', path: 'docs/requirements.html', type: 'file', mtime: now },
    ],
    config: [
      { name: 'source.json', path: 'config/source.json', type: 'file', mtime: now },
      { name: 'sink.json', path: 'config/sink.json', type: 'file', mtime: now },
      { name: 'job.json', path: 'config/job.json', type: 'file', mtime: now },
      { name: 'env.json', path: 'config/env.json', type: 'file', mtime: now },
    ],
    sql: [
      { name: 'ods', path: 'sql/ods', type: 'folder', mtime: now },
      { name: 'dwd', path: 'sql/dwd', type: 'folder', mtime: now },
      { name: 'dws', path: 'sql/dws', type: 'folder', mtime: now },
      { name: 'ads', path: 'sql/ads', type: 'folder', mtime: now },
    ],
    'sql/ods': [
      { name: 'ods_user_log.sql', path: 'sql/ods/ods_user_log.sql', type: 'file', mtime: now },
      { name: 'ods_order.sql', path: 'sql/ods/ods_order.sql', type: 'file', mtime: now },
    ],
    'sql/dwd': [
      { name: 'dwd_user_event.sql', path: 'sql/dwd/dwd_user_event.sql', type: 'file', mtime: now },
      { name: 'dwd_order_detail.sql', path: 'sql/dwd/dwd_order_detail.sql', type: 'file', mtime: now },
    ],
    'sql/dws': [
      { name: 'dws_order_summary.sql', path: 'sql/dws/dws_order_summary.sql', type: 'file', mtime: now },
    ],
    'sql/ads': [
      { name: 'ads_order_report.sql', path: 'sql/ads/ads_order_report.sql', type: 'file', mtime: now },
      { name: 'ads_user_retention.sql', path: 'sql/ads/ads_user_retention.sql', type: 'file', mtime: now },
    ],
    ddl: [
      { name: 'create_ods_tables.ddl', path: 'ddl/create_ods_tables.ddl', type: 'file', mtime: now },
      { name: 'create_dwd_tables.ddl', path: 'ddl/create_dwd_tables.ddl', type: 'file', mtime: now },
      { name: 'create_dws_tables.ddl', path: 'ddl/create_dws_tables.ddl', type: 'file', mtime: now },
    ],
  };

  return map[parentPath];
};

/**
 * 文件树 mock 接口。
 *
 * umi 会自动识别 web/mock/ 下的导出，拦截对应路由。
 */
export default {
  'GET /api/files/tree': async (req: any, res: any) => {
    const parentPath = req.query.parentPath ?? '';
    const children = childrenOf(parentPath);
    res.json({ code: 0, data: children ?? [] });
  },
};
