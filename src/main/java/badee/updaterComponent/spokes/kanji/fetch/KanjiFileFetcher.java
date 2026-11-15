package badee.updaterComponent.spokes.kanji.fetch;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.springframework.stereotype.Component;

/**
 * Shared downloader for the Kanji spoke's sub-sources, all of which publish a
 * single gzipped file (kanjidic2.xml.gz, radkfile.gz, kanjivg-*.xml.gz).
 */
@Component
public class KanjiFileFetcher {

	private final HttpClient httpClient = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private final HttpClient noRedirectClient = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	/**
	 * Downloads {@code url} and decompresses it into a fresh staging directory,
	 * returning the path to the decompressed file named {@code fileName}.
	 */
	public Path fetch(String url, String fileName) throws IOException, InterruptedException {
		Path stagingDir = Files.createTempDirectory("kanji-");
		Path gzFile = stagingDir.resolve(fileName + ".gz");
		Path file = stagingDir.resolve(fileName);

		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
		HttpResponse<Path> response = httpClient.send(request,
				HttpResponse.BodyHandlers.ofFile(gzFile));
		if (response.statusCode() != 200) {
			throw failure("Failed to download " + url, response, readStart(gzFile));
		}

		try (InputStream in = new GZIPInputStream(Files.newInputStream(gzFile))) {
			Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
		}
		return file;
	}

	/**
	 * Requests {@code url} without following redirects and returns the absolute
	 * {@code Location} it redirects to (e.g. GitHub's {@code releases/latest}).
	 */
	public String redirectLocation(String url) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
		HttpResponse<Void> response = noRedirectClient.send(request, HttpResponse.BodyHandlers.discarding());
		if (response.statusCode() / 100 != 3) {
			throw failure("Expected a redirect from " + url, response, null);
		}
		String location = response.headers().firstValue("Location")
				.orElseThrow(() -> new IOException("Redirect from " + url + " has no Location header"));
		return URI.create(url).resolve(location).toString();
	}

	/** Response headers that explain a refusal (rate limits, back-off), logged when present. */
	static final List<String> DIAGNOSTIC_HEADERS = List.of(
			"retry-after", "x-ratelimit-limit", "x-ratelimit-remaining", "x-ratelimit-reset",
			"x-ratelimit-resource", "x-github-request-id");

	/** Longest part of an error body put into the exception message. */
	static final int BODY_LIMIT = 300;

	/**
	 * An exception that says why a request failed: the status, the diagnostic headers that
	 * are present, and the start of the body. A GitHub 403, for example, then shows whether a
	 * rate limit was hit ({@code x-ratelimit-remaining=0}) or something else refused it.
	 */
	static IOException failure(String what, HttpResponse<?> response, String body) {
		StringBuilder message = new StringBuilder(what).append(", HTTP ").append(response.statusCode());
		for (String header : DIAGNOSTIC_HEADERS) {
			response.headers().firstValue(header)
					.ifPresent(value -> message.append(", ").append(header).append('=').append(value));
		}
		if (body != null && !body.isBlank()) {
			String oneLine = body.strip().replaceAll("\\s+", " ");
			if (oneLine.length() > BODY_LIMIT) {
				oneLine = oneLine.substring(0, BODY_LIMIT) + "...";
			}
			message.append(", body: ").append(oneLine);
		}
		return new IOException(message.toString());
	}

	/** The start of a downloaded file as text: an error page when the status is not 200. */
	private static String readStart(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			return new String(in.readNBytes(BODY_LIMIT + 1), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}
}
