#!/usr/bin/env bash
# 框架 ↔ 产品边界门禁（规范见 docs/conventions.md §13）
#
# 靶子：产品代码（cn.xiaoqu.* / dev.xq.*、xq-* 模块）与框架代码（dev.ainer.*、ainer-* 模块）
# 将在同一个私有 monorepo 中开发，再由脚本把框架子集机械导出回公开仓。边界一旦在开发期被
# 打破，「抽取」就会退化成重写，因此本脚本把边界固化为可执行门禁。
#
# 检查三件事：
#   (a) 框架 main 源码（ainer-*/src/main/java/**、ainer-framework/**/src/main/java/**）
#       的 import 语句不得命中产品包根；
#   (b) 框架模块与根 pom 不得声明产品 groupId 的依赖；
#   (c) 框架 migration（ainer-*/src/main/resources/db/migration/*.sql 及 ainer-framework 下的
#       同路径）只能创建/修改框架自有表前缀的表，或显式白名单中的上游协议表。
#
# 产品包根、产品 groupId、框架表前缀与白名单表全部来自
# scripts/framework-boundary-targets.txt；新增产品包根/表前缀时改配置即可，不需要改本脚本。
#
# 用法：
#   scripts/check-framework-boundary.sh [--targets <清单文件>]
# 退出码：0 = 零违规并打印一行统计摘要；1 = 存在违规，或配置/语句无法判定（失败关闭）。
#
# 已知限制（当前仓库无此形态，出现时按失败关闭处理）：
#   * CREATE/ALTER/DROP TABLE 的表名必须与关键字同行，否则报「无法解析表名」；
#   * 只识别 DDL 语句文本，不解析 SQL 语义（存储过程内动态 DDL 不在覆盖范围）。
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
targets_file="$boot_root/scripts/framework-boundary-targets.txt"

usage() {
  echo "用法: $(basename "$0") [--targets <清单文件>]" >&2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --targets)
      [ "$#" -ge 2 ] || { usage; exit 1; }
      targets_file="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
done

# 失败关闭：配置缺失、无法解析的 DDL 一律报错退出，避免门禁静默放行。
fail_closed() {
  echo "[ainer-framework-boundary] ERROR: $*" >&2
  echo "[ainer-framework-boundary] 框架/产品边界门禁失败关闭（规范见 docs/conventions.md §13）" >&2
  exit 1
}

[ -f "$targets_file" ] || fail_closed "缺少目标清单文件：$targets_file"

# ---------------------------------------------------------------- 读取配置
product_packages=""
product_groups=""
framework_table_prefixes=""
allowed_tables=""

line_number=0
while IFS= read -r raw_line || [ -n "$raw_line" ]; do
  line_number=$((line_number + 1))
  line="${raw_line%%#*}"
  line="$(printf '%s' "$line" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  [ -n "$line" ] || continue
  read -r key value <<<"$line" || true
  [ -n "${value:-}" ] || fail_closed "$targets_file:$line_number 配置行缺少值：$line"
  case "$key" in
    package) product_packages="${product_packages}${value}"$'\n' ;;
    group) product_groups="${product_groups}${value}"$'\n' ;;
    table-prefix) framework_table_prefixes="${framework_table_prefixes}${value}"$'\n' ;;
    table-allow) allowed_tables="${allowed_tables}${value}"$'\n' ;;
    *) fail_closed "$targets_file:$line_number 未知配置键：$key（可用键：package/group/table-prefix/table-allow）" ;;
  esac
done <"$targets_file"

[ -n "$product_packages" ] || fail_closed "$targets_file 未配置任何产品包根（package）"
[ -n "$product_groups" ] || fail_closed "$targets_file 未配置任何产品 groupId（group）"
[ -n "$framework_table_prefixes" ] || fail_closed "$targets_file 未配置任何框架表前缀（table-prefix）"

# 产品包根 -> 正则（转义正则元字符，避免包根里的 `.` 被当成通配）
package_pattern=""
while IFS= read -r package_root; do
  [ -n "$package_root" ] || continue
  escaped="$(printf '%s' "$package_root" | sed -E 's/[][\\.^$*+?(){}|]/\\&/g')"
  if [ -z "$package_pattern" ]; then
    package_pattern="$escaped"
  else
    package_pattern="$package_pattern|$escaped"
  fi
