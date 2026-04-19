package com.example.wallet.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.wallet.aws.EventBridgeEventPublisher;
import com.example.wallet.exception.AccountNotFoundException;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.facade.WalletFacade;
import com.example.wallet.repository.DynamoDbAccountRepository;
import com.example.wallet.service.DynamoDbTransferExecutionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Single Lambda behind HTTP API (SAM), using the same {@link WalletFacade} as the Spring Boot app with
 * {@code wallet.aws.enabled=true}.
 */
public class WalletApiLambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(WalletApiLambdaHandler.class);

    private static volatile Holder HOLDER;

    private static final class Holder {
        final WalletFacade wallet;
        final ObjectMapper mapper = new ObjectMapper();

        Holder(WalletFacade wallet) {
            this.wallet = wallet;
        }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Holder h = holder();
        String method = Optional.ofNullable(input.getHttpMethod()).orElse("GET").toUpperCase();
        String path = normalizePath(input.getPath());
        String correlationId = Optional.ofNullable(input.getHeaders())
                .map(m -> m.get("X-Correlation-Id"))
                .or(() -> Optional.ofNullable(input.getHeaders()).map(m -> m.get("x-correlation-id")))
                .orElse(context.getAwsRequestId());

        log.info("lambda_request method={} path={} correlationId={}", method, path, correlationId);

        try {
            return route(h, method, path, input);
        } catch (AccountNotFoundException e) {
            return json(404, Map.of("error", e.getMessage()), h.mapper);
        } catch (InsufficientFundsException | IllegalArgumentException e) {
            return json(400, Map.of("error", e.getMessage()), h.mapper);
        } catch (Exception e) {
            log.error("lambda_unhandled", e);
            return json(500, Map.of("error", "Internal error"), h.mapper);
        }
    }

    private static APIGatewayProxyResponseEvent route(
            Holder h, String method, String path, APIGatewayProxyRequestEvent input) {

        return switch (method) {
            case "GET" -> switch (path) {
                case "accounts" -> handleListAccounts(h, input);
                case String p when p.startsWith("accounts/") -> handleGetAccount(h, p);
                default -> notFound(method, path, h.mapper);
            };
            case "POST" -> switch (path) {
                case "accounts" -> handleCreateAccount(h, input);
                case "deposit" -> handleDeposit(h, input);
                case "transfer" -> handleTransfer(h, input);
                default -> notFound(method, path, h.mapper);
            };
            default -> notFound(method, path, h.mapper);
        };
    }

    private static APIGatewayProxyResponseEvent handleListAccounts(Holder h, APIGatewayProxyRequestEvent input) {
        Integer limit = queryInt(input, "limit");
        String cursor = query(input, "cursor");
        return json(200, h.wallet.listAccounts(limit, cursor), h.mapper);
    }

    private static APIGatewayProxyResponseEvent handleGetAccount(Holder h, String path) {
        String id = path.substring("accounts/".length());
        return json(200, h.wallet.getAccount(id), h.mapper);
    }

    private static APIGatewayProxyResponseEvent handleCreateAccount(Holder h, APIGatewayProxyRequestEvent input) {
        JsonNode body = readBody(h.mapper, input.getBody());
        BigDecimal initial = body != null && body.hasNonNull("initialBalance")
                ? new BigDecimal(body.get("initialBalance").asText())
                : null;
        return json(201, h.wallet.createAccount(initial), h.mapper);
    }

    private static APIGatewayProxyResponseEvent handleDeposit(Holder h, APIGatewayProxyRequestEvent input) {
        JsonNode body = readBody(h.mapper, input.getBody());
        if (body == null || !body.hasNonNull("accountId") || !body.hasNonNull("amount")) {
            throw new IllegalArgumentException("accountId and amount required");
        }
        return json(200, h.wallet.deposit(
                body.get("accountId").asText(),
                new BigDecimal(body.get("amount").asText())), h.mapper);
    }

    private static APIGatewayProxyResponseEvent handleTransfer(Holder h, APIGatewayProxyRequestEvent input) {
        JsonNode body = readBody(h.mapper, input.getBody());
        if (body == null
                || !body.hasNonNull("transactionId")
                || !body.hasNonNull("fromAccountId")
                || !body.hasNonNull("toAccountId")
                || !body.hasNonNull("amount")) {
            throw new IllegalArgumentException("transactionId, fromAccountId, toAccountId, amount required");
        }
        return json(200, h.wallet.transfer(
                body.get("transactionId").asText(),
                body.get("fromAccountId").asText(),
                body.get("toAccountId").asText(),
                new BigDecimal(body.get("amount").asText())), h.mapper);
    }

    private static APIGatewayProxyResponseEvent notFound(String method, String path, ObjectMapper mapper) {
        return json(404, Map.of("error", "Not found: " + method + " " + path), mapper);
    }

    private static JsonNode readBody(ObjectMapper mapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON body", e);
        }
    }

    private static String query(APIGatewayProxyRequestEvent input, String name) {
        if (input.getQueryStringParameters() == null) {
            return null;
        }
        return input.getQueryStringParameters().get(name);
    }

    private static Integer queryInt(APIGatewayProxyRequestEvent input, String name) {
        String v = query(input, name);
        if (v == null || v.isBlank()) {
            return null;
        }
        return Integer.parseInt(v);
    }

    /**
     * Strip optional stage prefix ({@code /prod/accounts} → {@code accounts}).
     */
    static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String p = path.startsWith("/") ? path.substring(1) : path;
        int slash = p.indexOf('/');
        if (slash > 0) {
            String first = p.substring(0, slash);
            if (first.matches("prod|dev|staging|test") || first.matches("v[0-9]+") || "$default".equals(first)) {
                p = p.substring(slash + 1);
            }
        }
        return p.endsWith("/") && p.length() > 1 ? p.substring(0, p.length() - 1) : p;
    }

    private static APIGatewayProxyResponseEvent json(int status, Object body, ObjectMapper mapper) {
        var resp = new APIGatewayProxyResponseEvent();
        resp.setStatusCode(status);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        resp.setHeaders(headers);
        try {
            resp.setBody(mapper.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
        return resp;
    }

    private static Holder holder() {
        if (HOLDER == null) {
            synchronized (WalletApiLambdaHandler.class) {
                if (HOLDER == null) {
                    HOLDER = buildHolder();
                }
            }
        }
        return HOLDER;
    }

    private static Holder buildHolder() {
        String accountsTable = requiredEnv("ACCOUNTS_TABLE");
        String idempotencyTable = requiredEnv("IDEMPOTENCY_TABLE");
        String busName = Optional.ofNullable(System.getenv("EVENT_BUS_NAME")).orElse("default");
        String source = Optional.ofNullable(System.getenv("EVENT_SOURCE")).orElse("wallet.wallet");
        String region = Optional.ofNullable(System.getenv("AWS_REGION")).orElse("eu-west-1");
        String endpoint = System.getenv("AWS_ENDPOINT_URL");

        var ddbBuilder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (endpoint != null && !endpoint.isBlank()) {
            ddbBuilder.endpointOverride(URI.create(endpoint));
        }
        DynamoDbClient ddb = ddbBuilder.build();

        var ebBuilder = EventBridgeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (endpoint != null && !endpoint.isBlank()) {
            ebBuilder.endpointOverride(URI.create(endpoint));
        }
        EventBridgeClient eb = ebBuilder.build();

        ObjectMapper om = new ObjectMapper();
        var publisher = new EventBridgeEventPublisher(eb, busName, source, om);
        var repo = new DynamoDbAccountRepository(ddb, accountsTable);
        var transfers = new DynamoDbTransferExecutionService(ddb, accountsTable, idempotencyTable, repo, publisher);
        var wallet = new WalletFacade(repo, publisher, transfers);
        return new Holder(wallet);
    }

    private static String requiredEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return v;
    }
}
