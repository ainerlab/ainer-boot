package dev.ainer.authorization.consumer;

import dev.ainer.authorization.DefaultQueryAuthorizationPlanner;
import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizedQueryPlan;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.PublicProjection;
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
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.SqlArrayValue;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Product-owned Golden Consumer query adapter proof for ADR-0030 §7.4/§7.5.
 *
 * <p>The product types, table and JDBC adapter deliberately live in test scope: Ainer emits only a
 * typed constraint and never learns the product schema or SQL. The adapter applies that constraint
 * in one prepared PostgreSQL statement, projects only public columns, and never loads unauthorized
 * rows for JVM-side filtering. All fixture values are synthetic.
 */
@Testcontainers(disabledWithoutDocker = true)
class GoldenConsumerPostgresQueryIntegrationTest {

    private static final PermissionCode LISTING_READ =
            new PermissionCode("consumer.listing.read");
    private static final ResourceType LISTING = new ResourceType("consumer.listing");
    private static final String LISTING_READ_SCOPE = "consumer.listings.read";
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    private static final UUID WORKSPACE_A =
            UUID.fromString("019c1000-0000-7000-8000-000000000001");
    private static final UUID WORKSPACE_B =
            UUID.fromString("019c1000-0000-7000-8000-000000000002");
    private static final UUID LISTING_A_PUBLISHED =
            UUID.fromString("019c1000-0000-7000-8000-0000000000a1");
    private static final UUID LISTING_A_DRAFT =
            UUID.fromString("019c1000-0000-7000-8000-0000000000a2");
    private static final UUID LISTING_B_PUBLISHED =
            UUID.fromString("019c1000-0000-7000-8000-0000000000b1");

    private static final SubjectRef OPERATOR =
            new SubjectRef("consumer-authority", "operator-1", SubjectType.USER);
    private static final SubjectRef OUTSIDER =
            new SubjectRef("consumer-authority", "outsider-1", SubjectType.USER);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("authorization_consumer_query_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    private static JdbcTemplate jdbcTemplate;

    private ProductListingQueryAdapter queryAdapter;
    private ProductListingSearchService searchService;

    @BeforeAll
    static void createProductFixtureSchema() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbcTemplate.execute("""
                CREATE TABLE consumer_listing (
                    id UUID NOT NULL DEFAULT uuidv7(),
                    workspace_id UUID NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    title VARCHAR(120) NOT NULL,
                    internal_cost NUMERIC(19, 4) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL,
                    CONSTRAINT pk_consumer_listing PRIMARY KEY (id),
                    CONSTRAINT ck_consumer_listing_id_version
                        CHECK (uuid_extract_version(id) = 7),
                    CONSTRAINT ck_consumer_listing_status
                        CHECK (status IN ('DRAFT', 'PUBLISHED')),
                    CONSTRAINT ck_consumer_listing_internal_cost
                        CHECK (internal_cost >= 0)
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_consumer_listing_authorized_search
                    ON consumer_listing (workspace_id, status, created_at, id)
                    INCLUDE (title)
                """);
    }

    @BeforeEach
    void resetProductFixture() {
        jdbcTemplate.execute("TRUNCATE TABLE consumer_listing");
        insertListing(
                LISTING_A_PUBLISHED, WORKSPACE_A, "PUBLISHED", "Workspace A published", "10.0000", 1);
        insertListing(LISTING_A_DRAFT, WORKSPACE_A, "DRAFT", "Workspace A draft", "20.0000", 2);
        insertListing(
                LISTING_B_PUBLISHED, WORKSPACE_B, "PUBLISHED", "Workspace B private", "30.0000", 3);
        queryAdapter = new ProductListingQueryAdapter(jdbcTemplate);
        searchService = new ProductListingSearchService(queryAdapter);
    }

