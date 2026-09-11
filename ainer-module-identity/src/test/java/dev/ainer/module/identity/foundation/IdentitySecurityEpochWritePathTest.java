package dev.ainer.module.identity.foundation;

import dev.ainer.core.error.BusinessException;
import dev.ainer.security.principal.IdentityAuthorityRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Identity 生命周期写路径与 {@code security_epoch} 递增的真实 PostgreSQL 证明。
 *
 * <p>背景：{@code HumanAccount.securityEpoch} / {@code ServicePrincipal.securityEpoch} 曾被写入
 * Token 的 {@code sec_epoch} claim，但仓库里只有 insert/select，没有任何 UPDATE，于是
 * {@code RevocationAwareOAuth2AuthorizationService} 的 epoch 比对恒真——"账号禁用 / 密码轮换
 * 后旧 Token 失效"在代码里并不存在。本测试固定住修复后的不变量：
 *
 * <ul>
 *   <li>状态迁移与 epoch 递增在同一条条件 UPDATE 中完成（不存在只改状态或只加 epoch 的中间态）；</li>
 *   <li>非法迁移（含重复迁移与离开 CLOSED 终态）失败关闭；</li>
 *   <li>密码轮换与凭据撤销追加递增 epoch，并与凭据材料替换处于同一事务；</li>
 *   <li>并发迁移只有一个能成功（compare-and-set，后到者得到冲突而不是覆盖）。</li>
 * </ul>
 *
 * <p>刻意不声明 {@code disabledWithoutDocker}：epoch 写路径是文档中的安全承诺本身，
 * 静默跳过等于重新制造"文档说已解决、实现却没有"的缺陷。没有 Docker 时必须失败。
 */
@Testcontainers
@SpringBootTest(classes = IdentitySecurityEpochWritePathTest.TestApp.class, properties = {
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "spring.main.banner-mode=off"
})
class IdentitySecurityEpochWritePathTest {

    private static final IdentityAuthorityRef AUTHORITY =
            new IdentityAuthorityRef("https://auth.ainer.test");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_identity_epoch_test")
            .withUsername("ainer")
            .withPassword("ainer");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private IdentityFoundationService foundationService;

    @Autowired
    private ServicePrincipalFoundationService servicePrincipalFoundationService;

    @Autowired
    private HumanAccountRepository accountRepository;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanFoundationTables() {
        jdbcTemplate.update("DELETE FROM ainer_identity_oauth_client_binding");
        jdbcTemplate.update("DELETE FROM ainer_identity_service_principal");
        jdbcTemplate.update("DELETE FROM ainer_identity_credential");
        jdbcTemplate.update("DELETE FROM ainer_identity_human_profile");
        jdbcTemplate.update("DELETE FROM ainer_identity_login_identity");
        jdbcTemplate.update("DELETE FROM ainer_identity_human_account");
    }