done <<<"$product_packages"

is_product_group() {
  local candidate="$1" product_group
  while IFS= read -r product_group; do
    [ -n "$product_group" ] || continue
    [ "$candidate" = "$product_group" ] && return 0
  done <<<"$product_groups"
  return 1
}

# 表名是否被允许：命中框架自有前缀，或命中白名单（支持 `oauth2_*` 形式通配）。
is_allowed_table() {
  local table="$1" prefix pattern
  while IFS= read -r prefix; do
    [ -n "$prefix" ] || continue
    case "$table" in "$prefix"*) return 0 ;; esac
  done <<<"$framework_table_prefixes"
  while IFS= read -r pattern; do
    [ -n "$pattern" ] || continue
    case "$table" in $pattern) return 0 ;; esac
  done <<<"$allowed_tables"
  return 1
}

# ---------------------------------------------------------------- 收集文件清单
source_file_list="$(mktemp "${TMPDIR:-/tmp}/ainer-boundary-sources.XXXXXX")"
pom_file_list="$(mktemp "${TMPDIR:-/tmp}/ainer-boundary-poms.XXXXXX")"
migration_file_list="$(mktemp "${TMPDIR:-/tmp}/ainer-boundary-migrations.XXXXXX")"
cleanup() {
  case "$source_file_list" in
    "${TMPDIR:-/tmp}"/ainer-boundary-*) rm -f -- "$source_file_list" "$pom_file_list" "$migration_file_list" ;;
    *) echo "[ainer-framework-boundary] 拒绝清理非预期路径：$source_file_list" >&2 ;;
  esac
}
trap cleanup EXIT

for source_root in "$boot_root"/ainer-*/src/main/java "$boot_root"/ainer-framework/*/src/main/java; do
  [ -d "$source_root" ] || continue
  find "$source_root" -type f -name '*.java' -print >>"$source_file_list"
done

for module_dir in "$boot_root"/ainer-*; do
  [ -d "$module_dir" ] || continue
  # 生成物模板不是 reactor 模块（release-artifacts.txt 亦排除），其 pom 归属被生成的产品，
  # 不属于「框架模块 pom」，因此不参与本项检查。
  find "$module_dir" -name pom.xml -type f \
    -not -path '*/target/*' \
    -not -path '*/src/main/resources/templates/*' \
    -print >>"$pom_file_list"
done
if [ -f "$boot_root/pom.xml" ]; then
  printf '%s\n' "$boot_root/pom.xml" >>"$pom_file_list"
fi

for migration_root in \
  "$boot_root"/ainer-*/src/main/resources/db/migration \
  "$boot_root"/ainer-framework/*/src/main/resources/db/migration; do
  [ -d "$migration_root" ] || continue
  find "$migration_root" -maxdepth 1 -type f -name '*.sql' -print >>"$migration_file_list"
done

# ---------------------------------------------------------------- (a) 源码 import
violations=0
source_scanned=0
while IFS= read -r source_file; do
  [ -n "$source_file" ] || continue
  source_scanned=$((source_scanned + 1))
  matches="$(grep -nE "^[[:space:]]*import[[:space:]]+(static[[:space:]]+)?(${package_pattern})\." "$source_file" || true)"
  [ -n "$matches" ] || continue
  while IFS= read -r match; do
    [ -n "$match" ] || continue
    echo "[ainer-framework-boundary] 违规(框架源码依赖产品包): ${source_file#"$boot_root"/}:${match}"
    violations=$((violations + 1))
  done <<<"$matches"
done <"$source_file_list"

# ---------------------------------------------------------------- (b) pom 依赖
pom_scanned=0
while IFS= read -r pom_file; do
  [ -n "$pom_file" ] || continue
  pom_scanned=$((pom_scanned + 1))
  matches="$(grep -nE '<groupId>[[:space:]]*[^<[:space:]]+[[:space:]]*</groupId>' "$pom_file" || true)"
  [ -n "$matches" ] || continue
  while IFS= read -r match; do
    [ -n "$match" ] || continue
    text="${match#*:}"
    # 判定该 groupId 是否落在 XML 注释里：看它之前的文本是否「已开注释未闭合」。
    prefix="${text%%<groupId>*}"
    case "$prefix" in
      *'<!--'*) case "$prefix" in *'-->'*) ;; *) continue ;; esac ;;
    esac
    # 取该行第一个 groupId 的值：不假设 <groupId> 在行首（pom 允许一行写完整 dependency）。
    candidate="$(printf '%s' "$text" \
      | grep -oE '<groupId>[[:space:]]*[^<[:space:]]+[[:space:]]*</groupId>' \
      | head -n 1 \
      | sed -E 's/^<groupId>[[:space:]]*//; s/[[:space:]]*<\/groupId>$//')"
    if is_product_group "$candidate"; then
      echo "[ainer-framework-boundary] 违规(框架 pom 声明产品 groupId 依赖): ${pom_file#"$boot_root"/}:${match}"
      violations=$((violations + 1))
    fi
  done <<<"$matches"
