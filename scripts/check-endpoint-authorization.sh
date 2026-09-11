#!/usr/bin/env bash
# 端点授权声明门禁（规范见 docs/conventions.md §9、docs/security.md §3.4）
#
# 靶子：`@AinerAuthorize` 是逐方法可选的注解——没有它的 Controller 方法会落到
# Resource Server 的 anyRequest().authenticated()，即「只要求登录、不要求任何权限」。
# 也就是说新增一个 Controller 方法若忘了写声明，编译期、启动期和既有 CI 都不会失败，
# 而它对所有已认证主体开放。本脚本把这条规则固化成可执行门禁。
#
# 检查三件事（对扫描范围内的每个 @RestController/@Controller）：
#   (a) 方法级 `@AinerAuthorize(permission=...)` → 通过（决策引擎粗粒度闸门）；
#   (b) 方法级或类级 `@EndpointAccess(kind=..., reason=...)` → 通过（显式访问声明：
#       PUBLIC 匿名 / AUTHENTICATED 仅要求登录 / DELEGATED 由应用服务或专用安全链强制）；
#   (c) 两者都没有 → 查 scripts/endpoint-authorization-whitelist.txt 是否登记
#       （`类#方法`，支持 * 通配）；登记 → 通过，未登记 → 违规并打印 `文件:行`。
#
# 扫描范围：
#   * reactor 模块的 `src/main/java/**/*.java`（ainer-*/、ainer-framework/*/）；
#   * `ainer-initializer/src/main/resources/templates/v2/**/*.java`——生成出来的工程会编译
#     这些 Controller 并 @Import(AuthorizationModuleConfiguration)，属于「未来会跑的端点」。
#
# 不扫描（每条都能说清理由）：
#   * `src/test/**`：测试夹具端点不进入任何交付制品；
#   * `templates/v1/**`：templates/v1/pom.xml 不含 ainer-starter-security /
#     ainer-module-authorization，v1 消费者既拿不到注解类也不装配授权拦截器（ADR-0036 通道）；
#   * `@RestControllerAdvice` / `@ControllerAdvice`：异常处理器不是端点。
#
# 已知限制（当前仓库无此形态；出现即按失败关闭处理，必要时先扩脚本）：
#   * 只认显式写在类/方法上的注解，不解析继承来的映射或注解（基类 Controller、接口默认实现）；
#   * 嵌套类型（其他类内部的 static Controller）同样扫描：解析器按花括号深度维护类型作用域栈；
#   * `@AinerAuthorize` 只支持方法级（`@Target(ElementType.METHOD)`），类级写法编译期就会失败，
#     脚本遇到类级写法直接报违规；`@EndpointAccess` 支持类级，类级声明对该类所有 handler 生效；
#   * Java 文本块（`"""`）会让字符串状态机失准，本脚本按「main 源码不含文本块」处理，
#     新增文本块时需同步扩展 strip_comments。
#
# 用法：
#   scripts/check-endpoint-authorization.sh [--whitelist <白名单文件>]
# 退出码：0 = 零违规并打印一行统计摘要；1 = 存在违规，或白名单/源文件无法判定（失败关闭）。
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
whitelist_file="$boot_root/scripts/endpoint-authorization-whitelist.txt"

usage() {
  echo "用法: $(basename "$0") [--whitelist <白名单文件>]" >&2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --whitelist)
      [ "$#" -ge 2 ] || { usage; exit 1; }
      whitelist_file="$2"
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

# 失败关闭：白名单缺失/格式错误、扫描目录缺失一律报错退出，避免门禁静默放行。
fail_closed() {
  echo "[ainer-endpoint-authorization] ERROR: $*" >&2
  echo "[ainer-endpoint-authorization] 端点授权门禁失败关闭（规范见 docs/conventions.md §9）" >&2
  exit 1
}