    @Test
    void appliesTypedConstraintInOneParameterizedQueryAndExcludesUnauthorizedRows() {
        ListingQueryIntent intent = new ListingQueryIntent(Set.of("PUBLISHED"));
        AuthorizedQueryPlan<ListingReadConstraint> plan =
                planner(Set.of(workspaceBinding())).plan(queryRequest(OPERATOR, intent));

        List<ListingProjection> rows = searchService.search(plan, intent);

        assertThat(rows).extracting(ListingProjection::id).containsExactly(LISTING_A_PUBLISHED);
        assertThat(rows).extracting(ListingProjection::workspaceId).containsOnly(WORKSPACE_A);
        assertThat(queryAdapter.executedProductQueries()).isEqualTo(1);
        assertThat(queryAdapter.lastSql())
                .isEqualTo(ProductListingQueryAdapter.RESTRICTED_SEARCH_SQL)
                .doesNotContain(WORKSPACE_A.toString())
                .doesNotContain("PUBLISHED");
    }

    @Test
    void appliesResourceConstraintWithoutWideningToTheOwningWorkspace() {
        ListingQueryIntent intent = new ListingQueryIntent(Set.of("PUBLISHED", "DRAFT"));
        AuthorizedQueryPlan<ListingReadConstraint> plan =
                planner(Set.of(resourceBinding())).plan(queryRequest(OPERATOR, intent));

        List<ListingProjection> rows = searchService.search(plan, intent);

        assertThat(rows).extracting(ListingProjection::id).containsExactly(LISTING_A_PUBLISHED);
        assertThat(queryAdapter.executedProductQueries()).isEqualTo(1);
    }

    @Test
    void bindsHostileLookingStatusWithoutWideningTheAuthorizationPredicate() {
        String hostileStatus = "PUBLISHED') OR TRUE --";
        ListingQueryIntent intent = new ListingQueryIntent(Set.of(hostileStatus));
        AuthorizedQueryPlan<ListingReadConstraint> plan =
                planner(Set.of(workspaceBinding())).plan(queryRequest(OPERATOR, intent));

        List<ListingProjection> rows = searchService.search(plan, intent);

        assertThat(rows).isEmpty();
        assertThat(queryAdapter.executedProductQueries()).isEqualTo(1);
        assertThat(queryAdapter.lastSql()).doesNotContain(hostileStatus);
        assertThat(productRowCount()).isEqualTo(3);
    }

    @Test
    void deniedPlanExecutesNoProductQuery() {
        ListingQueryIntent intent = new ListingQueryIntent(Set.of("PUBLISHED"));
        AuthorizedQueryPlan<ListingReadConstraint> plan =
                planner(Set.of(workspaceBinding())).plan(queryRequest(OUTSIDER, intent));

        assertThat(plan).isInstanceOf(AuthorizedQueryPlan.Denied.class);
        assertThatThrownBy(() -> searchService.search(plan, intent))
                .isInstanceOf(QueryAuthorizationDeniedException.class);
        assertThat(queryAdapter.executedProductQueries()).isZero();
    }

    @Test
    void unsupportedGlobalConstraintFailsClosedWithoutExecutingAQuery() {
        ListingQueryIntent intent = new ListingQueryIntent(Set.of("PUBLISHED"));
        AuthorizedQueryPlan<ListingReadConstraint> plan = new AuthorizedQueryPlan.Allowed<>(
                new ListingReadConstraint(true, Set.of(), Set.of()),
                List.of(),
                "postgres-golden-consumer-v1");

        assertThatThrownBy(() -> searchService.search(plan, intent))
                .isInstanceOf(QueryAuthorizationDeniedException.class)
                .hasMessage("unsupported-global-constraint");
        assertThat(queryAdapter.executedProductQueries()).isZero();
    }

    @Test
    void unconsumedObligationFailsClosedWithoutExecutingAQuery() {
        ListingQueryIntent intent = new ListingQueryIntent(Set.of("PUBLISHED"));
        AuthorizedQueryPlan<ListingReadConstraint> plan = new AuthorizedQueryPlan.Allowed<>(
                new ListingReadConstraint(false, Set.of(WORKSPACE_A), Set.of()),
                List.of(new PublicProjection("consumer-listing-public-v1")),
                "postgres-golden-consumer-v1");

        assertThatThrownBy(() -> searchService.search(plan, intent))
                .isInstanceOf(QueryAuthorizationDeniedException.class)
                .hasMessage("unsupported-obligation");
        assertThat(queryAdapter.executedProductQueries()).isZero();
    }