done <"$pom_file_list"

# ---------------------------------------------------------------- (c) migration 表名
migration_scanned=0
table_statements=0
while IFS= read -r migration_file; do
  [ -n "$migration_file" ] || continue
  migration_scanned=$((migration_scanned + 1))
  matches="$(grep -inE '^[[:space:]]*(create|alter|drop)[[:space:]]+table([[:space:]]|$)' "$migration_file" || true)"
  [ -n "$matches" ] || continue
  while IFS= read -r match; do
    [ -n "$match" ] || continue
    table_statements=$((table_statements + 1))
    statement="${match#*:}"
    # PostgreSQL 未加引号的标识符折叠为小写，因此统一小写后再比对白名单（语义等价）。
    remainder="$(printf '%s' "$statement" \
      | tr '[:upper:]' '[:lower:]' \
      | sed -E 's/^[[:space:]]*(create|alter|drop)[[:space:]]+table[[:space:]]+//' \
      | sed -E 's/^(unlogged[[:space:]]+|temporary[[:space:]]+|temp[[:space:]]+)//' \
      | sed -E 's/^(if[[:space:]]+not[[:space:]]+exists[[:space:]]+|if[[:space:]]+exists[[:space:]]+)//' \
      | sed -E 's/^only[[:space:]]+//' \
      | sed -E 's/^[[:space:]]+//')"
    remainder="${remainder%%(*}"
    remainder="${remainder%%;*}"
    # 关键字与表名不同行时无法可靠取表名；失败关闭，避免静默漏检。
    case "$remainder" in
      create[[:space:]]table*|alter[[:space:]]table*|drop[[:space:]]table*|'')
        fail_closed "${migration_file#"$boot_root"/}:${match%%:*} 无法解析表名（表名需与 CREATE/ALTER/DROP TABLE 关键字同行）" ;;
    esac
    IFS=',' read -r -a table_parts <<<"$remainder"
    for table_part in "${table_parts[@]}"; do
      table="$(printf '%s' "$table_part" \
        | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' \
        | sed -E 's/^"([^"]+)".*$/\1/' \
        | sed -E 's/^[^".]*\.//' \
        | sed -E 's/^([a-z0-9_]+).*$/\1/')"
      [ -n "$table" ] || fail_closed "$migration_file:$match 无法解析表名（表名需与 CREATE/ALTER/DROP TABLE 同行）"
      if ! is_allowed_table "$table"; then
        echo "[ainer-framework-boundary] 违规(框架 migration 触碰非框架表): ${migration_file#"$boot_root"/}:${match%%:*} 表名=${table}（框架只允许 $(printf '%s' "$framework_table_prefixes" | tr '\n' ' ')前缀及白名单表，产品表必须在产品 migration 中创建）"
        violations=$((violations + 1))
      fi
    done
  done <<<"$matches"
done <"$migration_file_list"

# ---------------------------------------------------------------- 结果
if [ "$violations" -gt 0 ]; then
  echo "[ainer-framework-boundary] 发现 $violations 处框架/产品边界违规（规范见 docs/conventions.md §13）" >&2
  exit 1
fi

echo "[ainer-framework-boundary] 通过：框架 main Java 文件 $source_scanned 个、框架与根 pom $pom_scanned 个、框架 migration $migration_scanned 个（DDL 语句 $table_statements 条），违规 0 处"
