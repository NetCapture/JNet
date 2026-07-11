package com.jnet.core;

import com.jnet.core.org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitContentsClientRegressionTest {

    @Test
    void githubCreateUsesHeaderTokenAndSharedContentPayload() {
        RecordingExchange exchange = new RecordingExchange()
                .respond(404, "")
                .respond(201, "{\"content\":{\"download_url\":\"https://download/github\"}}");
        GitContentsClient client = GitContentsClient.github(exchange);

        String result = client.createFile(true, "owner", "repo", "dir/file.txt", "gh-token",
                "hello", "commit", "Alice", "alice@example.com");

        assertEquals("https://download/github", result);
        assertEquals(2, exchange.calls.size());
        RecordedCall lookup = exchange.calls.get(0);
        assertEquals("GET", lookup.method);
        assertEquals("https://api.github.com/repos/owner/repo/contents/dir/file.txt", lookup.url);
        assertEquals("token gh-token", lookup.headers.get("Authorization"));
        assertFalse(lookup.url.contains("access_token"));

        RecordedCall create = exchange.calls.get(1);
        assertEquals("PUT", create.method);
        assertEquals(lookup.url, create.url);
        JSONObject payload = new JSONObject(create.body);
        assertEquals(JNetUtils.encodeBase64("hello"), payload.getString("content"));
        assertEquals("commit", payload.getString("message"));
        assertFalse(payload.has("sha"));
        assertEquals("Alice", payload.getJSONObject("committer").getString("name"));
    }

    @Test
    void giteeCreateUsesQueryTokenWithoutAuthorizationHeader() {
        RecordingExchange exchange = new RecordingExchange()
                .respond(404, "")
                .respond(201, "{\"content\":{\"download_url\":\"https://download/gitee\"}}");
        GitContentsClient client = GitContentsClient.gitee(exchange);

        String result = client.createFile(true, "owner", "repo", "/dir/file.txt", "gitee-token",
                "hello", "commit", "", "");

        assertEquals("https://download/gitee", result);
        assertEquals(2, exchange.calls.size());
        for (RecordedCall call : exchange.calls) {
            assertTrue(call.url.endsWith("/contents/dir/file.txt?access_token=gitee-token"));
            assertFalse(call.headers.containsKey("Authorization"));
            assertEquals("application/json", call.headers.get("Accept"));
        }
    }

    @Test
    void updateCarriesProviderShaAndReturnsRawResponseBody() {
        RecordingExchange exchange = new RecordingExchange()
                .respond(200, "{\"path\":\"file.txt\",\"sha\":\"abc123\",\"type\":\"file\"}")
                .respond(200, "{\"updated\":true}");
        GitContentsClient client = GitContentsClient.github(exchange);

        String result = client.updateContent("owner", "repo", "file.txt", "token", "next",
                "update", "", "");

        assertEquals("{\"updated\":true}", result);
        assertEquals("abc123", new JSONObject(exchange.calls.get(1).body).getString("sha"));
    }

    @Test
    void appendReusesTheContentLookupInsteadOfFetchingTheSameFileTwice() {
        RecordingExchange exchange = new RecordingExchange()
                .respond(200, "{\"path\":\"file.txt\",\"sha\":\"abc123\",\"type\":\"file\","
                        + "\"content\":\"YmVmb3Jl\",\"encoding\":\"base64\"}")
                .respond(200, "{\"updated\":true}");
        GitContentsClient client = GitContentsClient.github(exchange);

        String result = client.append("owner", "repo", "file.txt", "token", "after",
                "append", "", "");

        assertEquals("{\"updated\":true}", result);
        assertEquals(2, exchange.calls.size());
        assertEquals("GET", exchange.calls.get(0).method);
        assertEquals("PUT", exchange.calls.get(1).method);
        JSONObject payload = new JSONObject(exchange.calls.get(1).body);
        assertEquals("before\r\nafter", JNetUtils.decodeBase64(payload.getString("content")));
        assertEquals("abc123", payload.getString("sha"));
    }

    @Test
    void giteeDeleteAuthenticatesTheEndpointAndEscapesTheCommitPayload() {
        RecordingExchange exchange = new RecordingExchange()
                .respond(200, "{\"path\":\"dir/file.txt\",\"sha\":\"delete-sha\",\"type\":\"file\"}")
                .respond(200, "{\"deleted\":true}");
        GitContentsClient client = GitContentsClient.gitee(exchange);

        String result = client.deleteFile("owner", "repo", "dir/file.txt", "gitee-token",
                "delete \"file\"", "Alice", "alice@example.com");

        assertEquals("{\"deleted\":true}", result);
        assertEquals(2, exchange.calls.size());
        RecordedCall delete = exchange.calls.get(1);
        assertEquals("DELETE", delete.method);
        assertEquals("https://gitee.com/api/v5/repos/owner/repo/contents/dir/file.txt"
                + "?access_token=gitee-token", delete.url);
        assertFalse(delete.headers.containsKey("Authorization"));
        JSONObject payload = new JSONObject(delete.body);
        assertEquals("delete \"file\"", payload.getString("message"));
        assertEquals("delete-sha", payload.getString("sha"));
        assertEquals("Alice", payload.getJSONObject("committer").getString("name"));
    }

    @Test
    void commitHistoryKeepsProviderSpecificPathAndAuthenticationRules() {
        RecordingExchange githubExchange = new RecordingExchange().respond(200, "[]");
        GitContentsClient github = GitContentsClient.github(githubExchange);
        RecordingExchange giteeExchange = new RecordingExchange().respond(200, "[]");
        GitContentsClient gitee = GitContentsClient.gitee(giteeExchange);

        assertEquals("[]", github.getCommits("owner", "repo", "dir/file name.txt", "gh-token", 2, 50));
        assertEquals("[]", gitee.getCommits(
                "owner", "repo", "dir/file name#.txt", "gitee-token", 3, 25));

        RecordedCall githubCall = githubExchange.calls.get(0);
        assertEquals("https://api.github.com/repos/owner/repo/commits"
                + "?path=%2Fdir%2Ffile+name.txt&page=2&per_page=50", githubCall.url);
        assertEquals("token gh-token", githubCall.headers.get("Authorization"));

        RecordedCall giteeCall = giteeExchange.calls.get(0);
        assertEquals("https://gitee.com/api/v5/repos/owner/repo/commits"
                + "?path=dir%2Ffile+name%23.txt&page=3&per_page=25&access_token=gitee-token",
                giteeCall.url);
        assertFalse(giteeCall.headers.containsKey("Authorization"));
    }

    @Test
    void transportFailuresKeepLegacyEmptyResults() {
        GitContentsClient client = GitContentsClient.github((method, url, headers, body) -> {
            throw new IOException("offline");
        });

        assertEquals("", client.getRepositoryInfo("owner", "repo", "token"));
        assertTrue(client.getItems("owner", "repo", "file.txt", "token").isEmpty());
        assertFalse(client.fileExists("owner", "repo", "file.txt", "token"));
        assertEquals("", client.deleteFile("owner", "repo", "file.txt", "token", "delete", "", ""));
    }

    @Test
    void fileFacadeRejectsMissingInputBeforeAttemptingARemoteCreate() {
        assertEquals("", GithubHelper.createFile(
                "owner", "repo", "file.txt", "token", (File) null, "commit"));
        assertEquals("", GiteeHelper.createFile(
                "owner", "repo", "file.txt", "token", (File) null, "commit"));
    }

    @Test
    void nonSuccessfulContentResponsesAreNeverParsedAsItems() {
        RecordingExchange githubExchange = new RecordingExchange()
                .respond(404, "{\"message\":\"Not Found\",\"path\":\"fake\"}");
        RecordingExchange giteeExchange = new RecordingExchange()
                .respond(404, "{\"message\":\"Not Found\",\"path\":\"fake\"}");

        assertTrue(GitContentsClient.github(githubExchange)
                .getItems("owner", "repo", "missing.txt", "token").isEmpty());
        assertTrue(GitContentsClient.gitee(giteeExchange)
                .getItems("owner", "repo", "missing.txt", "token").isEmpty());
    }

    @Test
    void batchInputIsFullyValidatedBeforeAnyRemoteWriteStarts() {
        RecordingExchange exchange = new RecordingExchange();
        GitContentsClient client = GitContentsClient.github(exchange);
        List<Map<String, String>> files = new ArrayList<>();
        files.add(Map.of("path", "first.txt", "content", "first"));
        files.add(Map.of("content", "missing path"));

        assertThrows(IllegalArgumentException.class,
                () -> client.batchCreateFiles("owner", "repo", files, "token", "batch"));
        assertTrue(exchange.calls.isEmpty());
    }

    @Test
    void cancelingBatchInterruptsTheCoordinatorBeforeMoreWritesStart() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        GitContentsClient client = GitContentsClient.github((method, url, headers, body) -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                interrupted.countDown();
                throw new IOException("interrupted", error);
            }
            return response(404, "");
        });
        List<Map<String, String>> files = List.of(
                Map.of("path", "first.txt", "content", "first"),
                Map.of("path", "second.txt", "content", "second"));

        CompletableFuture<List<String>> future = client.batchCreateFiles(
                "owner", "repo", files, "token", "batch");
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertTrue(future.cancel(true));
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    @Test
    void publicReadsOmitBlankAuthenticationInsteadOfSendingNullTokens() {
        RecordingExchange githubExchange = new RecordingExchange().respond(200, "{}");
        RecordingExchange giteeExchange = new RecordingExchange().respond(200, "{}");

        GitContentsClient.github(githubExchange).getRepositoryInfo("owner", "repo", "  ");
        GitContentsClient.gitee(giteeExchange).getRepositoryInfo("owner", "repo", null);

        assertFalse(githubExchange.calls.get(0).headers.containsKey("Authorization"));
        assertFalse(giteeExchange.calls.get(0).url.contains("access_token"));
    }

    @Test
    void contentPathsAreEncodedPerSegment() {
        RecordingExchange exchange = new RecordingExchange().respond(404, "");

        GitContentsClient.github(exchange).getItems(
                "owner", "repo", "dir/a #?.txt", "token");

        assertEquals(
                "https://api.github.com/repos/owner/repo/contents/dir/a%20%23%3F.txt",
                exchange.calls.get(0).url);
    }

    @Test
    void directoryDeletionTreatsSymlinksAsLeafEntries() {
        RecordingExchange exchange = new RecordingExchange()
                .respond(200, "[{\"path\":\"dir/link\",\"sha\":\"link-sha\",\"type\":\"symlink\"}]")
                .respond(200, "{\"deleted\":true}");

        GitContentsClient.github(exchange).deleteDirectory(
                "owner", "repo", "dir", "token", "delete", "", "");

        assertEquals(2, exchange.calls.size());
        assertEquals("GET", exchange.calls.get(0).method);
        assertEquals("DELETE", exchange.calls.get(1).method);
    }

    private static Response response(int code, String body) {
        return Response.success(null).code(code).body(body).build();
    }

    private static final class RecordingExchange implements GitContentsClient.Exchange {
        private final Deque<Response> responses = new ArrayDeque<>();
        private final List<RecordedCall> calls = new ArrayList<>();

        private RecordingExchange respond(int code, String body) {
            responses.addLast(response(code, body));
            return this;
        }

        @Override
        public Response execute(String method, String url, Map<String, String> headers, String body)
                throws IOException {
            calls.add(new RecordedCall(method, url, headers, body));
            if (responses.isEmpty()) {
                throw new IOException("No response configured");
            }
            return responses.removeFirst();
        }
    }

    private static final class RecordedCall {
        private final String method;
        private final String url;
        private final Map<String, String> headers;
        private final String body;

        private RecordedCall(String method, String url, Map<String, String> headers, String body) {
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body;
        }
    }
}
