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
  -DskipTests \
  clean install

installed_root="$local_repository/dev/ainer"
[[ -d "$installed_root" ]] || fail "Ainer artifacts were not installed"

installed_poms=()
while IFS= read -r -d '' installed_pom; do
  installed_poms+=("$installed_pom")
  if grep -Fq '${revision}' "$installed_pom" \
    && ! grep -Fq "<revision>$ainer_version</revision>" "$installed_pom"; then
    fail "consumer POM contains revision without the installed version property: $installed_pom"
  fi
done < <(find "$installed_root" -type f -name '*.pom' ! -name '*-build.pom' -print0)

[[ "${#installed_poms[@]}" -eq 14 ]] \
  || fail "expected 14 installed Ainer consumer POMs, found ${#installed_poms[@]}"

spring_jar="$installed_root/ainer-spring/$ainer_version/ainer-spring-$ainer_version.jar"
[[ -f "$spring_jar" ]] || fail "Ainer Spring JAR was not installed: $spring_jar"
jar tf "$spring_jar" | grep -Fxq 'META-INF/spring-configuration-metadata.json' \
  || fail "Ainer Spring JAR is missing configuration metadata"

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
import dev.ainer.core.web.ApiResponse;

final class ConsumerSmoke {

    private ConsumerSmoke() {
    }

    static ApiResponse<String> response() {
        return ApiResponse.success("consumer", "maven-smoke");
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