    @Test
    void representativeFixtureUsesTheAuthorizationIndexAndStillRunsOneProductQuery() {
        insertRepresentativeBackgroundRows(20_000);
        analyzeProductTable();
        ListingQueryIntent intent = new ListingQueryIntent(Set.of("PUBLISHED"));
        AuthorizedQueryPlan<ListingReadConstraint> plan =
                planner(Set.of(workspaceBinding())).plan(queryRequest(OPERATOR, intent));
        ListingReadConstraint constraint = allowedConstraint(plan);

        String explain = String.join("\n", queryAdapter.explain(intent, constraint));
        List<ListingProjection> rows = searchService.search(plan, intent);

        assertThat(explain)
                .contains("idx_consumer_listing_authorized_search")
                .contains("actual time=")
                .contains("Buffers:");
        assertThat(rows).extracting(ListingProjection::id).containsExactly(LISTING_A_PUBLISHED);
        assertThat(queryAdapter.executedExplainQueries()).isEqualTo(1);
        assertThat(queryAdapter.executedProductQueries()).isEqualTo(1);
    }

    private static DefaultQueryAuthorizationPlanner<ListingQueryIntent, ListingReadConstraint> planner(
            Set<SubjectBinding> bindings) {
        PermissionRegistry registry = new PermissionRegistry().register(() -> Set.of(
                new Permission(
                        LISTING_READ,
                        "read",
                        LISTING,
                        RiskTier.LOW,
                        AuditLevel.NONE,
                        false,
                        false)));
        DomainAuthorizationPolicy policy = new DomainAuthorizationPolicy() {
            @Override
            public GrantPath pathFor(PermissionCode permission) {
                return LISTING_READ.equals(permission) ? GrantPath.BINDING_REQUIRED : null;
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

        return new DefaultQueryAuthorizationPlanner<>(
                registry,
                (scope, permission) ->
                        LISTING_READ_SCOPE.equals(scope) && LISTING_READ.equals(permission),
                subject -> OPERATOR.equals(subject) ? bindings : Set.of(),
                policy,
                GoldenConsumerPostgresQueryIntegrationTest::accumulateConstraint,
                "postgres-golden-consumer-v1");
    }

    private static ListingReadConstraint accumulateConstraint(
            ListingReadConstraint current,
            SubjectBinding binding,
            PermissionCode permission,
            ResourceType resourceType) {
        ListingReadConstraint constraint = current == null ? ListingReadConstraint.empty() : current;
        if (constraint.global()) {
            return constraint;
        }
        return switch (binding.scope()) {
            case Scope.Global ignored -> new ListingReadConstraint(true, Set.of(), Set.of());
            case Scope.Workspace workspace -> new ListingReadConstraint(
                    false,
                    plus(constraint.allowedWorkspaceIds(), workspace.workspaceId()),
                    constraint.allowedResourceIds());
            case Scope.Resource resource -> new ListingReadConstraint(
                    false,
                    constraint.allowedWorkspaceIds(),
                    plus(constraint.allowedResourceIds(), resource.resourceId()));
        };
    }

    private static Set<UUID> plus(Set<UUID> existing, UUID value) {
        Set<UUID> values = new HashSet<>(existing);
        values.add(value);
        return Set.copyOf(values);
    }

    private static SubjectBinding workspaceBinding() {
        return new SubjectBinding(
                OPERATOR,
                new Role("listing-reader", "Listing Reader", Set.of(LISTING_READ)),
                new Scope.Workspace(WORKSPACE_A),
                BindingStatus.ACTIVE,
                NOW.minusSeconds(60),
                null,
                1L);
    }

    private static SubjectBinding resourceBinding() {
        return new SubjectBinding(
                OPERATOR,
                new Role("listing-reader", "Listing Reader", Set.of(LISTING_READ)),
                new Scope.Resource(WORKSPACE_A, LISTING, LISTING_A_PUBLISHED),
                BindingStatus.ACTIVE,
                NOW.minusSeconds(60),
                null,
                1L);
    }

    private static QueryAuthorizationRequest<ListingQueryIntent> queryRequest(
            SubjectRef subject, ListingQueryIntent intent) {
        return new QueryAuthorizationRequest<>(
                new Requester.Authenticated(
                        subject,
                        Set.of(LISTING_READ_SCOPE),
                        Set.of("consumer-api"),
                        "consumer-client"),
                AccessMode.AUTHENTICATED,
                LISTING_READ,
                LISTING,
                "consumer-listing-search",
                intent,
                new AuthorizationContext(
                        NOW,
                        AuthorizationContext.Assurance.RECENT_STRONG,
                        "consumer-client",
                        "consumer-request",
                        "consumer-trace"));
    }

    private static ListingReadConstraint allowedConstraint(
            AuthorizedQueryPlan<ListingReadConstraint> plan) {
        AuthorizedQueryPlan.Allowed<?> allowed =
                (AuthorizedQueryPlan.Allowed<?>) plan;
        return ListingReadConstraint.class.cast(allowed.constraint());
    }

    private static void insertListing(
            UUID id,
            UUID workspaceId,
            String status,
            String title,
            String internalCost,
            int createdOffsetSeconds) {
        jdbcTemplate.update(
                """
                INSERT INTO consumer_listing
                    (id, workspace_id, status, title, internal_cost, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                workspaceId,
                status,
                title,
                new BigDecimal(internalCost),
                Timestamp.from(NOW.plusSeconds(createdOffsetSeconds)));
    }

    private static int productRowCount() {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject("SELECT count(*) FROM consumer_listing", Integer.class));
    }

    private static void insertRepresentativeBackgroundRows(int count) {
        jdbcTemplate.update(
                """
                INSERT INTO consumer_listing
                    (id, workspace_id, status, title, internal_cost, created_at)
                SELECT uuidv7(),
                       uuidv7(),
                       CASE WHEN sequence_number % 2 = 0 THEN 'PUBLISHED' ELSE 'DRAFT' END,
                       'fixture-' || sequence_number,
                       1.0000,
                       TIMESTAMPTZ '2026-08-11 00:00:00+00'
                           + sequence_number * INTERVAL '1 millisecond'
                FROM generate_series(1, ?) AS sequence_number
                """,
                count);
    }

    private static void analyzeProductTable() {
        jdbcTemplate.execute("ANALYZE consumer_listing");
    }

    record ListingQueryIntent(Set<String> statuses) {

        ListingQueryIntent {
            statuses = Set.copyOf(Objects.requireNonNull(statuses, "statuses"));
            if (statuses.isEmpty() || statuses.size() > 8) {
                throw new IllegalArgumentException("statuses must contain between 1 and 8 values");
            }
            if (statuses.stream().anyMatch(status -> status.isBlank() || status.length() > 64)) {
                throw new IllegalArgumentException("status values must contain between 1 and 64 characters");
            }
        }
    }

    record ListingReadConstraint(
            boolean global,
            Set<UUID> allowedWorkspaceIds,
            Set<UUID> allowedResourceIds) {

        private static final int MAX_AUTHORIZATION_IDS = 100;

        ListingReadConstraint {
            allowedWorkspaceIds =
                    Set.copyOf(Objects.requireNonNull(allowedWorkspaceIds, "allowedWorkspaceIds"));
            allowedResourceIds =
                    Set.copyOf(Objects.requireNonNull(allowedResourceIds, "allowedResourceIds"));
            if (allowedWorkspaceIds.size() + allowedResourceIds.size() > MAX_AUTHORIZATION_IDS) {
                throw new IllegalArgumentException("authorization ID constraint exceeds 100 values");
            }
            if (global && (!allowedWorkspaceIds.isEmpty() || !allowedResourceIds.isEmpty())) {
                throw new IllegalArgumentException("global constraint must not carry bounded IDs");
            }
        }

        static ListingReadConstraint empty() {
            return new ListingReadConstraint(false, Set.of(), Set.of());
        }
    }

    record ListingProjection(UUID id, UUID workspaceId, String status, String title) {
    }

    static final class ProductListingSearchService {

        private final ProductListingQueryAdapter queryAdapter;

        ProductListingSearchService(ProductListingQueryAdapter queryAdapter) {
            this.queryAdapter = queryAdapter;
        }

        List<ListingProjection> search(
                AuthorizedQueryPlan<ListingReadConstraint> plan, ListingQueryIntent intent) {
            if (plan instanceof AuthorizedQueryPlan.Denied<?> denied) {
                throw new QueryAuthorizationDeniedException(denied.reasonCode());
            }
            AuthorizedQueryPlan.Allowed<?> allowed = (AuthorizedQueryPlan.Allowed<?>) plan;
            if (!allowed.obligations().isEmpty()) {
                throw new QueryAuthorizationDeniedException("unsupported-obligation");
            }
            return queryAdapter.search(intent, ListingReadConstraint.class.cast(allowed.constraint()));
        }
    }

    static final class ProductListingQueryAdapter {

        static final String RESTRICTED_SEARCH_SQL = """
                SELECT id, workspace_id, status, title
                FROM consumer_listing
                WHERE status = ANY (?)
                  AND (workspace_id = ANY (?) OR id = ANY (?))
                ORDER BY created_at, id
                LIMIT 100
                """;

        private final AtomicInteger productQueries = new AtomicInteger();
        private final AtomicInteger explainQueries = new AtomicInteger();
        private final JdbcTemplate jdbcTemplate;
        private String lastSql = "";

        ProductListingQueryAdapter(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        List<ListingProjection> search(
                ListingQueryIntent intent, ListingReadConstraint constraint) {
            if (constraint.global()) {
                throw new QueryAuthorizationDeniedException("unsupported-global-constraint");
            }
            if (constraint.allowedWorkspaceIds().isEmpty()
                    && constraint.allowedResourceIds().isEmpty()) {
                throw new QueryAuthorizationDeniedException("empty-authorization-constraint");
            }
            return executeRestricted(RESTRICTED_SEARCH_SQL, intent, constraint);
        }

        List<String> explain(ListingQueryIntent intent, ListingReadConstraint constraint) {
            if (constraint.global()) {
                throw new IllegalArgumentException("representative plan requires a restricted constraint");
            }
            return executeRestrictedExplain(
                    "EXPLAIN (ANALYZE, BUFFERS) " + RESTRICTED_SEARCH_SQL,
                    intent,
                    constraint);
        }

        int executedProductQueries() {
            return productQueries.get();
        }

        int executedExplainQueries() {
            return explainQueries.get();
        }

        String lastSql() {
            return lastSql;
        }

        private List<ListingProjection> executeRestricted(
                String sql,
                ListingQueryIntent intent,
                ListingReadConstraint constraint) {
            lastSql = sql;
            productQueries.incrementAndGet();
            return jdbcTemplate.query(
                    sql,
                    (rows, rowNumber) -> new ListingProjection(
                            rows.getObject("id", UUID.class),
                            rows.getObject("workspace_id", UUID.class),
                            rows.getString("status"),
                            rows.getString("title")),
                    restrictedArguments(intent, constraint));
        }

        private List<String> executeRestrictedExplain(
                String sql, ListingQueryIntent intent, ListingReadConstraint constraint) {
            lastSql = sql;
            explainQueries.incrementAndGet();
            return jdbcTemplate.query(
                    sql,
                    (rows, rowNumber) -> rows.getString(1),
                    restrictedArguments(intent, constraint));
        }

        private static Object[] restrictedArguments(
                ListingQueryIntent intent, ListingReadConstraint constraint) {
            return new Object[] {
                new SqlArrayValue("varchar", intent.statuses().toArray()),
                new SqlArrayValue("uuid", constraint.allowedWorkspaceIds().toArray()),
                new SqlArrayValue("uuid", constraint.allowedResourceIds().toArray())
            };
        }
    }

    static final class QueryAuthorizationDeniedException extends RuntimeException {

        QueryAuthorizationDeniedException(String reasonCode) {
            super(reasonCode);
        }
    }
}
