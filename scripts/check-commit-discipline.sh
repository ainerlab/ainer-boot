#!/usr/bin/env bash
# 提交纪律检查（conventions.md §12）：
#   1. 提交信息必须符合 `type(scope): 中文描述` 规范；
#   2. docs 类型提交只允许修改文档路径（docs/**、README/CHANGELOG/CONTRIBUTING/AGENTS.md）。
# 用法: scripts/check-commit-discipline.sh <base-sha> <head-sha>
# merge 提交跳过格式检查；违规输出明细并以非零退出。
set -euo pipefail

base=${1:?用法: check-commit-discipline.sh <base-sha> <head-sha>}
head=${2:?用法: check-commit-discipline.sh <base-sha> <head-sha>}

allowed_doc_paths='^(docs/|README\.md$|CHANGELOG\.md$|CONTRIBUTING\.md$|AGENTS\.md$)'
type_re='^(feat|fix|docs|style|refactor|perf|test|chore|ci|build|revert)(\([a-z0-9.,-]+\))?: .+'

violations=0
while IFS= read -r line; do
  [ -z "$line" ] && continue
  commit=${line%% *}
  subject=${line#* }
  # 合并提交（Merge pull request…）不适用单提交规范
  if [[ "$subject" =~ ^Merge[[:space:]] ]]; then
    continue
  fi
  if ! [[ "$subject" =~ $type_re ]]; then
    echo "✗ $commit 提交信息不符合 'type(scope): 中文描述' 规范：$subject"
    violations=$((violations + 1))
    continue
  fi
  type=${subject%%[:(]*}
  if [ "$type" = "docs" ]; then
    while IFS= read -r path; do
      [ -z "$path" ] && continue
      if ! [[ "$path" =~ $allowed_doc_paths ]]; then
        echo "✗ $commit 是 docs 类型提交但修改了非文档文件：$path"
        violations=$((violations + 1))
      fi
    done < <(git diff-tree --no-commit-id --name-only -r --no-renames "$commit")
  fi
done < <(git log --format='%h %s' "$base..$head")

if [ "$violations" -gt 0 ]; then
  echo "提交纪律检查失败：$violations 处违规（规范见 docs/conventions.md §12 Git）"
  exit 1
fi
echo "✓ 提交纪律检查通过"
