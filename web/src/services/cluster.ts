import request from '@/utils/request';
import type { ClusterVO, CreateClusterDTO, UpdateClusterDTO } from '@/types/cluster';

export const listClusters = (): Promise<ClusterVO[]> =>
  request.get('/clusters');

export const getCluster = (id: string): Promise<ClusterVO> =>
  request.get(`/clusters/${id}`);

export const createCluster = (dto: CreateClusterDTO): Promise<ClusterVO> =>
  request.post('/clusters', dto);

export const updateCluster = (id: string, dto: UpdateClusterDTO): Promise<ClusterVO> =>
  request.put(`/clusters/${id}`, dto);

export const deleteCluster = (id: string): Promise<void> =>
  request.delete(`/clusters/${id}`);

export const toggleClusterStatus = (id: string): Promise<ClusterVO> =>
  request.put(`/clusters/${id}/status`);

export const checkFlinkVersion = (flinkHome: string): Promise<string> =>
  request.get('/clusters/check-version', { params: { flinkHome } });
