package com.example.retailstore.webapp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.model.request.RegistrationRequest;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "AbcXyz@123";

    @Mock
    private IdentityProviderPort registrationService;

    @Mock
    private CustomerServiceClient customerServiceClient;

    private UserRegistrationService userRegistrationService;

    @BeforeEach
    void setUp() {
        userRegistrationService = new UserRegistrationService(registrationService, customerServiceClient);
    }

    private static RegistrationRequest validRegistrationRequest() {
        return new RegistrationRequest(
                TEST_USERNAME,
                TEST_EMAIL,
                "Test",
                "User",
                TEST_PASSWORD,
                9848022334L,
                "junitAddress",
                null,
                "Test City",
                "Test State",
                "12345",
                "Test Country");
    }

    @Test
    void shouldRegisterUserAndCustomerSuccessfully() {
        when(customerServiceClient.getOrCreateCustomer(any(CustomerRequest.class)))
                .thenReturn(new CustomerResponse(
                        1L,
                        TEST_USERNAME,
                        TEST_EMAIL,
                        "9848022334",
                        "junitAddress",
                        null,
                        "Test City",
                        "Test State",
                        "12345",
                        "Test Country",
                        BigDecimal.valueOf(10_000)));

        CustomerResponse response = userRegistrationService.registerUserAndCustomer(validRegistrationRequest());

        assertThat(response).isNotNull();
        assertThat(response.customerId()).isEqualTo(1L);
        verify(registrationService).registerUser(any(RegistrationRequest.class));
        verify(customerServiceClient).getOrCreateCustomer(any(CustomerRequest.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409})
    void shouldCompensateConfirmedCustomerRejectionAndPropagateOriginalException(int status) {
        var failure = new HttpClientErrorException(HttpStatus.valueOf(status));
        when(customerServiceClient.getOrCreateCustomer(any(CustomerRequest.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> userRegistrationService.registerUserAndCustomer(validRegistrationRequest()))
                .isSameAs(failure);

        verify(registrationService).registerUser(any(RegistrationRequest.class));
        verify(registrationService).deleteUserByUsername(TEST_USERNAME);
    }

    @ParameterizedTest
    @MethodSource("indeterminateCustomerFailures")
    void shouldPreserveKeycloakUserAndPropagateOriginalException(RuntimeException failure) {
        when(customerServiceClient.getOrCreateCustomer(any(CustomerRequest.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> userRegistrationService.registerUserAndCustomer(validRegistrationRequest()))
                .isSameAs(failure);

        verify(registrationService).registerUser(any(RegistrationRequest.class));
        verify(registrationService, never()).deleteUserByUsername(any());
    }

    private static Stream<RuntimeException> indeterminateCustomerFailures() {
        return Stream.of(
                new ResourceAccessException("Response timed out", new SocketTimeoutException("Read timed out")),
                new HttpClientErrorException(HttpStatus.REQUEST_TIMEOUT),
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR),
                new HttpServerErrorException(HttpStatus.BAD_GATEWAY),
                new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT),
                new RestClientException("Cannot decode customer response"));
    }
}
