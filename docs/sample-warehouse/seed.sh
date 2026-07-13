#!/bin/bash
# ============================================================
# 将当前目录内容散入工作空间根目录，然后重建索引
#
# 用法:
#   bash docs/sample-warehouse/seed.sh
#
# 前置条件: admin 服务已启动（默认 http://localhost:8080）
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USERNAME="${USERNAME:-admin}"
PASSWORD="${PASSWORD:-admin}"

# ── 1. 登录获取 token ─────────────────────────────────────────

echo ">>> 登录 ..."

LOGIN_RESP=$(curl -sS -D - -X POST "${BASE_URL}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}")

TOKEN=$(echo "$LOGIN_RESP" | grep -i '^Set-Cookie:' | grep -o 'lanting-token=[^;]*' | cut -d= -f2 | tr -d '\r')

if [ -z "$TOKEN" ]; then
  echo "✗ 登录失败，无法提取 token"
  echo "$LOGIN_RESP"
  exit 1
fi

echo "  ✓ token: ${TOKEN:0:24}..."

# ── 2. 获取工作空间根路径 ─────────────────────────────────────

echo ">>> 获取工作空间根路径 ..."

ROOT_RESP=$(curl -sS -X GET "${BASE_URL}/api/workspaces/root" \
  -H "lanting-token: ${TOKEN}")

CODE=$(echo "$ROOT_RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null || echo "-1")

if [ "$CODE" != "0" ]; then
  echo "✗ 获取根路径失败: $ROOT_RESP"
  exit 1
fi

DEFAULT_WORKSPACE_ROOT=$(echo "$ROOT_RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['data'])" 2>/dev/null)

if [ -z "$DEFAULT_WORKSPACE_ROOT" ]; then
  echo "✗ 无法解析根路径"
  exit 1
fi

echo "  ✓ 工作空间根路径: $DEFAULT_WORKSPACE_ROOT"

# ── 3. 拷贝文件 ───────────────────────────────────────────────

SOURCE_DIR="$(cd "$(dirname "$0")" && pwd)"

echo ">>> 拷贝 sample-warehouse → 工作空间 ..."

# 拷贝除 seed.sh 自身外的所有内容
rsync -a --exclude='seed.sh' "$SOURCE_DIR/" "$DEFAULT_WORKSPACE_ROOT/"

echo "  ✓ 拷贝完成"

# ── 4. 重建索引 ───────────────────────────────────────────────

echo ">>> 触发索引修复 ..."

REPAIR_RESP=$(curl -sS -X POST "${BASE_URL}/api/admin/fs/repair" \
  -H "lanting-token: ${TOKEN}")

REPAIR_CODE=$(echo "$REPAIR_RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('code',-1))" 2>/dev/null || echo "-1")

if [ "$REPAIR_CODE" = "0" ]; then
  echo "  ✓ 索引修复成功"
  echo "$REPAIR_RESP" | python3 -m json.tool 2>/dev/null || echo "$REPAIR_RESP"
else
  echo "  ⚠ 索引修复返回: $REPAIR_RESP"
fi

echo ""
echo "=== 完成 ==="
echo "文件已拷贝到: $DEFAULT_WORKSPACE_ROOT/"
echo "索引已重建"
