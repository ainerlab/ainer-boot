package dev.ainer.security.token;

/**
 * Resolves a verified access token into a typed {@link AuthenticatedPrincipal} (ADR-0030 §2.2).
 *
 * <p>This is the single authority that interprets raw JWT claims for Foundation code. Implementations
 * (Authorization Server issuance projection and Resource Server request-time resolution) must:
 *
 * <ul>
 *   <li>read {@code token_profile} and {@code claim_contract_version} and fail closed on unknown / missing /
 *       mismatched values;
 *   <li>build an authority-qualified {@link dev.ainer.security.principal.PrincipalSubjectRef} from
 *       {@code iss}, {@code sub} and {@code actor_type} — never trusting a caller-supplied principal;
 *   <li>enforce that {@code USER_*} profiles resolve to a Human principal and {@code SERVICE_V1} to a Service
 *       principal;
 *   <li>never resurrect tenant-bound semantics or fall back to a legacy profile for a Greenfield audience.
 * </ul>
 *
 * <p>Business modules receive only the resulting {@code AuthenticatedPrincipal} and never the raw claims.
 */
public interface TokenProfileResolver {

    AuthenticatedPrincipal resolve(VerifiedJwtClaims claims);
}
