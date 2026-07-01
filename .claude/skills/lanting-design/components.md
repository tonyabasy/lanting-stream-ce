# components.md — 组件规范

> 每个组件都有正确写法和错误写法。
> 风格基准：claude.ai — 米白底、clay橙、Serif标题、极简无装饰。
> 遇到不确定的情况，以这里的正确写法为准。

---

## 一、整体布局

### 页面结构

```
┌─────────────────────────────────────────┐
│  顶部导航（TopBar）高度 48px  米白底      │
├────────┬────────────────────────────────┤
│        │                                │
│ 侧边栏  │  内容区                        │
│  48px  │  padding: 20px 22px            │
│ 米白底  │  背景 #FDFCF9                  │
│        │                                │
└────────┴────────────────────────────────┘
```

### 顶部导航（TopBar）

claude.ai 顶栏是米白，不是品牌色。

```tsx
/* ✅ 正确 */
<div style={{
  height: 48,
  background: '#FAF9F6',                    // 米白，不是品牌色
  borderBottom: '0.5px solid #E8E5DF',
  display: 'flex',
  alignItems: 'center',
  padding: '0 18px',
  gap: 12,
}}>
  {/* Logo容器 + 产品名（Serif）+ 工作空间chip + 右侧图标 */}
</div>

/* ❌ 错误 */
<div style={{
  background: '#D97757',    // 品牌色顶栏，不是 Claude 风格
  background: '#1B4B5A',    // 墨青顶栏，禁止
  height: 60,               // 高度不对
}}>
```

### Logo 容器

```tsx
/* ✅ 正确 */
<div style={{
  width: 26,
  height: 26,
  borderRadius: 6,
  background: '#D97757',    // clay 橙
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
}}>
  <img src="/logo.svg" style={{ width: 14, height: 11 }} />
</div>

/* 产品名用 Serif */
<span style={{
  fontSize: 14,
  fontWeight: 500,
  color: '#1A1915',
  fontFamily: 'Georgia, serif',
}}>
  Lanting Stream
</span>
```

### 工作空间 Chip

```tsx
/* ✅ 正确 */
<span style={{
  fontSize: 11,
  color: '#9B9689',
  background: '#F0EDE6',
  padding: '2px 8px',
  borderRadius: 4,
  border: '0.5px solid #E8E5DF',
}}>
  default
</span>

/* ❌ 错误 */
<span style={{
  borderRadius: 999,          // 不用胶囊形
  background: 'rgba(0,0,0,0.1)',  // 不用透明黑
}}>
```

### 侧边栏（Sidebar）

```tsx
/* ✅ 正确 */
<div style={{
  width: 48,
  background: '#F5F3EE',              // 比页面稍深的米白
  borderRight: '0.5px solid #E8E5DF',
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  padding: '12px 0',
  gap: 2,
}}>
```

### 导航项（NavItem）

```tsx
/* ✅ 正确：激活态 */
<div style={{
  width: 32,
  height: 32,
  borderRadius: 6,
  background: '#FDEEE8',    // ls-brand-tint
  color: '#D97757',         // ls-brand
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  cursor: 'pointer',
}}>
  <HomeOutlined style={{ fontSize: 15 }} />
</div>

/* ✅ 正确：非激活态 */
<div style={{
  width: 32,
  height: 32,
  borderRadius: 6,
  color: '#9B9689',         // ls-text-muted
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  cursor: 'pointer',
  // 无背景色
}}>

/* ❌ 错误 */
<div style={{
  background: '#D97757',    // 激活不用实色品牌背景
  color: '#fff',
  borderRadius: 12,         // 圆角太大
}}>
```

---

## 二、卡片（Card）

claude.ai 卡片：米白背景、细边框、10px 圆角、Serif 标题。

```tsx
/* ✅ 正确 */
<div style={{
  background: '#FAF9F6',
  border: '0.5px solid #E8E5DF',
  borderRadius: 10,
  padding: '13px 15px',
}}>
  <div style={{
    fontSize: 12,
    fontWeight: 500,
    color: '#1A1915',
    fontFamily: 'Georgia, serif',   // 卡片标题用 Serif
    marginBottom: 10,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  }}>
    作业列表
    <span style={{ fontSize: 11, color: '#D97757', fontFamily: 'system-ui', fontWeight: 400, cursor: 'pointer' }}>
      全部 →
    </span>
  </div>
  {/* 卡片内容 */}
</div>

/* ❌ 错误 */
<div style={{
  background: '#ffffff',        // 纯白，不是米白
  borderRadius: 8,              // 应该是 10px
  boxShadow: '0 4px 12px ...',  // 不加阴影
  border: '1px solid #e0e0e0', // 边框太重
}}>
  <div style={{ fontWeight: 600 }}>  // 字重太重
    卡片标题
  </div>
```

