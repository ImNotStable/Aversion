package com.aversion.server.modules.web;

import com.aversion.server.modules.BaseModule;
import com.aversion.server.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A module for web-related tools, providing functionalities like fetching webpage content and searching the web.
 */
public class WebModule extends BaseModule {

  private static final String MODULE_NAME = "web-module";
  private static final String MODULE_VERSION = "1.0.0";
  private static final String MODULE_DESCRIPTION = "A module for web-related tools.";

  private final OkHttpClient httpClient;

  public WebModule() {
    this.httpClient = new OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .writeTimeout(10, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build();
  }

  @Override
  public ModuleConfig getConfig() {
    return new ModuleConfig(MODULE_NAME, MODULE_VERSION, MODULE_DESCRIPTION);
  }

  @com.aversion.server.tools.ToolDefinition(name = "get_webpage_content", description = "Fetches the content of a given URL.")
  private Map<String, Object> handleGetWebpageContent(JsonNode args) throws IOException {
    String url = JsonUtil.getStringField(args, "url");

    Request request = new Request.Builder()
      .url(url)
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Failed to fetch content: " + response.code() + " " + response.message());
      }
      String content = response.body().string();
      return createTextResponse(content);
    }
  }

  @com.aversion.server.tools.ToolDefinition(name = "search_web", description = "Searches the web for a given query using DuckDuckGo Lite.")
  private Map<String, Object> handleSearchWeb(JsonNode args) throws IOException {
    String query = JsonUtil.getStringField(args, "query");
    String searchUrl = "https://lite.duckduckgo.com/lite/?q=" + query;

    Request request = new Request.Builder()
      .url(searchUrl)
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Failed to perform web search: " + response.code() + " " + response.message());
      }
      String htmlContent = response.body().string();

      // Parse HTML using Jsoup
      org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(htmlContent);
      org.jsoup.select.Elements results = doc.select(".result-link"); // Adjust selector based on DuckDuckGo Lite HTML structure

      java.util.List<java.util.Map<String, String>> searchResults = new java.util.ArrayList<>();
      for (org.jsoup.nodes.Element result : results) {
        String title = result.text();
        String url = result.attr("href");
        // DuckDuckGo Lite doesn't provide snippets directly in the result-link element, 
        // so we might need to look for a sibling element or fetch the page content.
        // For simplicity, I'll just include title and URL for now.
        searchResults.add(java.util.Map.of("title", title, "url", url));
      }

      return createTextResponse(JsonUtil.formatJson(searchResults));
    }
  }
}