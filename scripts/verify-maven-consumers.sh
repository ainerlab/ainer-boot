#!/usr/bin/env bash
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
wrapper="$boot_root/mvnw"
maven3_command="${MAVEN3_COMMAND:-mvn}"

fail() {
  echo "[ainer-maven-consumer] ERROR: $*" >&2
  exit 1
}

configured_version="$(
  sed -n 's:.*<revision>\([^<]*\)</revision>.*:\1:p' "$boot_root/pom.xml" \
    | sed -n '1p'
)"
ainer_version="${AINER_VERSION:-$configured_version}"
[[ -n "$ainer_version" ]] \
  || fail "cannot determine the Ainer version; set AINER_VERSION explicitly"

[[ -x "$wrapper" ]] || fail "Maven Wrapper is missing or not executable: $wrapper"
command -v "$maven3_command" >/dev/null 2>&1 \
  || fail "Maven 3 consumer command is missing: $maven3_command"
command -v jar >/dev/null 2>&1 || fail "JDK jar command is missing"

maven3_banner="$("$maven3_command" --version | sed -n '1p')"
if [[ ! "$maven3_banner" =~ ^Apache\ Maven\ 3\.([0-9]+) ]]; then
  fail "consumer compatibility requires Maven 3.9+, found: $maven3_banner"
fi
(( BASH_REMATCH[1] >= 9 )) \
  || fail "consumer compatibility requires Maven 3.9+, found: $maven3_banner"

temporary_parent="${TMPDIR:-/tmp}"
temporary_dir="$(mktemp -d "$temporary_parent/ainer-maven-consumer.XXXXXX")"
local_repository="$temporary_dir/repository"
consumer_dir="$temporary_dir/consumer"

cleanup() {
  case "$temporary_dir" in
    "$temporary_parent"/ainer-maven-consumer.*)
      rm -rf -- "$temporary_dir"
      ;;
    *)
      echo "[ainer-maven-consumer] refusing unsafe cleanup target: $temporary_dir" >&2
      ;;
  esac
}
trap cleanup EXIT

mkdir -p "$local_repository" "$consumer_dir/src/main/java/dev/ainer/consumer"

cd "$boot_root"
"$wrapper" --batch-mode --no-transfer-progress \
  -Dmaven.repo.local="$local_repository" \
  -Drevision="$ainer_version" \
  -Dgpg.skip=true \
  -Prelease \
  -DskipTests \
  clean install

installed_root="$local_repository/dev/ainer"
[[ -d "$installed_root" ]] || fail "Ainer artifacts were not installed"

# P1 发布门禁：library 制品必须附带 sources/javadoc 与 spring-configuration-metadata
# （signature 由 release.yml 真实密钥门禁覆盖，consumer 门禁只验证伴随制品存在）。
publish_artifacts=(ainer-spring ainer-starter-web ainer-starter-persistence ainer-starter-security \
  ainer-test-support ainer-module-identity ainer-module-workspace ainer-module-ai-runtime ainer-module-authorization)
for artifact in "${publish_artifacts[@]}"; do
  base="$installed_root/$artifact/$ainer_version/$artifact-$ainer_version"
  [[ -f "$base-sources.jar" ]] || fail "$artifact sources JAR missing"
  [[ -f "$base-javadoc.jar" ]] || fail "$artifact javadoc JAR missing"
done

installed_poms=()
while IFS= read -r -d '' installed_pom; do
  installed_poms+=("$installed_pom")
  if grep -Fq '${revision}' "$installed_pom" \
    && ! grep -Fq "<revision>$ainer_version</revision>" "$installed_pom"; then
    fail "consumer POM contains revision without the installed version property: $installed_pom"
  fi
done < <(find "$installed_root" -type f -name '*.pom' ! -name '*-build.pom' -print0)

[[ "${#installed_poms[@]}" -eq 19 ]] \
  || fail "expected 19 installed Ainer consumer POMs, found ${#installed_poms[@]}"

# Ainer 的公开配置类（含 @ConfigurationProperties 的 library/module 制品）必须随 JAR
# 生成 spring-configuration-metadata.json（ADR-0029 P0-3）。应用可执行 JAR（ainer-server、
# ainer-authorization-server）经 spring-boot 重打包后元数据位于 BOOT-INF/classes，不在此校验。
config_artifacts=(ainer-spring ainer-starter-security ainer-module-ai-runtime)
for artifact in "${config_artifacts[@]}"; do
  jar_path="$installed_root/$artifact/$ainer_version/$artifact-$ainer_version.jar"
  [[ -f "$jar_path" ]] || fail "$artifact JAR was not installed: $jar_path"
  jar tf "$jar_path" | grep -Fxq 'META-INF/spring-configuration-metadata.json' \
    || fail "$artifact JAR is missing configuration metadata"
done

"$wrapper" --batch-mode --no-transfer-progress \
  -Dmaven.repo.local="$local_repository" \
  -Drevision="$ainer_version" \
  -DskipTests \
  clean verify \
  org.apache.maven.plugins:maven-artifact-plugin:3.6.1:compare

cat >"$consumer_dir/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>dev.ainer.consumer</groupId>
    <artifactId>ainer-golden-consumer</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>dev.ainer</groupId>
                <artifactId>ainer-dependencies</artifactId>
                <version>$ainer_version</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>dev.ainer</groupId>
            <artifactId>ainer-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.ainer</groupId>
            <artifactId>ainer-starter-persistence</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.ainer</groupId>
            <artifactId>ainer-module-authorization</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.0</version>
                <configuration>
                    <release>25</release>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
EOF

cat >"$consumer_dir/src/main/java/dev/ainer/consumer/ConsumerSmoke.java" <<'EOF'
package dev.ainer.consumer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.core.web.ApiResponse;

final class ConsumerSmoke {

    private ConsumerSmoke() {
    }

    static ApiResponse<String> response() {
        return ApiResponse.success("consumer", "maven-smoke");
    }

    static String permissionCode() {
        return new PermissionCode("consumer.smoke").value();
    }

    static boolean isPersistenceMapper(BaseMapper<?> mapper) {
        return mapper != null;
    }
}
EOF

"$maven3_command" --batch-mode --no-transfer-progress \
  --file "$consumer_dir/pom.xml" \
  -Dmaven.repo.local="$local_repository" \
  clean package

"$wrapper" --batch-mode --no-transfer-progress \
  --file "$consumer_dir/pom.xml" \
  -Dmaven.repo.local="$local_repository" \
  clean package

echo "[ainer-maven-consumer] Maven 3.9+ and Maven 4 consumer checks passed"
