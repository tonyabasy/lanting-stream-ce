import { useCallback, useEffect, useState } from 'react';
import { stringToCreateTable, createTableToString } from '@/services/table';
import type { FlinkColumnVO, FlinkTableVO } from '@/types/table';

/** 前端字段行唯一标识（仅用于 React key 与行定位，不参与序列化） */
export type FormColumn = FlinkColumnVO & { __id: string };

/** 前端表单数据模型 */
export type FormData = Omit<FlinkTableVO, 'columns'> & { columns: FormColumn[] };

let idSeq = 0;
const generateId = () => `row-${Date.now().toString(36)}-${++idSeq}`;

const makeDefaultFormData = (): FormData => ({
  tableName: '',
  ifNotExists: false,
  columns: [],
  watermark: null,
  primaryKeys: [],
  comment: null,
  distribution: null,
  partitionKeys: [],
  properties: {},
  connector: null,
});

const enrichColumns = (columns: FlinkColumnVO[]): FormColumn[] =>
  columns.map((col) => ({ ...col, __id: generateId() }));

const stripColumns = (columns: FormColumn[]): FlinkColumnVO[] =>
  columns.map(({ __id, ...col }) => col);

const enrich = (data: FlinkTableVO): FormData => ({
  ...data,
  columns: enrichColumns(data.columns),
});

/**
 * TableFormEditor 状态层。
 *
 * - 加载时调用后端 /tables/utils/deserialize 解析 DDL。
 * - 提供局部更新函数，保证未变更的行/对象引用不变，便于 React.memo。
 * - 序列化时剔除前端辅助字段 __id。
 */
export const useTableForm = (initialDdl: string) => {
  const [formData, setFormData] = useState<FormData>(makeDefaultFormData());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 初始解析
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    stringToCreateTable(initialDdl)
      .then((data) => {
        if (!cancelled) setFormData(enrich(data));
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e?.message || 'DDL 解析失败，已展示空表单');
          setFormData(makeDefaultFormData());
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [initialDdl]);

  /** 顶层字段局部更新 */
  const setPartial = useCallback((patch: Partial<FormData>) => {
    setFormData((prev) => ({ ...prev, ...patch }));
  }, []);

  /** 列数组级更新（内部保证只改需要改的行） */
  const updateColumns = useCallback((updater: (cols: FormColumn[]) => FormColumn[]) => {
    setFormData((prev) => ({ ...prev, columns: updater(prev.columns) }));
  }, []);

  /** 新增字段 */
  const addColumn = useCallback((column?: Partial<FlinkColumnVO>) => {
    setFormData((prev) => {
      const newCol: FormColumn = {
        __id: generateId(),
        name: '',
        type: 'STRING',
        comment: null,
        ordinal: prev.columns.length,
        columnType: 'physical',
        metadataFrom: null,
        virtual: false,
        expr: null,
        ...column,
      };
      return { ...prev, columns: [...prev.columns, newCol] };
    });
  }, []);

  /** 更新单个字段 */
  const updateColumn = useCallback((id: string, patch: Partial<FlinkColumnVO>) => {
    setFormData((prev) => ({
      ...prev,
      columns: prev.columns.map((col) => (col.__id === id ? { ...col, ...patch } : col)),
    }));
  }, []);

  /** 删除字段并重建 ordinal */
  const removeColumn = useCallback((id: string) => {
    setFormData((prev) => ({
      ...prev,
      columns: prev.columns
        .filter((c) => c.__id !== id)
        .map((c, i) => ({ ...c, ordinal: i })),
    }));
  }, []);

  /** 移动字段并重建 ordinal */
  const moveColumn = useCallback((fromIndex: number, toIndex: number) => {
    setFormData((prev) => {
      const next = [...prev.columns];
      const [moved] = next.splice(fromIndex, 1);
      next.splice(toIndex, 0, moved);
      return { ...prev, columns: next.map((c, i) => ({ ...c, ordinal: i })) };
    });
  }, []);

  /** 序列化为 DDL */
  const serialize = useCallback(
    () => createTableToString({ ...formData, columns: stripColumns(formData.columns) }),
    [formData],
  );

  return {
    formData,
    loading,
    error,
    setPartial,
    updateColumns,
    addColumn,
    updateColumn,
    removeColumn,
    moveColumn,
    serialize,
  };
};
