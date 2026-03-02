/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.examples.quickstart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * SearchAgentExample - Demonstrates how to build real-time search Agents using Tavily and
 * Firecrawl.
 *
 * <p>This example creates two Agents, each enhanced with a different web search tool:
 *
 * <ul>
 *   <li>Agent 1: Uses Tavily Search API for real-time web search
 *   <li>Agent 2: Uses Firecrawl Search API for real-time web search with scraping
 * </ul>
 *
 * <p>Required environment variables:
 *
 * <ul>
 *   <li>{@code DASHSCOPE_API_KEY} - DashScope API key for the LLM
 *   <li>{@code TAVILY_API_KEY} - Tavily API key (get at https://tavily.com)
 *   <li>{@code FIRECRAWL_API_KEY} - Firecrawl API key (get at https://firecrawl.dev)
 * </ul>
 */
public class SearchAgentExample {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        ExampleUtils.printWelcome(
                "Search Agent Example",
                "This example demonstrates real-time web search agents.\n"
                        + "Two agents are available:\n"
                        + "  1. Tavily Search Agent - powered by Tavily Search API\n"
                        + "  2. Firecrawl Search Agent - powered by Firecrawl Search API\n");

        String dashScopeApiKey = ExampleUtils.getDashScopeApiKey();

        System.out.println("Select search agent:");
        System.out.println("  1 - Tavily Search Agent");
        System.out.println("  2 - Firecrawl Search Agent");
        System.out.print("\nYour choice (1 or 2): ");
        String choice = ExampleUtils.readLine().trim();

        ReActAgent agent;
        if ("2".equals(choice)) {
            agent = buildFirecrawlAgent(dashScopeApiKey);
        } else {
            agent = buildTavilyAgent(dashScopeApiKey);
        }

        ExampleUtils.startChat(agent);
    }

    private static ReActAgent buildTavilyAgent(String dashScopeApiKey) throws Exception {
        String tavilyApiKey =
                ExampleUtils.getApiKey("TAVILY_API_KEY", "Tavily", "https://tavily.com");

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new TavilySearchTools(tavilyApiKey));

        System.out.println("\nRegistered tools:");
        System.out.println("  - tavily_search: Search the web in real-time via Tavily\n");

        return ReActAgent.builder()
                .name("TavilySearchAgent")
                .sysPrompt(
                        "You are a helpful assistant with real-time web search capabilities. "
                                + "When users ask questions that require up-to-date information, "
                                + "use the tavily_search tool to find the latest information. "
                                + "Always cite the sources (URLs) in your answer.")
                .model(
                        DashScopeChatModel.builder()
                                .apiKey(dashScopeApiKey)
                                .modelName("qwen-max")
                                .stream(true)
                                .enableThinking(false)
                                .formatter(new DashScopeChatFormatter())
                                .build())
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .build();
    }

    private static ReActAgent buildFirecrawlAgent(String dashScopeApiKey) throws Exception {
        String firecrawlApiKey =
                ExampleUtils.getApiKey(
                        "FIRECRAWL_API_KEY", "Firecrawl", "https://firecrawl.dev");

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new FirecrawlSearchTools(firecrawlApiKey));

        System.out.println("\nRegistered tools:");
        System.out.println(
                "  - firecrawl_search: Search and scrape the web in real-time via Firecrawl\n");

        return ReActAgent.builder()
                .name("FirecrawlSearchAgent")
                .sysPrompt(
                        "You are a helpful assistant with real-time web search and scraping "
                                + "capabilities. When users ask questions that require up-to-date "
                                + "information, use the firecrawl_search tool to find and extract "
                                + "the latest information. Always cite the sources (URLs) in your "
                                + "answer.")
                .model(
                        DashScopeChatModel.builder()
                                .apiKey(dashScopeApiKey)
                                .modelName("qwen-max")
                                .stream(true)
                                .enableThinking(false)
                                .formatter(new DashScopeChatFormatter())
                                .build())
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .build();
    }

    /**
     * Tavily Search tool that provides real-time web search via the Tavily Search API.
     *
     * @see <a href="https://docs.tavily.com/documentation/api-reference/endpoint/search">Tavily
     *     API</a>
     */
    public static class TavilySearchTools {

        private static final String TAVILY_API_URL = "https://api.tavily.com/search";

        private final String apiKey;
        private final HttpClient httpClient;

        public TavilySearchTools(String apiKey) {
            this.apiKey = apiKey;
            this.httpClient =
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(30))
                            .build();
        }

        @Tool(
                name = "tavily_search",
                description =
                        "Search the web in real-time using Tavily. Returns relevant search"
                            + " results with titles, URLs, and content snippets. Use this for"
                            + " any question that needs current or up-to-date information.")
        public String search(
                @ToolParam(name = "query", description = "The search query to execute")
                        String query,
                @ToolParam(
                                name = "max_results",
                                description =
                                        "Maximum number of results to return (1-20, default 5)",
                                required = false)
                        Integer maxResults,
                @ToolParam(
                                name = "search_depth",
                                description =
                                        "Search depth: 'basic' for quick results, 'advanced' for"
                                            + " more detailed results (default 'basic')",
                                required = false)
                        String searchDepth,
                @ToolParam(
                                name = "topic",
                                description =
                                        "Search category: 'general' or 'news' (default 'general')",
                                required = false)
                        String topic) {

            try {
                if (maxResults == null) {
                    maxResults = 5;
                }
                if (searchDepth == null || searchDepth.isBlank()) {
                    searchDepth = "basic";
                }
                if (topic == null || topic.isBlank()) {
                    topic = "general";
                }

                String requestBody =
                        OBJECT_MAPPER.writeValueAsString(
                                OBJECT_MAPPER
                                        .createObjectNode()
                                        .put("query", query)
                                        .put("max_results", maxResults)
                                        .put("search_depth", searchDepth)
                                        .put("topic", topic)
                                        .put("include_answer", true));

                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(URI.create(TAVILY_API_URL))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer " + apiKey)
                                .timeout(Duration.ofSeconds(30))
                                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                .build();

                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    return "Tavily search failed (HTTP " + response.statusCode() + "): "
                            + response.body();
                }

                return formatTavilyResponse(response.body());

            } catch (Exception e) {
                return "Error calling Tavily search: " + e.getMessage();
            }
        }

        private String formatTavilyResponse(String responseBody) throws Exception {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            StringBuilder sb = new StringBuilder();

            JsonNode answer = root.get("answer");
            if (answer != null && !answer.isNull() && !answer.asText().isBlank()) {
                sb.append("Answer: ").append(answer.asText()).append("\n\n");
            }

            JsonNode results = root.get("results");
            if (results != null && results.isArray()) {
                sb.append("Search Results:\n");
                for (int i = 0; i < results.size(); i++) {
                    JsonNode result = results.get(i);
                    sb.append(i + 1).append(". ");
                    sb.append(getTextOrEmpty(result, "title")).append("\n");
                    sb.append("   URL: ").append(getTextOrEmpty(result, "url")).append("\n");
                    sb.append("   ").append(getTextOrEmpty(result, "content")).append("\n\n");
                }
            }

            return sb.toString().trim();
        }
    }

    /**
     * Firecrawl Search tool that provides real-time web search with scraping via the Firecrawl
     * API.
     *
     * @see <a href="https://docs.firecrawl.dev/api-reference/endpoint/search">Firecrawl API</a>
     */
    public static class FirecrawlSearchTools {

        private static final String FIRECRAWL_API_URL = "https://api.firecrawl.dev/v2/search";

        private final String apiKey;
        private final HttpClient httpClient;

        public FirecrawlSearchTools(String apiKey) {
            this.apiKey = apiKey;
            this.httpClient =
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(30))
                            .build();
        }

        @Tool(
                name = "firecrawl_search",
                description =
                        "Search and scrape the web in real-time using Firecrawl. Returns"
                            + " search results with full page content in markdown format. Use"
                            + " this for questions that need current information or detailed"
                            + " webpage content.")
        public String search(
                @ToolParam(name = "query", description = "The search query to execute")
                        String query,
                @ToolParam(
                                name = "limit",
                                description =
                                        "Maximum number of results to return (1-100, default 5)",
                                required = false)
                        Integer limit,
                @ToolParam(
                                name = "country",
                                description =
                                        "ISO country code for geo-targeted results, e.g. 'US',"
                                            + " 'CN', 'JP' (default 'US')",
                                required = false)
                        String country,
                @ToolParam(
                                name = "tbs",
                                description =
                                        "Time-based search filter: 'qdr:h' (past hour), 'qdr:d'"
                                            + " (past day), 'qdr:w' (past week), 'qdr:m' (past"
                                            + " month), 'qdr:y' (past year)",
                                required = false)
                        String tbs) {

            try {
                if (limit == null) {
                    limit = 5;
                }
                if (country == null || country.isBlank()) {
                    country = "US";
                }

                var requestNode =
                        OBJECT_MAPPER
                                .createObjectNode()
                                .put("query", query)
                                .put("limit", limit)
                                .put("country", country);

                if (tbs != null && !tbs.isBlank()) {
                    requestNode.put("tbs", tbs);
                }

                String requestBody = OBJECT_MAPPER.writeValueAsString(requestNode);

                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(URI.create(FIRECRAWL_API_URL))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer " + apiKey)
                                .timeout(Duration.ofSeconds(60))
                                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                .build();

                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    return "Firecrawl search failed (HTTP " + response.statusCode() + "): "
                            + response.body();
                }

                return formatFirecrawlResponse(response.body());

            } catch (Exception e) {
                return "Error calling Firecrawl search: " + e.getMessage();
            }
        }

        private String formatFirecrawlResponse(String responseBody) throws Exception {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            StringBuilder sb = new StringBuilder();

            JsonNode data = root.get("data");
            if (data == null) {
                return "No search results returned.";
            }

            JsonNode webResults = data.get("web");
            if (webResults != null && webResults.isArray()) {
                sb.append("Search Results:\n");
                for (int i = 0; i < webResults.size(); i++) {
                    JsonNode result = webResults.get(i);
                    sb.append(i + 1).append(". ");
                    sb.append(getTextOrEmpty(result, "title")).append("\n");
                    sb.append("   URL: ").append(getTextOrEmpty(result, "url")).append("\n");

                    String description = getTextOrEmpty(result, "description");
                    if (!description.isEmpty()) {
                        sb.append("   ").append(description).append("\n");
                    }

                    String markdown = getTextOrEmpty(result, "markdown");
                    if (!markdown.isEmpty()) {
                        String truncated =
                                markdown.length() > 500
                                        ? markdown.substring(0, 500) + "..."
                                        : markdown;
                        sb.append("   Content: ").append(truncated).append("\n");
                    }

                    sb.append("\n");
                }
            }

            return sb.toString().trim();
        }
    }

    private static String getTextOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText();
    }
}
