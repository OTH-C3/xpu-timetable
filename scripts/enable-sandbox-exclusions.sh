#!/usr/bin/env bash
# ============================================================
# 让 git / adb 在 WorkBuddy 沙箱外运行（写入 excludedCommands）
#
# 作用：把 "git" 与 "adb" 加入 ~/.workbuddy/settings.json 的 sandbox.excludedCommands，
#       使这两个命令不再受 bubblewrap 沙箱限制，从而让 Agent 能：
#         - 用主机上的 git 做提交 / 推送 GitHub
#         - 用 adb 安装 APK 到真机 / 抓取日志
#
# 用法（任选其一）：
#   bash enable-sandbox-exclusions.sh              # 应用改动（仅在确有改动时备份）
#   bash enable-sandbox-exclusions.sh --undo       # 回滚到最近一次「改动前」备份
#   bash enable-sandbox-exclusions.sh --show       # 只查看当前 sandbox 配置
#   bash enable-sandbox-exclusions.sh --path FILE  # 对指定文件操作（自测用）
#
# 安全设计：
#   1) 先判断是否需要改动：已是目标状态则直接退出，不备份、不写入（保证 --undo 语义正确）
#   2) 仅在确实要改时备份为 settings.json.bak-YYYYmmdd-HHMMSS（备份必为「改动前」状态）
#   3) 幂等：已存在的命令不重复添加
#   4) 只动 sandbox.excludedCommands 一个键，其余配置原样保留
#   5) 原文件不是合法 JSON 时中止，且不做任何修改
#   6) 写入用「同目录临时文件 + 原子替换」，写入后再复检一次
#   7) 不联网、不下载、不需要 sudo
# ============================================================

set -euo pipefail

TARGET="${HOME}/.workbuddy/settings.json"
MODE="apply"
WANTED=("git" "adb")

while [ $# -gt 0 ]; do
  case "$1" in
    --undo) MODE="undo"; shift ;;
    --show) MODE="show"; shift ;;
    --path) TARGET="$2"; shift 2 ;;
    -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
    *) echo "未知参数：$1（用 -h 查看用法）" >&2; exit 2 ;;
  esac
done

say() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m[中止] %s\033[0m\n' "$*" >&2; exit 1; }

command -v python3 >/dev/null 2>&1 || die "未找到 python3，无法安全修改 JSON。请先安装：sudo pacman -S python"

# ---------- 只查看 ----------
if [ "$MODE" = "show" ]; then
  [ -f "$TARGET" ] || die "文件不存在：$TARGET"
  say "当前配置：$TARGET"
  python3 - "$TARGET" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as f:
    data = json.load(f)
print(json.dumps(data.get("sandbox", {}), indent=2, ensure_ascii=False))
PY
  exit 0
fi

# ---------- 回滚 ----------
if [ "$MODE" = "undo" ]; then
  LATEST="$(ls -1t "${TARGET}".bak-* 2>/dev/null | head -n 1 || true)"
  [ -n "$LATEST" ] || die "未找到备份文件（${TARGET}.bak-*），无法回滚"
  say "回滚到改动前状态"
  echo "  备份：$LATEST"
  echo "  目标：$TARGET"
  cp -a "$LATEST" "$TARGET"
  python3 -c "import json,sys; d=json.load(open(sys.argv[1],encoding='utf-8')); print('回滚后 sandbox.excludedCommands =', d.get('sandbox',{}).get('excludedCommands','（无）'))" "$TARGET"
  say "已回滚。请重启 WorkBuddy 生效"
  exit 0
fi

# ---------- 应用 ----------
# 前提检查：这些命令必须先在主机上真实存在，否则「排除沙箱」不会带来可用性
say "前提检查"
if command -v git >/dev/null 2>&1; then
  echo "  git ：$(command -v git)  [$(git --version 2>/dev/null | head -n 1)]"
else
  echo "  git ：未找到 → 请先安装：sudo pacman -S git"
  echo "        （未安装时，本脚本仍会写入排除项，但 git 依然不可用）"
fi
if command -v adb >/dev/null 2>&1; then
  echo "  adb ：$(command -v adb)"
elif [ -x "${HOME}/Android/Sdk/platform-tools/adb" ]; then
  echo "  adb ：${HOME}/Android/Sdk/platform-tools/adb（未加入 PATH，Agent 会按绝对路径调用）"
else
  echo "  adb ：未找到 → 请确认 Android SDK 的 platform-tools 已安装"
fi

[ -d "$(dirname "$TARGET")" ] || die "目录不存在：$(dirname "$TARGET")（请确认 WorkBuddy 至少运行过一次）"

if [ ! -f "$TARGET" ]; then
  say "目标文件不存在，将新建：$TARGET"
  printf '{}\n' > "$TARGET"
fi

# 先判断是否需要改动（同时完成 JSON 合法性校验）
if ! MISSING="$(python3 - "$TARGET" "${WANTED[@]}" <<'PY' 2>&1
import json, sys
path, wanted = sys.argv[1], sys.argv[2:]
try:
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
except Exception as e:
    print(f"JSON 解析失败：{e}")
    sys.exit(3)
sandbox = data.get("sandbox")
excluded = sandbox.get("excludedCommands", []) if isinstance(sandbox, dict) else []
if not isinstance(excluded, list):
    excluded = []
missing = [c for c in wanted if c not in excluded]
print("NONE" if not missing else " ".join(missing))
PY
)"; then
  die "现有配置无法安全处理，已中止（未做任何修改）：$TARGET
