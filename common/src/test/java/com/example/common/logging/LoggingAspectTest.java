package com.example.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

class LoggingAspectTest {

    private final ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    private final MethodSignature signature = mock(MethodSignature.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final LoggingAspect aspect = new LoggingAspect(environment);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger targetLogger = (Logger) LoggerFactory.getLogger(LoggedService.class);
    private final Logger aspectLogger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
    private Level previousLevel;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        previousLevel = targetLogger.getLevel();
        targetLogger.setLevel(Level.ALL);
        appender.start();
        targetLogger.addAppender(appender);
        aspectLogger.addAppender(appender);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new LoggedService());
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(signature.getMethod()).thenReturn(LoggedService.class.getMethod("execute"));
        when(signature.getName()).thenReturn("execute");
        when(signature.getDeclaringTypeName()).thenReturn(LoggedService.class.getName());
    }

    @AfterEach
    void tearDown() {
        targetLogger.detachAppender(appender);
        aspectLogger.detachAppender(appender);
        targetLogger.setLevel(previousLevel);
        appender.stop();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "password",
                "userPassword",
                "dbpassword",
                "password1",
                "passwd",
                "pwd",
                "passphrase",
                "clientSecret",
                "secrets",
                "accessToken",
                "token2",
                "OAuth2Token",
                "refresh_token",
                "credentials",
                "credential",
                "authorization",
                "authentication",
                "auth",
                "apiKey",
                "APIKey",
                "api_key",
                "privateKey",
                "accessKey",
                "creditCard",
                "credit_card",
                "creditcardnumber",
                "ssn",
                "customerSsn",
                "SSNValue",
                "getSSN",
                "getAPIKey"
            })
    void redactsSensitiveArgumentsAndResults(String name) throws Throwable {
        when(signature.getName()).thenReturn(name);
        when(signature.getParameterNames()).thenReturn(new String[] {name});
        when(joinPoint.getArgs()).thenReturn(new Object[] {"sensitive-value"});
        when(joinPoint.proceed()).thenReturn("sensitive-value");

        assertThat(aspect.logMethod(joinPoint)).isEqualTo("sensitive-value");

        assertThat(messages())
                .contains(
                        name + "() args :: -> " + name + " : REDACTED",
                        name + "() Returned : REDACTED")
                .noneMatch(message -> message.contains("sensitive-value"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "className",
                "businessName",
                "getClassName",
                "getBusinessName",
                "assignment",
                "tokenizer",
                "secretariat"
            })
    void preservesUnrelatedNamesAndSanitizesScalarValues(String name) throws Throwable {
        when(signature.getName()).thenReturn(name);
        when(signature.getParameterNames()).thenReturn(new String[] {name});
        when(joinPoint.getArgs()).thenReturn(new Object[] {"safe\r\nvalue\t"});
        when(joinPoint.proceed()).thenReturn("safe\r\nvalue\t");

        aspect.logMethod(joinPoint);

        assertThat(messages())
                .contains(
                        name + "() args :: -> " + name + " : safe_value_",
                        name + "() Returned : safe_value_");
    }

    @ParameterizedTest
    @MethodSource("unavailableParameterNames")
    void redactsArgumentsWithoutUsableParameterNames(String[] names) throws Throwable {
        when(signature.getParameterNames()).thenReturn(names);
        when(joinPoint.getArgs()).thenReturn(new Object[] {"sensitive-value"});

        aspect.logMethod(joinPoint);

        assertThat(messages())
                .contains("execute() args :: -> arg0 : REDACTED")
                .noneMatch(message -> message.contains("sensitive-value"));
    }

    static Stream<Arguments> unavailableParameterNames() {
        return Stream.<String[]>of(
                        null,
                        new String[0],
                        new String[] {null},
                        new String[] {""},
                        new String[] {" "})
                .map(names -> Arguments.of((Object) names));
    }

    @Test
    void redactsOnlyArgumentsMissingNamesWhenMetadataIsIncomplete() throws Throwable {
        when(signature.getParameterNames()).thenReturn(new String[] {"businessName"});
        when(joinPoint.getArgs()).thenReturn(new Object[] {"safe", "sensitive-value"});

        aspect.logMethod(joinPoint);

        assertThat(messages())
                .contains("execute() args :: -> businessName : safe, arg1 : REDACTED");
    }

    @ParameterizedTest
    @MethodSource("complexValues")
    void redactsComplexArgumentsAndResultsWithoutRenderingThem(Object value) throws Throwable {
        when(signature.getParameterNames()).thenReturn(new String[] {"request"});
        when(joinPoint.getArgs()).thenReturn(new Object[] {value});
        when(joinPoint.proceed()).thenReturn(value);

        assertThat(aspect.logMethod(joinPoint)).isSameAs(value);

        assertThat(messages())
                .contains(
                        "execute() args :: -> request : REDACTED", "execute() Returned : REDACTED");
    }

    static Stream<Arguments> complexValues() {
        return Stream.of(
                        new Object() {
                            @Override
                            public String toString() {
                                throw new AssertionError("Complex values must not be rendered");
                            }
                        },
                        new String[] {"sensitive-value"},
                        new char[] {'s', 'e', 'c', 'r', 'e', 't'},
                        List.of("sensitive-value"),
                        Map.of("password", "sensitive-value"),
                        Optional.of("sensitive-value"))
                .map(value -> Arguments.of(value));
    }

    @ParameterizedTest
    @MethodSource("scalarValues")
    void preservesKnownScalarArgumentsAndResults(Object value) throws Throwable {
        when(signature.getParameterNames()).thenReturn(new String[] {"value"});
        when(joinPoint.getArgs()).thenReturn(new Object[] {value});
        when(joinPoint.proceed()).thenReturn(value);

        aspect.logMethod(joinPoint);

        assertThat(messages())
                .contains("execute() args :: -> value : " + value, "execute() Returned : " + value);
    }

    static Stream<Object> scalarValues() {
        return Stream.of("safe", true, 'x', (byte) 1, (short) 2, 3, 4L, 5.0f, 6.0d);
    }

    @Test
    void preservesNullArgumentAndOmitsNullResult() throws Throwable {
        when(signature.getParameterNames()).thenReturn(new String[] {"value"});
        when(joinPoint.getArgs()).thenReturn(new Object[] {null});

        assertThat(aspect.logMethod(joinPoint)).isNull();

        assertThat(messages())
                .contains("execute() args :: -> value : null")
                .noneMatch(message -> message.contains("Returned"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void omitsExceptionMessagesAndRethrowsOriginalException(String profile) throws Throwable {
        environment.setActiveProfiles(profile);
        RuntimeException exception =
                new RuntimeException(
                        "password=outer-secret\r\nforged",
                        new IllegalStateException("token=cause-secret\r\nforged\t\u001B[31m"));
        exception.addSuppressed(new RuntimeException("password=suppressed-secret\nforged"));
        when(joinPoint.proceed()).thenThrow(exception);

        assertThat(catchThrowable(() -> aspect.logMethod(joinPoint))).isSameAs(exception);

        ILoggingEvent event = appender.list.getLast();
        assertThat(event.getArgumentArray()[2]).isEqualTo(IllegalStateException.class.getName());
        if (profile.equals("dev")) {
            assertThat(event.getArgumentArray()[3]).isEqualTo(RuntimeException.class.getName());
        }
        assertThat(event.getFormattedMessage())
                .doesNotContain(
                        "outer-secret",
                        "cause-secret",
                        "suppressed-secret",
                        "forged",
                        "\r",
                        "\n",
                        "\t",
                        "\u001B");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev"})
    void preservesNullCauseFallback(String profile) throws Throwable {
        environment.setActiveProfiles(profile);
        RuntimeException exception = new RuntimeException("outer");
        when(joinPoint.proceed()).thenThrow(exception);

        assertThat(catchThrowable(() -> aspect.logMethod(joinPoint))).isSameAs(exception);

        assertThat(appender.list.getLast().getArgumentArray()[2]).isEqualTo("NULL");
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    static class LoggedService {
        @Loggable
        public void execute() {}
    }
}