---

## 三、统计卡片（Stat Card）

```tsx
/* ✅ 正确 */
<div style={{
  background: '#F5F3EE',    // 比卡片更深一层的米白
  borderRadius: 10,
  padding: '11px 13px',
  // 无边框，用背景色差异区分
}}>
  <div style={{
    fontSize: 11,
    color: '#9B9689',
    marginBottom: 5,
    fontFamily: 'system-ui',
  }}>
    在线作业
  </div>
  <div style={{
    fontSize: 22,
    fontWeight: 500,
    color: '#1A1915',
    lineHeight: 1,
    fontFamily: 'system-ui',
  }}>
    12
  </div>
  <div style={{ fontSize: 10, color: '#D97757', marginTop: 4 }}>
    ↑ 2 今日
  </div>
</div>

/* ❌ 错误 */
<div style={{
  background: 'linear-gradient(...)',  // 不用渐变
  border: '0.5px solid #e8e8e6',      // 统计卡片不加边框
  fontSize: 32,                        // 数字太大
  fontWeight: 700,                     // 字重太重
}}>
```

---

## 四、按钮（Button）

```tsx
/* ✅ 正确：主要按钮 */
<button style={{
  background: '#D97757',
  color: '#fff',
  border: 'none',
  borderRadius: 6,
  fontSize: 12,
  fontWeight: 500,
  padding: '5px 13px',
  cursor: 'pointer',
  fontFamily: 'system-ui',
  transition: 'background-color 0.15s ease',
}}>
  查看分析
</button>

// hover 态
style={{ background: '#C86A47' }}

/* ✅ 正确：次要按钮 */
<button style={{
  background: 'transparent',
  color: '#1A1915',
  border: '0.5px solid #E8E5DF',
  borderRadius: 6,
  fontSize: 12,
  fontWeight: 400,
  padding: '5px 13px',
  cursor: 'pointer',
}}>
  取消
</button>

/* ✅ 正确：危险按钮 */
<button style={{
  background: '#FCEBEB',
  color: '#791F1F',
  border: '0.5px solid #F09595',
  borderRadius: 6,
  fontSize: 12,
  fontWeight: 500,
  padding: '5px 13px',
  cursor: 'pointer',
}}>
  停止作业
</button>

/* ❌ 错误 */
<Button
  type="primary"
  style={{
    background: '#1890ff',       // Ant Design 默认蓝，禁止
    borderRadius: 20,            // 圆角太大
    fontWeight: 700,             // 字重太重
    boxShadow: '...',            // 不加阴影
  }}
>
```

### 按钮文案规则

```
/* ✅ 正确：动词开头，简洁 */
查看分析
停止作业
新建脚本
添加数据源

/* ❌ 错误 */
点击提交          // 不加"点击"
确认              // 太模糊
Submit            // 不用英文（中文界面）
```

---

## 五、状态标签（Status Badge）

claude.ai 风格：方形标签，4px 圆角，字号 10px。

```tsx
/* ✅ 正确：运行中 */
<span style={{
  fontSize: 10,
  padding: '2px 6px',
  borderRadius: 4,
  background: '#EAF3DE',
  color: '#27500A',
  fontFamily: 'system-ui',
  fontWeight: 400,
}}>
  运行中
</span>

/* ✅ 正确：异常 */
<span style={{
  fontSize: 10,
  padding: '2px 6px',
  borderRadius: 4,
  background: '#FCEBEB',
  color: '#791F1F',
}}>
  异常
</span>

/* ✅ 正确：警告 */
<span style={{
  fontSize: 10,
  padding: '2px 6px',
  borderRadius: 4,
  background: '#FAEEDA',
  color: '#633806',
}}>
  警告
</span>

/* ✅ 正确：已停止 */
<span style={{
  fontSize: 10,
  padding: '2px 6px',
  borderRadius: 4,
  background: '#F1EFE8',
  color: '#888780',
  border: '0.5px solid #E8E5DF',
}}>
  已停止
</span>

/* ❌ 错误 */
<Tag style={{
  borderRadius: 999,    // 不用胶囊
  fontSize: 13,         // 太大
  fontWeight: 700,      // 太重
}}>
```

---

## 六、作业列表行（Job Row）

```tsx
/* ✅ 正确 */
<div style={{
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  padding: '6px 0',
  borderBottom: '0.5px solid #EEEBE4',
  fontSize: 12,
}}>
  {/* 状态点 */}
  <div style={{
    width: 6,
    height: 6,
    borderRadius: '50%',
    background: '#639922',    // 用语义色 dot
    flexShrink: 0,
  }} />

  {/* 作业名 */}
  <span style={{
    flex: 1,
    color: '#1A1915',
    fontWeight: 500,
    fontFamily: 'system-ui',
  }}>
    dws_user_count
  </span>

  {/* 集群 */}
  <span style={{ color: '#9B9689', fontSize: 11 }}>prod</span>

  {/* 状态标签 */}
  <span style={{
    fontSize: 10, padding: '2px 6px', borderRadius: 4,
    background: '#EAF3DE', color: '#27500A',
  }}>
    运行中
  </span>
</div>
```

