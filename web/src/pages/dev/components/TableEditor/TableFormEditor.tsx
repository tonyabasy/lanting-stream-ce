import { Button, Input, Segmented, Table, Tag, Flex } from 'antd';
import type { TableProps } from 'antd';
import { IconGripVertical, IconPlus, IconX } from '@tabler/icons-react';
import TablerIcon from '@/components/TablerIcon';
import type { FlinkColumnVO, FlinkTableVO } from '@/types/table';
import './index.css';

export interface TableFormEditorProps {
  /** 表单数据（由容器 deserialize 回填 + 编辑） */
  value: FlinkTableVO;
  /** 数据变更 */
  onChange: (data: FlinkTableVO) => void;
  /** 只读（锁被他人持有） */
  readonly: boolean;
  /** 保存（确定按钮） */
  onSave: () => void;
}

/** 常用 Flink 类型候选（datalist 联想） */
const TYPE_CANDIDATES = [
  'STRING', 'BIGINT', 'INT', 'DOUBLE', 'FLOAT', 'BOOLEAN',
  'TIMESTAMP(3)', 'TIMESTAMP_LTZ(3)', 'DECIMAL(10,2)', 'DATE', 'TIME',
];

/** 列类型徽标颜色 */
const COLUMN_TYPE_TAG = {
  physical: { color: 'default', label: '物理' },
  metadata: { color: 'gold', label: '元数据' },
  computed: { color: 'red', label: '计算' },
} as const;

/**
 * Table 五段式表单编辑器。
 *
 * 段：基本信息 / 字段定义 / 高级属性（PK/WATERMARK/DISTRIBUTED）/ 分区 / 连接器属性。
 * 受控组件：数据由容器持有，本组件只触发 onChange。
 */
