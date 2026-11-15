package badee.updaterComponent.spokes.kanji.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Failure messages carry the status, rate-limit headers and the start of the body (D69). */
class KanjiFileFetcherTest {

	private static final String RATE_LIMIT_BODY = """
			{"message":"API rate limit exceeded for 1.2.3.4.",
			 "documentation_url":"https://docs.github.com/rest/overview/rate-limits-for-the-rest-api"}
			""";

	private HttpServer server;
	private String baseUrl;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/limited", exchange -> {
			exchange.getResponseHeaders().add("X-RateLimit-Limit", "60");
			exchange.getResponseHeaders().add("X-RateLimit-Remaining", "0");
			exchange.getResponseHeaders().add("X-RateLimit-Reset", "1763150000");
			respond(exchange, 403, RATE_LIMIT_BODY);
		});
		server.createContext("/long", exchange -> respond(exchange, 500, "x".repeat(1000)));
		server.createContext("/redirect", exchange -> {
			exchange.getResponseHeaders().add("Location", "/releases/tag/r20250816");
			respond(exchange, 302, "");
		});
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void downloadFailureShowsStatusRateLimitHeadersAndBody() {
		IOException e = assertThrows(IOException.class,
				() -> new KanjiFileFetcher().fetch(baseUrl + "/limited", "kanjivg.xml"));

		String message = e.getMessage();
		assertTrue(message.startsWith("Failed to download " + baseUrl + "/limited, HTTP 403"), message);
		assertTrue(message.contains("x-ratelimit-limit=60"), message);
		assertTrue(message.contains("x-ratelimit-remaining=0"), message);
		assertTrue(message.contains("x-ratelimit-reset=1763150000"), message);
		assertFalse(message.contains("retry-after"), "absent headers are left out: " + message);
		assertTrue(message.contains("API rate limit exceeded"), message);
		assertFalse(message.contains("\n"), "the body is put on one line: " + message);
	}

	@Test
	void missingRedirectShowsStatusAndHeaders() {
		IOException e = assertThrows(IOException.class,
				() -> new KanjiFileFetcher().redirectLocation(baseUrl + "/limited"));

		assertTrue(e.getMessage().startsWith("Expected a redirect from " + baseUrl + "/limited, HTTP 403"),
				e.getMessage());
		assertTrue(e.getMessage().contains("x-ratelimit-remaining=0"), e.getMessage());
	}

	@Test
	void redirectLocationIsResolvedAgainstTheRequestUrl() throws Exception {
		assertEquals(baseUrl + "/releases/tag/r20250816",
				new KanjiFileFetcher().redirectLocation(baseUrl + "/redirect"));
	}

	@Test
	void longBodiesAreCut() {
		IOException e = assertThrows(IOException.class,
				() -> new KanjiFileFetcher().fetch(baseUrl + "/long", "x.xml"));

		assertTrue(e.getMessage().endsWith("x".repeat(KanjiFileFetcher.BODY_LIMIT) + "..."), e.getMessage());
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}
}
