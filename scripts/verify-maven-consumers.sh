#!/usr/bin/env bash
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
wrapper="$boot_root/mvnw"
maven3_command="${MAVEN3_COMMAND:-mvn}"
artifact_source="${AINER_ARTIFACT_SOURCE:-local}"
maven_settings="${AINER_MAVEN_SETTINGS:-}"

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

case "$artifact_source" in
  local|remote) ;;
  *) fail "AINER_ARTIFACT_SOURCE must be local or remote (got: $artifact_source)" ;;
esac
if [[ "$artifact_source" == "remote" && "$ainer_version" == *-SNAPSHOT ]]; then
  fail "remote consumer verification requires a non-SNAPSHOT AINER_VERSION"
fi

maven_settings_args=()
if [[ -n "$maven_settings" ]]; then
  [[ -f "$maven_settings" ]] || fail "Maven settings file is missing: $maven_settings"
  maven_settings_args=(--settings "$maven_settings")
elif [[ "$artifact_source" == "remote" ]]; then
  fail "remote consumer verification requires AINER_MAVEN_SETTINGS"
fi

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
maven4_repository="$local_repository"
consumer_dir="$temporary_dir/consumer"

if [[ "$artifact_source" == "remote" ]]; then
  maven4_repository="$temporary_dir/repository-maven4"
fi

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

mkdir -p "$local_repository" "$maven4_repository" \
  "$consumer_dir/src/main/java/dev/ainer/consumer" \
  "$consumer_dir/src/test/java/dev/ainer/consumer"

if [[ "$artifact_source" == "local" ]]; then
  cd "$boot_root"
  "$wrapper" --batch-mode --no-transfer-progress \
    "${maven_settings_args[@]}" \
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

  [[ "${#installed_poms[@]}" -eq 25 ]] \
    || fail "expected 25 installed Ainer consumer POMs, found ${#installed_poms[@]}"

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
    "${maven_settings_args[@]}" \
    -Dmaven.repo.local="$local_repository" \
    -Drevision="$ainer_version" \
    -Dgpg.skip=true \
    -DskipTests \
    clean verify \
    org.apache.maven.plugins:maven-artifact-plugin:3.6.1:compare
else
  [[ ! -e "$local_repository/dev/ainer" ]] \
    || fail "remote Maven 3 repository must start without Ainer artifacts"
  [[ ! -e "$maven4_repository/dev/ainer" ]] \
    || fail "remote Maven 4 repository must start without Ainer artifacts"
fi

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
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
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
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.3</version>
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

cat >"$consumer_dir/src/test/java/dev/ainer/consumer/AuthorizationGoldenConsumerTest.java" <<'EOF'
package dev.ainer.consumer;

import dev.ainer.authorization.AuthorizationService;
import dev.ainer.authorization.DefaultQueryAuthorizationPlanner;
import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.AuthorizedQueryPlan;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.QueryAuthorizationRequest;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.domain.Role;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectBinding;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.policy.BindingResolver;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Artifact-only external consumer: product types and policies live here, outside the Ainer reactor.
 */
class AuthorizationGoldenConsumerTest {

    private static final PermissionCode OFFER_READ = new PermissionCode("consumer.offer.read");
    private static final ResourceType OFFER = new ResourceType("consumer.offer");
    private static final String READ_SCOPE = "consumer.offers.read";
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
    private static final UUID WORKSPACE = UUID.fromString("019c1000-0000-7000-8000-000000000001");
    private static final UUID OTHER_WORKSPACE = UUID.fromString("019c1000-0000-7000-8000-000000000002");
    private static final UUID OFFER_ID = UUID.fromString("019c1000-0000-7000-8000-000000000003");
    private static final SubjectRef OPERATOR =
            new SubjectRef("consumer-authority", "operator-1", SubjectType.USER);
    private static final SubjectRef OUTSIDER =
            new SubjectRef("consumer-authority", "outsider-1", SubjectType.USER);

    record OfferQueryIntent(Set<String> statuses) {
    }

    record OfferReadConstraint(Set<UUID> allowedWorkspaceIds) {
        OfferReadConstraint {
            allowedWorkspaceIds = Set.copyOf(allowedWorkspaceIds);
        }
    }

    @Test
    void consumesAuthorizationServiceAndQueryPlannerFromInstalledArtifacts() {
        PermissionRegistry registry = new PermissionRegistry().register(() -> Set.of(
                new Permission(
                        OFFER_READ, "read", OFFER, RiskTier.LOW,
                        AuditLevel.ON_DECISION, false, false)));
        DomainAuthorizationPolicy policy = bindingRequiredPolicy();
        BindingResolver resolver = productBindings();

        AuthorizationService service = new AuthorizationService(
                registry,
                (scope, permission) -> READ_SCOPE.equals(scope) && OFFER_READ.equals(permission),
                (permission, resource) -> Optional.empty(),
                policy,
                resolver,
                "external-consumer-v1");

        AuthorizationDecision allowed = service.authorize(singleResourceRequest(OPERATOR, WORKSPACE));
        AuthorizationDecision crossWorkspace = service.authorize(
                singleResourceRequest(OPERATOR, OTHER_WORKSPACE));
        assertEquals(AuthorizationOutcome.ALLOW, allowed.outcome());
        assertEquals(AuthorizationOutcome.DENY, crossWorkspace.outcome());

        DefaultQueryAuthorizationPlanner<OfferQueryIntent, OfferReadConstraint> planner =
                new DefaultQueryAuthorizationPlanner<>(
                        registry,
                        (scope, permission) -> READ_SCOPE.equals(scope) && OFFER_READ.equals(permission),
                        resolver,
                        policy,
                        (current, binding, permission, resourceType) -> {
                            Set<UUID> workspaces = current == null
                                    ? new HashSet<>()
                                    : new HashSet<>(current.allowedWorkspaceIds());
                            if (binding.scope() instanceof Scope.Workspace workspaceScope) {
                                workspaces.add(workspaceScope.workspaceId());
                            }
                            return new OfferReadConstraint(workspaces);
                        },
                        "external-consumer-v1");

        AuthorizedQueryPlan<OfferReadConstraint> allowedPlan = planner.plan(queryRequest(OPERATOR));
        AuthorizedQueryPlan.Allowed<?> allowedQuery =
                assertInstanceOf(AuthorizedQueryPlan.Allowed.class, allowedPlan);
        OfferReadConstraint constraint = assertInstanceOf(
                OfferReadConstraint.class, allowedQuery.constraint());
        assertEquals(Set.of(WORKSPACE), constraint.allowedWorkspaceIds());
        assertInstanceOf(AuthorizedQueryPlan.Denied.class, planner.plan(queryRequest(OUTSIDER)));
    }

