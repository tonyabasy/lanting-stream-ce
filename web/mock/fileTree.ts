const now = Date.now();

interface MockFileTreeNode {
  fileId: number;
  name: string;
  path: string;
  type: 'file' | 'folder';
  mtime: number;
  children?: MockFileTreeNode[];
}

const childrenOf = (parentPath: string): MockFileTreeNode[] | undefined => {
  const map: Record<string, MockFileTreeNode[]> = {
    '': [
      { fileId: 1, name: 'docs', path: 'docs', type: 'folder', mtime: now },
      { fileId: 2, name: 'config', path: 'config', type: 'folder', mtime: now },
      { fileId: 3, name: 'sql', path: 'sql', type: 'folder', mtime: now },
      { fileId: 4, name: 'ddl', path: 'ddl', type: 'folder', mtime: now },
      { fileId: 5, name: 'README.md', path: 'README.md', type: 'file', mtime: now },
      { fileId: 6, name: 'project.json', path: 'project.json', type: 'file', mtime: now },
    ],
    docs: [
      { fileId: 7, name: 'architecture.md', path: 'docs/architecture.md', type: 'file', mtime: now },
      { fileId: 8, name: 'data_model_design.html', path: 'docs/data_model_design.html', type: 'file', mtime: now },
      { fileId: 9, name: 'etl_process.md', path: 'docs/etl_process.md', type: 'file', mtime: now },
      { fileId: 10, name: 'api_spec.md', path: 'docs/api_spec.md', type: 'file', mtime: now },
      { fileId: 11, name: 'requirements.html', path: 'docs/requirements.html', type: 'file', mtime: now },
    ],
    config: [
      { fileId: 12, name: 'source.json', path: 'config/source.json', type: 'file', mtime: now },
      { fileId: 13, name: 'sink.json', path: 'config/sink.json', type: 'file', mtime: now },
      { fileId: 14, name: 'job.json', path: 'config/job.json', type: 'file', mtime: now },
      { fileId: 15, name: 'env.json', path: 'config/env.json', type: 'file', mtime: now },
    ],
    sql: [
      { fileId: 16, name: 'ods', path: 'sql/ods', type: 'folder', mtime: now },
      { fileId: 17, name: 'dwd', path: 'sql/dwd', type: 'folder', mtime: now },
      { fileId: 18, name: 'dws', path: 'sql/dws', type: 'folder', mtime: now },
      { fileId: 19, name: 'ads', path: 'sql/ads', type: 'folder', mtime: now },
    ],
    'sql/ods': [
      { fileId: 20, name: 'ods_user_log.sql', path: 'sql/ods/ods_user_log.sql', type: 'file', mtime: now },
      { fileId: 21, name: 'ods_order.sql', path: 'sql/ods/ods_order.sql', type: 'file', mtime: now },
    ],
    'sql/dwd': [
      { fileId: 22, name: 'dwd_user_event.sql', path: 'sql/dwd/dwd_user_event.sql', type: 'file', mtime: now },
      { fileId: 23, name: 'dwd_order_detail.sql', path: 'sql/dwd/dwd_order_detail.sql', type: 'file', mtime: now },
    ],
    'sql/dws': [
      { fileId: 24, name: 'dws_order_summary.sql', path: 'sql/dws/dws_order_summary.sql', type: 'file', mtime: now },
    ],
    'sql/ads': [
      { fileId: 25, name: 'ads_order_report.sql', path: 'sql/ads/ads_order_report.sql', type: 'file', mtime: now },
      { fileId: 26, name: 'ads_user_retention.sql', path: 'sql/ads/ads_user_retention.sql', type: 'file', mtime: now },
    ],
    ddl: [
      { fileId: 27, name: 'create_ods_tables.ddl', path: 'ddl/create_ods_tables.ddl', type: 'file', mtime: now },
      { fileId: 28, name: 'create_dwd_tables.ddl', path: 'ddl/create_dwd_tables.ddl', type: 'file', mtime: now },
      { fileId: 29, name: 'create_dws_tables.ddl', path: 'ddl/create_dws_tables.ddl', type: 'file', mtime: now },
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