const TableFormEditor: React.FC<TableFormEditorProps> = ({
  value,
  onChange,
  readonly,
  onSave,
}) => {
  /** 更新顶层字段 */
  const patch = (partial: Partial<FlinkTableVO>) => onChange({ ...value, ...partial });

  // ── 字段列表操作 ──
  const patchField = (index: number, field: Partial<FlinkColumnVO>) => {
    const columns = value.columns.map((c, i) => (i === index ? { ...c, ...field } : c));
    patch({ columns });
  };
  const addField = () => patch({
    columns: [...value.columns, {
      name: '', type: 'STRING', comment: null, ordinal: value.columns.length,
      columnType: 'physical', metadataFrom: null, virtual: false, expr: null,
    }],
  });
  const removeField = (index: number) => patch({ columns: value.columns.filter((_, i) => i !== index) });

  /** 字段上移/下移（排序） */
  const moveField = (index: number, dir: -1 | 1) => {
    const target = index + dir;
    if (target < 0 || target >= value.columns.length) return;
    const columns = [...value.columns];
    [columns[index], columns[target]] = [columns[target], columns[index]];
    // 修正 ordinal
    patch({ columns: columns.map((c, i) => ({ ...c, ordinal: i })) });
  };

  // ── 属性列表操作（Record<string,string>）──
  const propertyEntries = Object.entries(value.properties ?? {});
  const patchProperty = (key: string, propValue: string) => {
    patch({ properties: { ...value.properties, [key]: propValue } });
  };
  const renamePropertyKey = (oldKey: string, newKey: string) => {
    const { [oldKey]: v, ...rest } = value.properties;
    patch({ properties: { ...rest, [newKey]: v } });
  };
  const addProperty = () => {
    // 找一个不冲突的空 key
    let i = 1;
    while (value.properties[`property_${i}`] !== undefined) i++;
    patch({ properties: { ...value.properties, [`property_${i}`]: '' } });
  };
  const removeProperty = (key: string) => {
    const { [key]: _, ...rest } = value.properties;
    patch({ properties: rest });
  };

  // ── 字段表格列 ──
  const fieldColumns: TableProps<FlinkColumnVO>['columns'] = [
    {
      title: '',
      width: 36,
      render: (_, __, index) => (
        <div className="lt-table-field-drag">
          <IconGripVertical size={13} />
          <div className="lt-table-field-move">
            <button disabled={readonly || index === 0} onClick={() => moveField(index, -1)}>↑</button>
            <button disabled={readonly || index === value.columns.length - 1} onClick={() => moveField(index, 1)}>↓</button>
          </div>
        </div>
      ),
    },
    {
      title: '类型',
      width: 72,
      render: (_, record) => (
        <Tag color={COLUMN_TYPE_TAG[record.columnType].color}>
          {COLUMN_TYPE_TAG[record.columnType].label}
        </Tag>
      ),
    },
    {
      title: '字段名',
      dataIndex: 'name',
      render: (_, record, index) => (
        <Input
          size="small"
          value={record.name}
          disabled={readonly || record.columnType === 'computed'}
          placeholder="字段名"
          onChange={(e) => patchField(index, { name: e.target.value })}
        />
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      render: (_, record, index) => (
        record.columnType === 'computed' ? (
          <Input size="small" value={record.expr ?? ''} disabled placeholder="表达式" />
        ) : (
          <Input
            size="small"
            value={record.type ?? ''}
            disabled={readonly}
            placeholder="STRING"
            list="lt-table-field-types"
            onChange={(e) => patchField(index, { type: e.target.value })}
          />
        )
      ),
    },
    {
      title: '注释',
      dataIndex: 'comment',
      render: (_, record, index) => (
        <Input
          size="small"
          value={record.comment ?? ''}
          disabled={readonly || record.columnType === 'computed'}
          placeholder="字段说明"
          onChange={(e) => patchField(index, { comment: e.target.value })}
        />
      ),
    },
    ...(readonly
      ? []
      : [{
          title: '',
          width: 36,
          render: (_: unknown, __: FlinkColumnVO, index: number) => (
            <Button
              type="text" size="small" danger
              icon={<TablerIcon icon={IconX} size={13} />}
              onClick={() => removeField(index)}
            />
          ),
        } as NonNullable<TableProps<FlinkColumnVO>['columns']>[number]]),
  ];

  // ── 属性表格列（key/value，Record 条目）──
  const propertyColumns: TableProps<[string, string]>['columns'] = [
    {
      title: 'Key',
      width: '40%',
      render: (_, entry) => (
        <Input
          size="small"
          value={entry[0]}
          disabled={readonly}
          placeholder="connector / topic / format ..."
          onChange={(e) => renamePropertyKey(entry[0], e.target.value)}
        />
      ),
    },
    {
      title: 'Value',
      render: (_, entry) => (
        <Input
          size="small"
          value={entry[1]}
          disabled={readonly}
          onChange={(e) => patchProperty(entry[0], e.target.value)}
        />
      ),
    },
    ...(readonly
      ? []
      : [{
          title: '',
          width: 36,
          render: (_: unknown, entry: [string, string]) => (
            <Button
              type="text" size="small" danger
              icon={<TablerIcon icon={IconX} size={13} />}
              onClick={() => removeProperty(entry[0])}
            />
          ),
        } as NonNullable<TableProps<[string, string]>['columns']>[number]]),
  ];

  /** 字段名候选（PK/分区/分布选择用） */
  const fieldNames = value.columns.map((c) => c.name).filter((n) => n.trim());

  return (
    <div className="lt-table-form">
      {/* ── 段1：基本信息 ── */}
      <div className="lt-table-form-section">
        <div className="lt-table-form-label">基本信息</div>
        <Flex gap={12}>
          <Input
            size="small" value={value.tableName} disabled={readonly}
            placeholder="表名（如 ods_order）"
            onChange={(e) => patch({ tableName: e.target.value })}
          />
          <Input
            size="small" value={value.connector ?? ''} disabled={readonly}
            placeholder="连接器（如 kafka / doris / jdbc）"
            onChange={(e) => patch({ connector: e.target.value })}
          />
          <Input
            size="small" value={value.comment ?? ''} disabled={readonly}
            placeholder="表注释（COMMENT）"
            onChange={(e) => patch({ comment: e.target.value })}
          />
        </Flex>
      </div>

      {/* ── 段2：字段定义 ── */}
      <div className="lt-table-form-section">
        <div className="lt-table-form-label">字段定义</div>
        <Table<FlinkColumnVO>
          size="small"
          rowKey={(_, i) => String(i)}
          columns={fieldColumns}
          dataSource={value.columns}
          pagination={false}
          footer={() =>
            !readonly && (
              <Button type="dashed" size="small" block icon={<TablerIcon icon={IconPlus} size={13} />} onClick={addField}>
                添加字段
              </Button>
            )
          }
        />
        {value.columns.some((c) => c.columnType === 'computed') && (
          <div className="lt-table-form-hint">
            计算列为表达式派生，表单只读展示，编辑请切换文本模式
          </div>
        )}
      </div>

      {/* ── 段3：高级属性 ── */}
      <div className="lt-table-form-section">
        <div className="lt-table-form-label">高级属性</div>

        {/* PRIMARY KEY */}
        <div className="lt-table-adv-card">
          <div className="lt-table-adv-title">PRIMARY KEY</div>
          <Segmented
            size="small"
            disabled={readonly}
            options={[
              { label: '无', value: '' },
              ...fieldNames.map((n) => ({ label: n, value: n })),
            ]}
            value={value.primaryKeys[0] ?? ''}
            onChange={(v) => patch({ primaryKeys: v ? [String(v)] : [] })}
          />
        </div>

        {/* WATERMARK */}
        <div className="lt-table-adv-card">
          <div className="lt-table-adv-title">WATERMARK</div>
          <Flex gap={8} align="center">
            <Input
              size="small" value={value.watermark?.field ?? ''} disabled={readonly}
              placeholder="时间字段"
              style={{ width: 160 }}
              onChange={(e) => patch({ watermark: { field: e.target.value, expr: value.watermark?.expr ?? '' } })}
            />
            <Input
              size="small" value={value.watermark?.expr ?? ''} disabled={readonly}
              placeholder="水位线表达式（如 ts - INTERVAL '5' SECOND）"
              onChange={(e) => patch({ watermark: { field: value.watermark?.field ?? '', expr: e.target.value } })}
            />
            {value.watermark && !readonly && (
              <Button type="text" size="small" danger icon={<TablerIcon icon={IconX} size={13} />}
                onClick={() => patch({ watermark: null })} />
            )}
          </Flex>
        </div>

        {/* DISTRIBUTED */}
        <div className="lt-table-adv-card">
          <div className="lt-table-adv-title">DISTRIBUTED</div>
          <Flex gap={8} align="center">
            <Input
              size="small" value={value.distribution?.by.join(', ') ?? ''} disabled={readonly}
              placeholder="分布字段（逗号分隔）"
              style={{ width: 200 }}
              onChange={(e) => patch({
                distribution: {
                  by: e.target.value.split(',').map((s) => s.trim()).filter(Boolean),
                  buckets: value.distribution?.buckets ?? null,
                },
              })}
            />
            <Input
              size="small" value={value.distribution?.buckets ?? ''} disabled={readonly}
              placeholder="桶数"
              style={{ width: 80 }}
              onChange={(e) => patch({
                distribution: {
                  by: value.distribution?.by ?? [],
                  buckets: e.target.value ? Number(e.target.value) : null,
                },
              })}
            />
            {value.distribution && !readonly && (
              <Button type="text" size="small" danger icon={<TablerIcon icon={IconX} size={13} />}
                onClick={() => patch({ distribution: null })} />
            )}
          </Flex>
        </div>
      </div>

      {/* ── 段4：分区 ── */}
      <div className="lt-table-form-section">
        <div className="lt-table-form-label">分区</div>
        <Segmented
          size="small"
          disabled={readonly}
          options={[
            { label: '无分区', value: '' },
            ...fieldNames.map((n) => ({ label: n, value: n })),
          ]}
          value={value.partitionKeys[0] ?? ''}
          onChange={(v) => patch({ partitionKeys: v ? [String(v)] : [] })}
        />
      </div>

      {/* ── 段5：连接器属性 ── */}
      <div className="lt-table-form-section">
        <div className="lt-table-form-label">连接器属性（WITH 子句）</div>
        <Table<[string, string]>
          size="small"
          rowKey={(_, i) => String(i)}
          columns={propertyColumns}
          dataSource={propertyEntries}
          pagination={false}
          footer={() =>
            !readonly && (
              <Button type="dashed" size="small" block icon={<TablerIcon icon={IconPlus} size={13} />} onClick={addProperty}>
                添加属性
              </Button>
            )
          }
        />
      </div>

      {/* 保存按钮 */}
      {!readonly && (
        <div className="lt-table-form-footer">
          <Button type="primary" onClick={onSave}>确定</Button>
        </div>
      )}

      {/* 字段类型候选（datalist 联想） */}
      <datalist id="lt-table-field-types">
        {TYPE_CANDIDATES.map((t) => <option key={t} value={t} />)}
      </datalist>
    </div>
  );
};

export default TableFormEditor;