    private static AuthorizationRequest singleResourceRequest(SubjectRef subject, UUID workspaceId) {
        return new AuthorizationRequest(
                requester(subject),
                AccessMode.AUTHENTICATED,
                OFFER_READ,
                new ResourceRef(workspaceId, OFFER, OFFER_ID),
                context());
    }

    private static QueryAuthorizationRequest<OfferQueryIntent> queryRequest(SubjectRef subject) {
        return new QueryAuthorizationRequest<>(
                requester(subject),
                AccessMode.AUTHENTICATED,
                OFFER_READ,
                OFFER,
                "consumer-offer-search",
                new OfferQueryIntent(Set.of("PUBLISHED")),
                context());
    }

    private static Requester.Authenticated requester(SubjectRef subject) {
        return new Requester.Authenticated(
                subject, Set.of(READ_SCOPE), Set.of("consumer-api"), "external-consumer");
    }

    private static AuthorizationContext context() {
        return new AuthorizationContext(
                NOW, AuthorizationContext.Assurance.RECENT_STRONG,
                "external-consumer", "consumer-request", "consumer-trace");
    }

    private static BindingResolver productBindings() {
        SubjectBinding binding = new SubjectBinding(
                OPERATOR,
                new Role("offer-reader", "Offer Reader", Set.of(OFFER_READ)),
                new Scope.Workspace(WORKSPACE),
                BindingStatus.ACTIVE,
                NOW.minusSeconds(60),
                null,
                1L);
        return subject -> OPERATOR.equals(subject) ? Set.of(binding) : Set.of();
    }

    private static DomainAuthorizationPolicy bindingRequiredPolicy() {
        return new DomainAuthorizationPolicy() {
            @Override
            public GrantPath pathFor(PermissionCode permission) {
                return OFFER_READ.equals(permission) ? GrantPath.BINDING_REQUIRED : null;
            }

            @Override
            public boolean relationGrants(
                    Requester.Authenticated subject,
                    PermissionCode permission,
                    ResourceRef resource,
                    AuthorizationContext context) {
                return false;
            }

            @Override
            public boolean resourceStateSatisfies(
                    Requester.Authenticated subject,
                    PermissionCode permission,
                    ResourceRef resource,
                    AuthorizationContext context) {
                return true;
            }
        };
    }
}
EOF

verify_authorization_consumer_test() {
  local maven_label="$1"
  local report="$consumer_dir/target/surefire-reports/TEST-dev.ainer.consumer.AuthorizationGoldenConsumerTest.xml"
  [[ -f "$report" ]] || fail "$maven_label authorization Golden Consumer report is missing"
  grep -Eq '<testsuite[^>]*tests="1"' "$report" \
    || fail "$maven_label authorization Golden Consumer did not execute exactly one test"
  grep -Eq '<testsuite[^>]*failures="0"' "$report" \
    || fail "$maven_label authorization Golden Consumer reported a failure"
  grep -Eq '<testsuite[^>]*errors="0"' "$report" \
    || fail "$maven_label authorization Golden Consumer reported an error"
  grep -Eq '<testsuite[^>]*skipped="0"' "$report" \
    || fail "$maven_label authorization Golden Consumer was skipped"
}

"$maven3_command" --batch-mode --no-transfer-progress \
  "${maven_settings_args[@]}" \
  --file "$consumer_dir/pom.xml" \
  -Dmaven.repo.local="$local_repository" \
  clean verify
verify_authorization_consumer_test "Maven 3.9+"

"$wrapper" --batch-mode --no-transfer-progress \
  "${maven_settings_args[@]}" \
  --file "$consumer_dir/pom.xml" \
  -Dmaven.repo.local="$maven4_repository" \
  clean verify
verify_authorization_consumer_test "Maven 4"

if [[ "$artifact_source" == "remote" ]]; then
  [[ -f "$local_repository/dev/ainer/ainer-dependencies/$ainer_version/ainer-dependencies-$ainer_version.pom" ]] \
    || fail "Maven 3 did not resolve the remote Ainer BOM"
  [[ -f "$maven4_repository/dev/ainer/ainer-dependencies/$ainer_version/ainer-dependencies-$ainer_version.pom" ]] \
    || fail "Maven 4 did not resolve the remote Ainer BOM"
fi

echo "[ainer-maven-consumer] Maven 3.9+ and Maven 4 $artifact_source consumer checks passed"