---

## 七、表格（Table）

```tsx
/* ✅ 正确 */
<Table
  size="small"
  dataSource={data}
  columns={columns}
  style={{ fontSize: 12 }}
/>

const columns = [
  {
    title: '作业名称',
    dataIndex: 'name',
    width: 200,
    render: (name) => (
      <span style={{
        fontWeight: 500,
        fontSize: 12,
        color: '#1A1915',
        fontFamily: 'system-ui',
      }}>
        {name}
      </span>
    ),
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 100,
    render: (status) => <StatusBadge status={status} />,
  },
];

/* ❌ 错误 */
<Table
  bordered                      // 不用 bordered
  size="large"                  // 用 small
  style={{ boxShadow: '...' }} // 不加阴影
/>
```

---

## 八、页面标题区

```tsx
/* ✅ 正确 */
<div style={{ marginBottom: 18 }}>
  <div style={{
    fontSize: 16,
    fontWeight: 500,
    color: '#1A1915',
    fontFamily: 'Georgia, serif',  // 页面标题用 Serif
    marginBottom: 2,
  }}>
    概览
  </div>
  <div style={{
    fontSize: 12,
    color: '#9B9689',
    fontFamily: 'system-ui',
  }}>
    今天 · default 工作空间
  </div>
</div>

/* ❌ 错误 */
<h1 style={{ fontSize: 28, fontWeight: 700 }}>概览</h1>  // 字号太大、字重太重
<Title level={1}>概览</Title>                             // Ant Design Title 默认太重
```

---

## 九、AI 交互组件

### AI 洞察点（AI indicator dot）

```tsx
/* ✅ 正确：AI 相关内容用 clay 橙小圆点标识 */
<div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 9 }}>
  <div style={{
    width: 6,
    height: 6,
    borderRadius: '50%',
    background: '#D97757',    // clay 橙，不是绿色
  }} />
  <span style={{
    fontSize: 12,
    fontWeight: 500,
    color: '#1A1915',
    fontFamily: 'Georgia, serif',
  }}>
    AI 洞察
  </span>
</div>
```

### AI 洞察内容

```tsx
/* ✅ 正确：AI 分析文字用 Serif，有书写感 */
<div style={{
  fontSize: 11,
  color: '#5F5E5A',
  lineHeight: 1.7,
  fontFamily: 'Georgia, serif',  // AI内容用 Serif
}}>
  <WarningOutlined style={{ color: '#BA7517', fontSize: 12 }} />
  {' '}dws_gmv_realtime 检测到反压，建议并行度 4→8
</div>

/* ❌ 错误 */
<div style={{
  fontFamily: 'system-ui',    // AI 内容不用 sans
  fontWeight: 700,            // 不加粗
  color: '#D97757',           // 不用橙色做文字
}}>
```

### 行内操作菜单（In-place Action）

```tsx
/* ✅ 正确 */
<div style={{
  position: 'absolute',
  background: '#FAF9F6',
  border: '0.5px solid #E8E5DF',
  borderRadius: 6,
  padding: 4,
  display: 'flex',
  gap: 2,
  boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
  zIndex: 100,
}}>
  {['解释', '修改', 'Review'].map(action => (
    <button key={action} style={{
      background: 'transparent',
      border: 'none',
      borderRadius: 4,
      fontSize: 11,
      color: '#5F5E5A',
      padding: '3px 8px',
      cursor: 'pointer',
      fontFamily: 'system-ui',
    }}>
      {action}
    </button>
  ))}
</div>
```

### AI diff 展示

```tsx
/* ✅ 正确 */
<div style={{ fontFamily: "'Fira Code', monospace", fontSize: 12, lineHeight: 1.6 }}>
  <div style={{
    background: '#FCEBEB',
    color: '#791F1F',
    padding: '0 8px',
    textDecoration: 'line-through',
  }}>
    - SELECT user_id FROM orders
  </div>
  <div style={{
    background: '#EAF3DE',
    color: '#27500A',
    padding: '0 8px',
  }}>
    + SELECT user_id, count(*) as order_count FROM orders
  </div>
</div>

{/* diff 操作栏 */}
<div style={{
  display: 'flex',
  gap: 8,
  padding: '8px 0',
  borderTop: '0.5px solid #E8E5DF',
  marginTop: 8,
}}>
  <button style={{
    background: '#D97757', color: '#fff',
    border: 'none', borderRadius: 4,
    fontSize: 11, fontWeight: 500,
    padding: '3px 10px', cursor: 'pointer',
  }}>Accept</button>
  <button style={{
    background: 'transparent', color: '#5F5E5A',
    border: '0.5px solid #E8E5DF', borderRadius: 4,
    fontSize: 11, padding: '3px 10px', cursor: 'pointer',
  }}>Reject</button>
  <button style={{
    background: 'transparent', color: '#D97757',
    border: 'none', borderRadius: 4,
    fontSize: 11, padding: '3px 10px', cursor: 'pointer',
  }}>继续调整</button>
</div>
```