    @Test
    void disableIncrementsEpochAndPersistsDisabledStatus() {
        HumanAccount account = registerAccount("disable-me");

        IdentityFoundationService.AccountStatusTransition transition =
                foundationService.changeAccountStatus(account.accountId(), AccountStatus.DISABLED);

        assertThat(transition.previousStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(transition.previousEpoch()).isZero();
        assertThat(transition.current().status()).isEqualTo(AccountStatus.DISABLED);
        assertThat(transition.current().securityEpoch()).isEqualTo(1L);
        assertThat(storedEpoch(account.accountId())).isEqualTo(1L);
        // 禁用后认证路径不再解析出可用凭据（授权服务器因此不会签发新 Token）
        assertThat(foundationService.findPasswordCredentialForLogin(
                LoginIdentityType.USERNAME, AUTHORITY.issuer(), "disable-me")).isEmpty();
    }

    @Test
    void lockAndRestoreBothIncrementEpoch() {
        HumanAccount account = registerAccount("lock-me");

        HumanAccount locked = foundationService
                .changeAccountStatus(account.accountId(), AccountStatus.LOCKED).current();
        assertThat(locked.status()).isEqualTo(AccountStatus.LOCKED);
        assertThat(locked.securityEpoch()).isEqualTo(1L);

        // 恢复也递增 epoch：锁定/禁用前签发的 Token 不能随恢复"复活"
        HumanAccount restored = foundationService
                .changeAccountStatus(account.accountId(), AccountStatus.ACTIVE).current();
        assertThat(restored.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(restored.securityEpoch()).isEqualTo(2L);
        assertThat(storedEpoch(account.accountId())).isEqualTo(2L);
    }

    @Test
    void disabledAccountCanBeRestoredByRaisingEpochAgain() {
        HumanAccount account = registerAccount("re-enable-me");

        foundationService.changeAccountStatus(account.accountId(), AccountStatus.DISABLED);
        HumanAccount restored = foundationService
                .changeAccountStatus(account.accountId(), AccountStatus.ACTIVE).current();

        assertThat(restored.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(restored.securityEpoch()).isEqualTo(2L);
    }

    @Test
    void closedIsTerminalAndFailsClosed() {
        HumanAccount account = registerAccount("close-me");

        foundationService.changeAccountStatus(account.accountId(), AccountStatus.CLOSED);
        assertThat(storedEpoch(account.accountId())).isEqualTo(1L);

        // CLOSED 是终态：任何离开它的迁移都失败关闭，且不得偷偷递增 epoch
        assertThatThrownBy(() -> foundationService.changeAccountStatus(
                account.accountId(), AccountStatus.ACTIVE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.HUMAN_ACCOUNT_STATE_CONFLICT);
        assertThatThrownBy(() -> foundationService.changeAccountStatus(
                account.accountId(), AccountStatus.LOCKED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.HUMAN_ACCOUNT_STATE_CONFLICT);
        assertThat(accountRepository.findByAccountId(account.accountId()).orElseThrow().status())
                .isEqualTo(AccountStatus.CLOSED);
        assertThat(storedEpoch(account.accountId())).isEqualTo(1L);
    }

    @Test
    void repeatedTransitionIsNotASilentNoOp() {
        HumanAccount account = registerAccount("twice-disabled");

        foundationService.changeAccountStatus(account.accountId(), AccountStatus.DISABLED);

        assertThatThrownBy(() -> foundationService.changeAccountStatus(
                account.accountId(), AccountStatus.DISABLED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.HUMAN_ACCOUNT_STATE_CONFLICT);
        assertThat(storedEpoch(account.accountId())).isEqualTo(1L);
    }

    @Test
    void unknownAccountTransitionFailsClosed() {
        assertThatThrownBy(() -> foundationService.changeAccountStatus(
                UUID.randomUUID(), AccountStatus.DISABLED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.HUMAN_ACCOUNT_NOT_FOUND);
    }

    @Test
    void passwordRotationRevokesOldMaterialAndIncrementsEpoch() {
        HumanAccount account = registerAccount("rotate-me");
        UUID previousCredentialId = credentialRepository
                .findActive(account.accountId(), CredentialType.PASSWORD).orElseThrow().credentialId();

        IdentityFoundationService.PasswordRotation rotation =
                foundationService.rotatePassword(account.accountId(), "new-password");

        assertThat(rotation.previousEpoch()).isZero();
        assertThat(rotation.newEpoch()).isEqualTo(1L);
        assertThat(rotation.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(rotation.credential().isActive()).isTrue();
        assertThat(rotation.credential().credentialId()).isNotEqualTo(previousCredentialId);
        assertThat(credentialRepository.findActive(account.accountId(), CredentialType.PASSWORD)
                .orElseThrow().credentialId()).isEqualTo(rotation.credential().credentialId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_identity_credential WHERE id = ?",
                String.class, previousCredentialId)).isEqualTo("REVOKED");
        assertThat(storedEpoch(account.accountId())).isEqualTo(1L);
    }

    @Test
    void credentialRevocationIncrementsEpochAndRemovesActiveMaterial() {
        HumanAccount account = registerAccount("revoke-me");

        IdentityFoundationService.CredentialRevocation revocation =
                foundationService.revokeCredential(account.accountId(), CredentialType.PASSWORD);

        assertThat(revocation.previousEpoch()).isZero();
        assertThat(revocation.newEpoch()).isEqualTo(1L);
        assertThat(revocation.revoked().isActive()).isFalse();
        assertThat(credentialRepository.findActive(account.accountId(), CredentialType.PASSWORD))
                .isEmpty();
        assertThat(storedEpoch(account.accountId())).isEqualTo(1L);
    }

    @Test
    void credentialRevocationFailsClosedWithoutActiveMaterial() {
        HumanAccount account = registerAccount("no-passkey");

        assertThatThrownBy(() -> foundationService.revokeCredential(
                account.accountId(), CredentialType.WEBAUTHN_PUBLIC_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.CREDENTIAL_NOT_FOUND);
        // 失败关闭：没有撤销任何材料时不得递增 epoch
        assertThat(storedEpoch(account.accountId())).isZero();
    }

    @Test
    void servicePrincipalDisableIncrementsEpoch() {
        ServicePrincipal principal = servicePrincipalFoundationService
                .registerServicePrincipal(AUTHORITY);
        servicePrincipalFoundationService.bindClient(principal.principalId(), "epoch-machine-client");

        ServicePrincipalFoundationService.PrincipalStatusTransition transition =
                servicePrincipalFoundationService.changePrincipalStatus(
                        principal.principalId(), ServicePrincipalStatus.DISABLED);

        assertThat(transition.previousStatus()).isEqualTo(ServicePrincipalStatus.ACTIVE);
        assertThat(transition.previousEpoch()).isZero();
        assertThat(transition.current().securityEpoch()).isEqualTo(1L);
        assertThat(transition.current().status().canAuthenticate()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT security_epoch FROM ainer_identity_service_principal WHERE id = ?",
                Long.class, principal.principalId())).isEqualTo(1L);

        // 恢复同样递增 epoch（服务凭据泄漏后轮换 secret 再恢复，旧 Token 不会复活）
        ServicePrincipal restored = servicePrincipalFoundationService.changePrincipalStatus(
                principal.principalId(), ServicePrincipalStatus.ACTIVE).current();
        assertThat(restored.securityEpoch()).isEqualTo(2L);
        assertThat(restored.status().canAuthenticate()).isTrue();
    }

    @Test
    void servicePrincipalRepeatedTransitionFailsClosed() {
        ServicePrincipal principal = servicePrincipalFoundationService
                .registerServicePrincipal(AUTHORITY);

        servicePrincipalFoundationService.changePrincipalStatus(
                principal.principalId(), ServicePrincipalStatus.DISABLED);

        assertThatThrownBy(() -> servicePrincipalFoundationService.changePrincipalStatus(
                principal.principalId(), ServicePrincipalStatus.DISABLED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.SERVICE_PRINCIPAL_STATE_CONFLICT);
    }

    @Test
    void concurrentDisableLetsExactlyOneWriterWin() throws Exception {
        HumanAccount account = registerAccount("racing-disable");

        Callable<String> disable = () -> {
            try {
                foundationService.changeAccountStatus(account.accountId(), AccountStatus.DISABLED);
                return "OK";
            } catch (BusinessException exception) {
                return exception.errorCode().code();
            }
        };
        List<String> results;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = executor.submit(disable);
            Future<String> second = executor.submit(disable);
            results = List.of(first.get(), second.get());
        }

        // compare-and-set 语义：恰好一个成功，另一个得到状态冲突（不存在后写覆盖先写）
        assertThat(results).containsExactlyInAnyOrder(
                "OK", IdentityErrorCode.HUMAN_ACCOUNT_STATE_CONFLICT.code());
        assertThat(storedEpoch(account.accountId())).isEqualTo(1L);
    }

    private HumanAccount registerAccount(String username) {
        return foundationService.registerHumanAccountWithPassword(
                AUTHORITY, LoginIdentityType.USERNAME, AUTHORITY.issuer(), username, "initial-password")
                .account();
    }

    private long storedEpoch(UUID accountId) {
        Long epoch = jdbcTemplate.queryForObject(
                "SELECT security_epoch FROM ainer_identity_human_account WHERE id = ?",
                Long.class, accountId);
        return epoch == null ? -1L : epoch;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(dev.ainer.module.identity.IdentityModuleConfiguration.class)
    static class TestApp {
    }
}
