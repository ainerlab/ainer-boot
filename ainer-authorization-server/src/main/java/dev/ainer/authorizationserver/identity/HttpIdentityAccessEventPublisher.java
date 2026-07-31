package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.IdentityAccessEventPublicationException;
import dev.ainer.module.identity.account.application.IdentityAccessEventPublisher;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.security.client.ClientCredentialsServiceTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

final class HttpIdentityAccessEventPublisher implements IdentityAccessEventPublisher {

    static final String AUTHENTICATION_REJECTED =
            "AINER.IDENTITY.ACCESS_EVENT_AUTHENTICATION_REJECTED";
    static final String DELIVERY_REJECTED =
            "AINER.IDENTITY.ACCESS_EVENT_DELIVERY_REJECTED";
    static final String TARGET_UNAVAILABLE =
            "AINER.IDENTITY.ACCESS_EVENT_TARGET_UNAVAILABLE";

    private final RestClient restClient;
    private final ClientCredentialsServiceTokenProvider tokenProvider;

    HttpIdentityAccessEventPublisher(
            RestClient restClient,
            ClientCredentialsServiceTokenProvider tokenProvider) {
        this.restClient = restClient;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void publish(IdentityAccessEvent event) {
        try {
            restClient.post()
                    .uri("/internal/identity/access-events")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.accessToken())
                    .body(IdentityAccessEventRequest.from(event))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
            throw new IdentityAccessEventPublicationException(AUTHENTICATION_REJECTED, exception);
        } catch (HttpClientErrorException exception) {
            throw new IdentityAccessEventPublicationException(DELIVERY_REJECTED, exception);
        } catch (RestClientException | dev.ainer.security.client.ServiceTokenException exception) {
            throw new IdentityAccessEventPublicationException(TARGET_UNAVAILABLE, exception);
        }
    }
}
