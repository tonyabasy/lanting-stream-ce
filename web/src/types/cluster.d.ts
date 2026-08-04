/** 集群实体 */
export interface ClusterVO {
  id: string;
  name: string;
  flinkHome: string;
  flinkVersion: string;
  resourceType: string;      // YARN / KUBERNETES / LOCAL
  deployTarget: string;       // yarn-session / kubernetes-application / local 等
  configurations: string | null;
  status: string;             // ACTIVE / INACTIVE
  createTime: number;
  updateTime: number;
}

/** 新建集群 */
export interface CreateClusterDTO {
  name: string;
  flinkHome: string;
  deployTarget: string;
  configurations?: string;
}

/** 编辑集群 */
export interface UpdateClusterDTO {
  id: string;
  name: string;
  flinkHome: string;
  deployTarget: string;
  configurations?: string;
}
