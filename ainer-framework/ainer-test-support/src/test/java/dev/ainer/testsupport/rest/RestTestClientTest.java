package dev.ainer.testsupport.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RestTestClientTest {

    @Test
    void buildsALocalServerBaseUrl() {
        RestTestClient client = new RestTestClient(new TestRestTemplate(), "http://localhost:8080");
        assertThat(client.baseUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    void forLocalServerAlwaysUsesLocalhost() {
        RestTestClient client = RestTestClient.forLocalServer(new TestRestTemplate(), 9876);
        assertThat(client.baseUrl()).isEqualTo("http://localhost:9876");
    }
}