[ -f "$whitelist_file" ] || fail_closed "缺少白名单文件：$whitelist_file"

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/ainer-endpoint-authorization.XXXXXX")"
cleanup() {
  case "$work_dir" in
    "${TMPDIR:-/tmp}"/ainer-endpoint-authorization.*) rm -rf -- "$work_dir" ;;
    *) echo "[ainer-endpoint-authorization] 拒绝清理非预期路径：$work_dir" >&2 ;;
  esac
}
trap cleanup EXIT

awk_program="$work_dir/scan.awk"
cat >"$awk_program" <<'AWK'
# 扫描单个 Java 源文件，输出：
#   handler <行号> <简单类名> <全限定类名> <方法名> <ainerAuthorize|endpointAccess|classLevel|undeclared|endpointAccessNoReason>
#   error   <行号> <类名> - <说明>
# 解析方式：先按字符状态机去掉注释（字符串字面量内容也丢弃，避免 reason 文本里的注解名与花括号
# 污染判定），再按花括号深度维护「类型作用域栈」：每个类型体（含嵌套类型）的直接成员层累积
# 「注解 + 签名」缓冲，缓冲里带 HTTP 映射注解的成员即 handler。因此注解写在映射注解之前或之后、
# 签名跨多行、Controller 嵌套在其他类里都能正确归属。

function strip_comments(line,   out, i, n, c) {
  out = ""
  i = 1
  n = length(line)
  while (i <= n) {
    c = substr(line, i, 1)
    if (in_block) {
      if (c == "*" && substr(line, i + 1, 1) == "/") {
        in_block = 0
        i += 2
      } else {
        i++
      }
      continue
    }
    if (in_string) {
      # 字符串字面量内容整体丢弃（只保留引号）：否则 reason/路径里的注解名、`{`、`}` 会污染
      # 注解判定与花括号深度。
      if (c == "\\") {
        i += 2
        continue
      }
      if (c == "\"") {
        in_string = 0
        out = out c
      }
      i++
      continue
    }
    if (c == "\"") {
      in_string = 1
      out = out c
      i++
      continue
    }
    if (c == "/" && substr(line, i + 1, 1) == "*") {
      in_block = 1
      i += 2
      continue
    }
    if (c == "/" && substr(line, i + 1, 1) == "/") {
      break
    }
    out = out c
    i++
  }
  return out
}

# 去掉注解（含括号参数，按嵌套深度配对），只留签名，便于取方法名与类型名。
function strip_annotations(text,   out, i, n, c, j, k, depth, d) {
  out = ""
  i = 1
  n = length(text)
  while (i <= n) {
    c = substr(text, i, 1)
    if (c != "@") {
      out = out c
      i++
      continue
    }
    j = i + 1
    while (j <= n && substr(text, j, 1) ~ /[A-Za-z0-9_.]/) {
      j++
    }
    k = j
    while (k <= n && substr(text, k, 1) ~ /[[:space:]]/) {
      k++
    }
    if (k <= n && substr(text, k, 1) == "(") {
      depth = 0
      while (k <= n) {
        d = substr(text, k, 1)
        if (d == "(") {
          depth++
        } else if (d == ")") {
          depth--
          if (depth == 0) {
            k++
            break
          }
        }
        k++
      }
    }
    out = out " "
    i = k
  }
  return out
}

# 成员缓冲是类型声明时返回类型名（否则空串）。允许 Initializer 模板占位符
# （例如 {{entity.className}}Controller）。
function type_decl_name(text,   sig, prefix, matched) {
  sig = strip_annotations(text)
  if (!match(sig, /(class|interface|enum|record)[[:space:]]+[A-Za-z0-9_{}.]+/)) {
    return ""
  }
  prefix = (RSTART > 1) ? substr(sig, RSTART - 1, 1) : " "
  # 排除 `Foo.class` 这类限定名，只认真实类型声明
  if (prefix == "." || prefix ~ /[A-Za-z0-9_]/) {
    return ""
  }
  matched = substr(sig, RSTART, RLENGTH)
  sub(/^(class|interface|enum|record)[[:space:]]+/, "", matched)
  return matched
}