$MISSING"
fi

if [ "$MISSING" = "NONE" ]; then
  say "已是目标状态（excludedCommands 已包含：${WANTED[*]}），无需改动"
  echo "未创建备份、未写入文件。用 --show 可查看当前配置。"
  exit 0
fi

say "需要添加：$MISSING"
BACKUP="${TARGET}.bak-$(date +%Y%m%d-%H%M%S)"
cp -a "$TARGET" "$BACKUP"
echo "已备份原配置（改动前状态）→ $BACKUP"

python3 - "$TARGET" "${WANTED[@]}" <<'PY'
import json, os, sys, tempfile

path, wanted = sys.argv[1], sys.argv[2:]

with open(path, encoding='utf-8') as f:
    data = json.load(f)

sandbox = data.get("sandbox")
if not isinstance(sandbox, dict):
    sandbox = {}
    data["sandbox"] = sandbox

excluded = sandbox.get("excludedCommands")
if not isinstance(excluded, list):
    excluded = []
    sandbox["excludedCommands"] = excluded

before = list(excluded)
for cmd in wanted:
    if cmd not in excluded:
        excluded.append(cmd)
        print(f"  + 新增：{cmd}")
    else:
        print(f"  = 已存在，跳过：{cmd}")

print("  改动前：", before if before else "（无）")
print("  改动后：", excluded)

d = os.path.dirname(os.path.abspath(path))
mode = os.stat(path).st_mode
fd, tmp = tempfile.mkstemp(dir=d, prefix=".settings-", suffix=".tmp")
try:
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
    os.chmod(tmp, mode & 0o777)
    os.replace(tmp, path)
except Exception:
    try:
        os.unlink(tmp)
    except OSError:
        pass
    raise
print("  已原子写入")
PY

# 写入后复检
python3 -c "
import json,sys
d=json.load(open(sys.argv[1],encoding='utf-8'))
ex=d.get('sandbox',{}).get('excludedCommands',[])
missing=[c for c in sys.argv[2:] if c not in ex]
print('复检：excludedCommands =',ex)
sys.exit(1 if missing else 0)
" "$TARGET" "${WANTED[@]}" || die "写入后复检失败，请用 --undo 回滚"

# 结构完整性复检：确认其余配置未被动到
python3 - "$TARGET" <<'PY'
import json, sys
d = json.load(open(sys.argv[1], encoding='utf-8'))
print("结构复检：顶层键 =", list(d.keys()))
sb = d.get("sandbox", {})
print("  sandbox 键 =", list(sb.keys()))
PY

say "完成"
cat <<'TIP'

下一步：
  1) 重启 WorkBuddy（或重新加载设置），让新的沙箱配置生效；
  2) 回到对话告诉我，我会实测 `git --version` 与 `adb devices` 验证已脱离沙箱；
  3) 撤销：bash scripts/enable-sandbox-exclusions.sh --undo

代价说明：excludedCommands 中的命令以主机完整权限运行（沙箱隔离对其失效）。
      git 的理论风险来自不受信任仓库的 hooks —— 本项目只操作自建仓库，风险可控。
TIP