### 审核确认弹窗（Review & Confirm）

```tsx
/* ✅ 正确 */
<Modal
  title={
    <span style={{ fontFamily: 'Georgia, serif', fontWeight: 500, fontSize: 14 }}>
      部署确认
    </span>
  }
  open={open}
  footer={null}
  style={{ borderRadius: 12 }}
>
  {/* AI Review 结论 */}
  <div style={{ marginBottom: 16 }}>
    <div style={{
      fontSize: 11,
      color: '#9B9689',
      marginBottom: 8,
      fontFamily: 'system-ui',
    }}>
      AI Review 结论
    </div>
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div style={{ fontSize: 12, color: '#1A1915', fontFamily: 'Georgia, serif' }}>
        ✅ SQL 语法正确
      </div>
      <div style={{ fontSize: 12, color: '#1A1915', fontFamily: 'Georgia, serif' }}>
        ⚠️ 存在大状态风险，建议设置 TTL
      </div>
    </div>
  </div>

  {/* 高危确认输入 */}
  <div style={{
    background: '#F5F3EE',
    borderRadius: 6,
    padding: '12px 14px',
    marginBottom: 16,
  }}>
    <div style={{ fontSize: 11, color: '#9B9689', marginBottom: 8 }}>
      输入作业名称以确认
    </div>
    <input
      style={{
        width: '100%',
        border: '0.5px solid #E8E5DF',
        borderRadius: 6,
        padding: '6px 10px',
        fontSize: 12,
        background: '#FAF9F6',
        color: '#1A1915',
        fontFamily: 'system-ui',
        outline: 'none',
      }}
      placeholder="dws_user_count"
    />
  </div>

  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
    <button style={{
      background: 'transparent', color: '#5F5E5A',
      border: '0.5px solid #E8E5DF', borderRadius: 6,
      fontSize: 12, padding: '5px 13px', cursor: 'pointer',
    }}>取消</button>
    <button style={{
      background: confirmName === jobName ? '#D97757' : '#F0EDE6',
      color: confirmName === jobName ? '#fff' : '#C4C0B8',
      border: 'none', borderRadius: 6,
      fontSize: 12, fontWeight: 500,
      padding: '5px 13px', cursor: confirmName === jobName ? 'pointer' : 'not-allowed',
    }}>确认部署</button>
  </div>
</Modal>

/* ❌ 错误 */
if (window.confirm('确认部署？')) { deploy(); }
```

---

## 十、快捷操作卡（Quick Action Card）

```tsx
/* ✅ 正确 */
<div style={{
  background: '#FAF9F6',
  border: '0.5px solid #E8E5DF',
  borderRadius: 10,
  padding: '10px 12px',
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  cursor: 'pointer',
}}>
  <CodeOutlined style={{ fontSize: 15, color: '#D97757' }} />
  <div>
    <div style={{
      fontSize: 12,
      fontWeight: 500,
      color: '#1A1915',
      fontFamily: 'system-ui',
    }}>
      新建作业
    </div>
    <div style={{ fontSize: 10, color: '#9B9689', marginTop: 1 }}>
      AI 生成 SQL
    </div>
  </div>
</div>
```

---

## 十一、空状态

```tsx
/* ✅ 正确 */
<div style={{ textAlign: 'center', padding: '40px 0' }}>
  <div style={{
    fontSize: 14,
    fontWeight: 500,
    color: '#1A1915',
    fontFamily: 'Georgia, serif',
    marginBottom: 8,
  }}>
    还没有作业
  </div>
  <div style={{
    fontSize: 12,
    color: '#9B9689',
    marginBottom: 16,
    fontFamily: 'system-ui',
  }}>
    新建一个作业，让 AI 帮你生成 SQL。
  </div>
  <button style={{
    background: '#D97757', color: '#fff',
    border: 'none', borderRadius: 6,
    fontSize: 12, fontWeight: 500,
    padding: '6px 16px', cursor: 'pointer',
  }}>
    新建作业
  </button>
</div>

/* ❌ 错误 */
<Empty description="暂无数据" />   // 太冷漠，没有引导
<div>Nothing here yet</div>        // 不用英文
```