function push_type(buffer, name,   is_ctrl, declared, fqcn_prefix) {
  is_ctrl = 0
  declared = 0
  if (buffer !~ /@(RestController|Controller)Advice/) {
    if (buffer ~ /@RestController/ || buffer ~ /@Controller/) {
      is_ctrl = 1
    }
  }
  if (buffer ~ /@EndpointAccess[[:space:]]*\(/) {
    declared = 1
    if (buffer !~ /reason[[:space:]]*=/) {
      printf "error\t%d\t%s\t%s\t-\t类级 @EndpointAccess 缺少 reason（必填：说明为什么该 Controller 不需要 @AinerAuthorize）\n", \
        memberLine, name, name
    }
  }
  if (buffer ~ /@AinerAuthorize[[:space:]]*\(/) {
    printf "error\t%d\t%s\t%s\t-\t@AinerAuthorize 只支持方法级（@Target(ElementType.METHOD)），类级写法在编译期即失败\n", \
      memberLine, name, name
  }
  typeCount++
  t_name[typeCount] = name
  t_ctrl[typeCount] = is_ctrl
  t_decl[typeCount] = declared
  t_depth[typeCount] = depth
  if (typeCount > 1) {
    fqcn_prefix = t_fqcn[typeCount - 1] "."
  } else {
    fqcn_prefix = (packageName == "" ? "" : packageName ".")
  }
  t_fqcn[typeCount] = fqcn_prefix name
}

function evaluate_member(   text, sig, name, mechanism, line) {
  text = memberBuffer
  if (text !~ /@(Get|Post|Put|Delete|Patch)Mapping/ && text !~ /@RequestMapping/) {
    return
  }
  sig = strip_annotations(text)
  name = ""
  if (match(sig, /[A-Za-z_][A-Za-z0-9_]*[[:space:]]*\(/)) {
    name = substr(sig, RSTART, RLENGTH)
    sub(/[[:space:]]*\($/, "", name)
  }
  if (name == "") {
    return
  }
  if (text ~ /@AinerAuthorize[[:space:]]*\(/) {
    mechanism = "ainerAuthorize"
  } else if (text ~ /@EndpointAccess[[:space:]]*\(/) {
    if (text !~ /reason[[:space:]]*=/) {
      mechanism = "endpointAccessNoReason"
    } else {
      mechanism = "endpointAccess"
    }
  } else if (t_decl[typeCount]) {
    mechanism = "classLevel"
  } else {
    mechanism = "undeclared"
  }
  line = (memberMappingLine > 0 ? memberMappingLine : memberLine)
  printf "handler\t%d\t%s\t%s\t%s\t%s\n", line, t_name[typeCount], t_fqcn[typeCount], name, mechanism
}

{
  raw[NR] = strip_comments($0)
}

END {
  depth = 0
  typeCount = 0
  packageName = ""
  memberBuffer = ""
  memberLine = 0
  memberMappingLine = 0

  for (i = 1; i <= NR; i++) {
    text = raw[i]
    before = depth
    memberDepth = (typeCount > 0) ? t_depth[typeCount] : 0

    if (before == 0 && typeCount == 0 && memberBuffer == "" \
        && text ~ /^[[:space:]]*(package|import)[[:space:]]/) {
      if (text ~ /^[[:space:]]*package[[:space:]]/) {
        packageName = text
        sub(/^[[:space:]]*package[[:space:]]+/, "", packageName)
        sub(/[[:space:]]*;.*$/, "", packageName)
      }
    } else if (before == memberDepth) {
      if (memberBuffer == "") {
        memberLine = i
      }
      memberBuffer = memberBuffer " " text
      if (memberMappingLine == 0 \
          && (text ~ /@(Get|Post|Put|Delete|Patch)Mapping/ || text ~ /@RequestMapping/)) {
        memberMappingLine = i
      }
    }

    opens = text
    nOpen = gsub(/\{/, "", opens)
    closes = text
    nClose = gsub(/\}/, "", closes)
    depth = before + nOpen - nClose

    if (depth > before) {
      # 某个成员打开了 body：类型声明 → 入栈；Controller 的 handler → 判定
      if (before == memberDepth && memberBuffer != "") {
        declName = type_decl_name(memberBuffer)
        if (declName != "") {
          push_type(memberBuffer, declName)
        } else if (t_ctrl[typeCount]) {
          evaluate_member()
        }
        memberBuffer = ""
        memberLine = 0
        memberMappingLine = 0
      }
      continue
    }

    if (depth < before) {
      while (typeCount > 0 && t_depth[typeCount] > depth) {
        typeCount--
      }
      memberBuffer = ""
      memberLine = 0
      memberMappingLine = 0
      continue
    }

    # 深度不变且成员以 `;` 结束：字段或抽象方法声明
    if (before == memberDepth && memberBuffer != "" && memberBuffer ~ /;[[:space:]]*$/) {
      if (type_decl_name(memberBuffer) == "" && t_ctrl[typeCount]) {
        evaluate_member()
      }
      memberBuffer = ""
      memberLine = 0
      memberMappingLine = 0
    }
  }
}
AWK

java_file_list="$work_dir/java-files.txt"
: >"$java_file_list"
for source_root in "$boot_root"/ainer-*/src/main/java "$boot_root"/ainer-framework/*/src/main/java; do
  [ -d "$source_root" ] || continue
  find "$source_root" -type f -name '*.java' -print >>"$java_file_list"
done
v2_templates="$boot_root/ainer-initializer/src/main/resources/templates/v2"
[ -d "$v2_templates" ] || fail_closed "缺少 Initializer v2 模板目录：$v2_templates"
find "$v2_templates" -type f -name '*.java' -print >>"$java_file_list"

# ---------------------------------------------------------------- 读取白名单
wl_class=()
wl_method=()
wl_used=()
wl_lines=()
wl_specs=()
line_number=0
while IFS= read -r raw_line || [ -n "$raw_line" ]; do
  line_number=$((line_number + 1))
  case "$raw_line" in
    '#'*) continue ;;
  esac
  trimmed="$(printf '%s' "$raw_line" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  [ -n "$trimmed" ] || continue
  spec="${trimmed%%[[:space:]]*}"
  reason="${trimmed#"$spec"}"
  reason="$(printf '%s' "$reason" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  case "$spec" in
    *'#'*) ;;
    *) fail_closed "$whitelist_file:$line_number 登记项缺少「类#方法」分隔符：$spec" ;;
  esac
  entry_class="${spec%%#*}"
  entry_method="${spec#*#}"
  case "$entry_method" in
    *'#'*) fail_closed "$whitelist_file:$line_number 登记项含多个「#」：$spec" ;;
  esac
  [ -n "$entry_class" ] || fail_closed "$whitelist_file:$line_number 登记项类名为空：$spec"
  [ -n "$entry_method" ] || fail_closed "$whitelist_file:$line_number 登记项方法名为空：$spec"
  [ -n "$reason" ] || fail_closed "$whitelist_file:$line_number 登记项缺少理由（理由必填，说明为什么可以不写声明）：$spec"
  wl_class+=("$entry_class")
  wl_method+=("$entry_method")
  wl_used+=(0)
  wl_lines+=("$line_number")
  wl_specs+=("$spec")
done <"$whitelist_file"

has_wildcard() {
  case "$1" in
    *'*'*|*'?'*|*'['*) return 0 ;;
  esac
  return 1
}

glob_match() {
  case "$1" in
    $2) return 0 ;;
  esac
  return 1
}

# ---------------------------------------------------------------- 扫描
violations=0
handler_total=0
declared_ainer_authorize=0
declared_endpoint_access=0
declared_class_level=0
whitelisted=0
controller_files=0
scanned_files=0

while IFS= read -r java_file; do
  [ -n "$java_file" ] || continue
  scanned_files=$((scanned_files + 1))
  relative_file="${java_file#"$boot_root"/}"
  while IFS=$'\t' read -r kind field_a field_b field_c field_d field_e; do
    [ -n "${kind:-}" ] || continue
    case "$kind" in
      error)
        echo "[ainer-endpoint-authorization] 违规(无法判定的类级声明): ${relative_file}:${field_a} ${field_b} ${field_e}"
        violations=$((violations + 1))
        ;;
      handler)
        handler_total=$((handler_total + 1))
        handler_line="$field_a"
        handler_class="$field_b"
        handler_fqcn="$field_c"
        handler_method="$field_d"
        mechanism="${field_e:-}"
        case "$mechanism" in
          ainerAuthorize) declared_ainer_authorize=$((declared_ainer_authorize + 1)) ;;
          endpointAccess) declared_endpoint_access=$((declared_endpoint_access + 1)) ;;
          classLevel) declared_class_level=$((declared_class_level + 1)) ;;
          endpointAccessNoReason)
            echo "[ainer-endpoint-authorization] 违规(@EndpointAccess 缺少 reason): ${relative_file}:${handler_line} ${handler_class}#${handler_method} 必须写清为什么该端点不需要 @AinerAuthorize"
            violations=$((violations + 1))
            ;;
        esac
        whitelist_hit=""
        index=0
        while [ "$index" -lt "${#wl_class[@]}" ]; do
          entry_class="${wl_class[$index]}"
          entry_method="${wl_method[$index]}"
          class_matched=0
          if glob_match "$handler_class" "$entry_class" || glob_match "$handler_fqcn" "$entry_class"; then
            class_matched=1
          fi
          if [ "$class_matched" -eq 1 ] && glob_match "$handler_method" "$entry_method"; then
            wl_used[$index]=1
            if [ -z "$whitelist_hit" ]; then
              whitelist_hit="${wl_specs[$index]}"
            fi
          fi
          index=$((index + 1))
        done
        if [ "$mechanism" = "undeclared" ]; then
          if [ -n "$whitelist_hit" ]; then
            whitelisted=$((whitelisted + 1))
          else
            echo "[ainer-endpoint-authorization] 违规(未声明授权的端点): ${relative_file}:${handler_line} ${handler_class}#${handler_method} 既没有 @AinerAuthorize，也没有 @EndpointAccess，且未登记在 ${whitelist_file#"$boot_root"/}"
            violations=$((violations + 1))
          fi
        fi
        ;;
    esac
  done < <(awk -f "$awk_program" "$java_file")
done <"$java_file_list"

# 过期登记：无通配的登记项必须至少命中一个真实 handler，否则白名单会烂成"永久豁免"。
index=0
while [ "$index" -lt "${#wl_class[@]}" ]; do
  entry_class="${wl_class[$index]}"
  entry_method="${wl_method[$index]}"
  if [ "${wl_used[$index]}" -eq 0 ] && ! has_wildcard "$entry_class" && ! has_wildcard "$entry_method"; then
    echo "[ainer-endpoint-authorization] 违规(过期登记): ${whitelist_file#"$boot_root"/}:${wl_lines[$index]} ${wl_specs[$index]} 在扫描范围内没有匹配的 handler，请删除该登记项"
    violations=$((violations + 1))
  fi
  index=$((index + 1))
done

# ---------------------------------------------------------------- 结果
if [ "$violations" -gt 0 ]; then
  echo "[ainer-endpoint-authorization] 发现 $violations 处端点授权声明违规（规范见 docs/conventions.md §9）" >&2
  exit 1
fi

echo "[ainer-endpoint-authorization] 通过：Java 文件 $scanned_files 个、handler 方法 $handler_total 个（@AinerAuthorize $declared_ainer_authorize 个、@EndpointAccess $declared_endpoint_access 个、类级 @EndpointAccess $declared_class_level 个、白名单登记 $whitelisted 个），违规 0 处"
