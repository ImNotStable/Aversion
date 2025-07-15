package com.aversion.server.modules;

import com.aversion.server.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Web module for MCP server.
 * <p>
 * Provides tools for fetching and processing web content including
 * single and multiple URL fetching, link extraction, and web page analysis.
 */
public class WebModule extends BaseModule {

  private static final String DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

  private static final int DEFAULT_TIMEOUT = 10000;
  private static final int MAX_CONTENT_LENGTH = 50000;
  private static final int MAX_URLS = 10;
  private static final int MAX_LINKS = 500;

  private final OkHttpClient httpClient;

  public WebModule() {
    this.httpClient = new OkHttpClient.Builder()
      .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
      .readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
      .followRedirects(true)
      .build();
  }

  @Override
  public ModuleConfig getConfig() {
    return new ModuleConfig(
      "web-module",
      "1.0.0",
      "Web scraping and URL content fetching tools"
    );
  }


  // Tool handlers

  /**
   * Handles the "fetch_url" tool call. Fetches and extracts content from a single web URL.
   *
   * @param args JsonNode containing the tool arguments:
   *             - "url" (String): The URL to fetch.
   *             - "options" (Object, optional): An object containing fetch options:
   *             - "timeout" (Integer, optional): Connection and read timeout in milliseconds. Default is 10000.
   *             - "userAgent" (String, optional): User-Agent header to send. Default is a common browser user agent.
   *             - "followRedirects" (Boolean, optional): Whether to follow HTTP redirects. Default is true.
   *             - "includeHeaders" (Boolean, optional): Whether to include response headers in the output. Default is false.
   *             - "textOnly" (Boolean, optional): Whether to strip HTML tags and return only text content. Default is true.
   *             - "maxLength" (Integer, optional): Maximum length of the content to return. Default is 50000.
   * @return A Map containing the fetched web content as a text response.
   * @throws Exception if the URL cannot be fetched or processed.
   */
  @com.aversion.server.tools.ToolDefinition(name = "fetch_url", description = "Fetch and extract content from a single web URL with comprehensive options")
  private Map<String, Object> handleFetchUrl(JsonNode args) throws Exception {
    String url = JsonUtil.getStringField(args, "url");
    JsonNode optionsNode = args.get("options");

    FetchOptions options = parseFetchOptions(optionsNode);
    String content = fetchWebContent(url, options);

    return createTextResponse(content);
  }

  @com.aversion.server.tools.ToolDefinition(name = "fetch_multiple_urls", description = "Fetch content from multiple URLs concurrently with aggregated results")
  private Map<String, Object> handleFetchMultipleUrls(JsonNode args) {
    JsonNode urlsNode = JsonUtil.getArrayField(args, "urls");
    JsonNode optionsNode = args.get("options");

    if (urlsNode.size() > MAX_URLS) {
      throw new IllegalArgumentException("Cannot fetch more than " + MAX_URLS + " URLs at once");
    }

    List<String> urls = new ArrayList<>();
    urlsNode.forEach(node -> urls.add(node.asText()));

    MultiFetchOptions options = parseMultiFetchOptions(optionsNode);
    String results = fetchMultipleUrls(urls, options);

    return createTextResponse(results);
  }

  @com.aversion.server.tools.ToolDefinition(name = "extract_links", description = "Extract and filter links from web pages with advanced filtering options")
  private Map<String, Object> handleExtractLinks(JsonNode args) throws Exception {
    String url = JsonUtil.getStringField(args, "url");
    JsonNode optionsNode = args.get("options");

    LinkExtractionOptions options = parseLinkExtractionOptions(optionsNode);
    String links = extractLinksFromPage(url, options);

    return createTextResponse(links);
  }

  @com.aversion.server.tools.ToolDefinition(name = "analyze_webpage", description = "Comprehensive web page analysis including metadata, structure, and performance")
  private Map<String, Object> handleAnalyzeWebPage(JsonNode args) throws Exception {
    String url = JsonUtil.getStringField(args, "url");
    JsonNode analysisNode = args.get("analysis");

    PageAnalysisOptions options = parsePageAnalysisOptions(analysisNode);
    String analysis = analyzeWebPage(url, options);

    return createTextResponse(analysis);
  }

  // Core fetching methods

  private String fetchWebContent(String urlStr, FetchOptions options) throws IOException {
    Request request = new Request.Builder()
      .url(urlStr)
      .header("User-Agent", options.userAgent())
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("HTTP " + response.code() + ": " + response.message());
      }

      String contentType = response.header("content-type", "");

      //noinspection DataFlowIssue
      if (!isSupportedContentType(contentType))
        throw new IOException("Unsupported content type: " + contentType);

      String content = response.body().string();

      if (options.textOnly() && contentType.contains("text/html")) {
        content = stripHtml(content);
      }

      if (content.length() > options.maxLength()) {
        content = content.substring(0, options.maxLength()) + "\n\n[Content truncated...]";
      }

      return formatFetchResult(urlStr, response, content, options.includeHeaders());
    }
  }

  private String fetchMultipleUrls(List<String> urls, MultiFetchOptions options) {
    List<CompletableFuture<FetchResult>> futures = urls.stream()
      .map(url -> CompletableFuture.supplyAsync(() -> {
        try {
          FetchOptions fetchOpts = new FetchOptions(
            options.timeout(),
            DEFAULT_USER_AGENT,
            true,
            false,
            options.textOnly(),
            options.maxLength()
          );
          String content = fetchWebContent(url, fetchOpts);
          return new FetchResult(url, true, content, null);
        } catch (Exception e) {
          return new FetchResult(url, false, null, e.getMessage());
        }
      }))
      .toList();

    List<FetchResult> results = futures.stream()
      .map(CompletableFuture::join)
      .collect(Collectors.toList());

    return formatMultiFetchResults(results, options.includeFailures());
  }

  private String extractLinksFromPage(String url, LinkExtractionOptions options) throws IOException, URISyntaxException {
    Document doc = Jsoup.connect(url)
      .userAgent(DEFAULT_USER_AGENT)
      .timeout(DEFAULT_TIMEOUT)
      .get();

    Elements linkElements = doc.select("a[href]");
    List<LinkInfo> links = new ArrayList<>();

    String baseHostname = new URI(url).getHost();

    for (Element link : linkElements) {
      if (links.size() >= options.maxLinks()) break;

      String href = link.attr("abs:href");
      String text = link.text().trim();

      if (href.isEmpty()) continue;

      LinkInfo linkInfo = new LinkInfo(href, text.isEmpty() ? "[No text]" : text);

      // Apply filtering
      if (shouldIncludeLink(linkInfo, options.filter(), baseHostname))
        links.add(linkInfo);
    }

    if (options.unique()) {
      links = new ArrayList<>(links.stream()
        .collect(Collectors.toMap(
          LinkInfo::url,
          link -> link,
          (existing, replacement) -> existing,
          LinkedHashMap::new
        ))
        .values());
    }

    return formatLinksResult(url, links, options.filter(), options.includeText());
  }

  private String analyzeWebPage(String url, PageAnalysisOptions options) throws IOException {
    long startTime = System.currentTimeMillis();

    Document doc = Jsoup.connect(url)
      .userAgent(DEFAULT_USER_AGENT)
      .timeout(DEFAULT_TIMEOUT)
      .get();

    long loadTime = System.currentTimeMillis() - startTime;

    StringBuilder result = new StringBuilder();
    result.append("Web Page Analysis: ").append(url).append("\n\n");

    if (options.metadata()) {
      result.append(extractMetadata(doc));
    }

    if (options.structure()) {
      result.append(analyzeStructure(doc));
    }

    if (options.images()) {
      result.append(extractImages(doc, url));
    }

    if (options.performance()) {
      result.append("Performance Metrics:\n");
      result.append("- Load time: ").append(loadTime).append("ms\n");
      result.append("- Content size: ").append(doc.html().length()).append(" characters\n\n");
    }

    return result.toString();
  }

  // Content processing methods

  private String stripHtml(String html) {
    Document doc = Jsoup.parse(html);
    doc.select("script, style").remove();
    return doc.text();
  }

  private boolean isSupportedContentType(String contentType) {
    return contentType.contains("text/html") ||
      contentType.contains("text/plain") ||
      contentType.contains("application/json");
  }

  private String formatFetchResult(String url, Response response, String content, boolean includeHeaders) {
    StringBuilder result = new StringBuilder();
    result.append("URL: ").append(url).append("\n");
    result.append("Status: ").append(response.code()).append(" ").append(response.message()).append("\n");
    result.append("Content-Type: ").append(response.header("content-type", "")).append("\n");
    result.append("Content Length: ").append(content.length()).append(" characters\n\n");

    if (includeHeaders) {
      result.append("Response Headers:\n");
      response.headers().forEach(pair -> result.append(pair.getFirst()).append(": ").append(pair.getSecond()).append("\n"));
      result.append("\n");
    }

    result.append("Content:\n").append(content);
    return result.toString();
  }

  private String formatMultiFetchResults(List<FetchResult> results, boolean includeFailures) {
    StringBuilder output = new StringBuilder();
    output.append("Fetched ").append(results.size()).append(" URLs:\n\n");

    int index = 1;
    for (FetchResult result : results) {
      if (result.success()) {
        output.append("=== URL ").append(index).append(": ").append(result.url()).append(" ===\n");
        output.append(result.content()).append("\n\n");
      } else if (includeFailures) {
        output.append("=== URL ").append(index).append(": ").append(result.url()).append(" (FAILED) ===\n");
        output.append("Error: ").append(result.error()).append("\n\n");
      }
      index++;
    }

    long successCount = results.stream().mapToLong(r -> r.success() ? 1 : 0).sum();
    output.append("Summary: ").append(successCount).append("/").append(results.size()).append(" URLs fetched successfully");

    return output.toString();
  }

  private boolean shouldIncludeLink(LinkInfo link, String filter, String baseHostname) {
    if ("all".equals(filter)) return true;

    try {
      URI linkUrl = new URI(link.url());
      boolean isInternal = baseHostname.equals(linkUrl.getHost());
      return "internal".equals(filter) == isInternal;
    } catch (Exception e) {
      return false;
    }
  }

  private String formatLinksResult(String url, List<LinkInfo> links, String filter, boolean includeText) {
    StringBuilder result = new StringBuilder();
    result.append("Links extracted from: ").append(url).append("\n");
    result.append("Total links found: ").append(links.size()).append("\n");
    result.append("Filter applied: ").append(filter).append("\n\n");

    for (int i = 0; i < links.size(); i++) {
      LinkInfo link = links.get(i);
      result.append(i + 1).append(". ").append(link.url());
      if (includeText && !"[No text]".equals(link.text())) {
        result.append(" - \"").append(link.text()).append("\"");
      }
      result.append("\n");
    }

    return result.toString();
  }

  // Analysis methods

  private String extractMetadata(Document doc) {
    StringBuilder result = new StringBuilder("Metadata:\n");

    String title = doc.title();
    if (!title.isEmpty()) {
      result.append("- Title: ").append(title).append("\n");
    }

    Element description = doc.selectFirst("meta[name=description]");
    if (description != null) {
      result.append("- Description: ").append(description.attr("content")).append("\n");
    }

    Element keywords = doc.selectFirst("meta[name=keywords]");
    if (keywords != null) {
      result.append("- Keywords: ").append(keywords.attr("content")).append("\n");
    }

    result.append("\n");
    return result.toString();
  }

  private String analyzeStructure(Document doc) {
    StringBuilder result = new StringBuilder("Page Structure:\n");

    Elements h1s = doc.select("h1");
    Elements h2s = doc.select("h2");
    Elements h3s = doc.select("h3");
    Elements paragraphs = doc.select("p");
    Elements links = doc.select("a[href]");

    result.append("- H1 headings: ").append(h1s.size()).append("\n");
    result.append("- H2 headings: ").append(h2s.size()).append("\n");
    result.append("- H3 headings: ").append(h3s.size()).append("\n");

    if (!h1s.isEmpty())
      //noinspection DataFlowIssue
      result.append("- Main heading text: \"").append(h1s.first().text()).append("\"\n");

    if (!h2s.isEmpty()) {
      result.append("- H2 headings text:\n");
      for (int i = 0; i < Math.min(h2s.size(), 5); i++) {
        result.append("  ").append(i + 1).append(". \"").append(h2s.get(i).text()).append("\"\n");
      }
    }

    result.append("- Paragraphs: ").append(paragraphs.size()).append("\n");
    result.append("- Links: ").append(links.size()).append("\n");
    result.append("\n");

    return result.toString();
  }

  private String extractImages(Document doc, String baseUrl) {
    StringBuilder result = new StringBuilder("Images:\n");
    Elements images = doc.select("img[src]");

    int count = 0;
    for (Element img : images) {
      if (count >= 20) break; // Limit to 20 images

      String src = img.absUrl("src");
      String alt = img.attr("alt");
      if (alt.isEmpty()) alt = "[No alt text]";

      result.append(count + 1).append(". ").append(src).append(" - \"").append(alt).append("\"\n");
      count++;
    }

    result.append("\nTotal images found: ").append(count).append("\n\n");
    return result.toString();
  }

  // Utility methods and records

  private FetchOptions parseFetchOptions(JsonNode optionsNode) {
    if (optionsNode == null || optionsNode.isNull()) {
      return new FetchOptions(DEFAULT_TIMEOUT, DEFAULT_USER_AGENT, true, false, true, MAX_CONTENT_LENGTH);
    }

    return new FetchOptions(
      optionsNode.path("timeout").asInt(DEFAULT_TIMEOUT),
      optionsNode.path("userAgent").asText(DEFAULT_USER_AGENT),
      optionsNode.path("followRedirects").asBoolean(true),
      optionsNode.path("includeHeaders").asBoolean(false),
      optionsNode.path("textOnly").asBoolean(true),
      optionsNode.path("maxLength").asInt(MAX_CONTENT_LENGTH)
    );
  }

  private MultiFetchOptions parseMultiFetchOptions(JsonNode optionsNode) {
    if (optionsNode == null || optionsNode.isNull()) {
      return new MultiFetchOptions(DEFAULT_TIMEOUT, true, 10000, false);
    }

    return new MultiFetchOptions(
      optionsNode.path("timeout").asInt(DEFAULT_TIMEOUT),
      optionsNode.path("textOnly").asBoolean(true),
      optionsNode.path("maxLength").asInt(10000),
      optionsNode.path("includeFailures").asBoolean(false)
    );
  }

  private LinkExtractionOptions parseLinkExtractionOptions(JsonNode optionsNode) {
    if (optionsNode == null || optionsNode.isNull()) {
      return new LinkExtractionOptions("all", true, true, 100);
    }

    return new LinkExtractionOptions(
      optionsNode.path("filter").asText("all"),
      optionsNode.path("includeText").asBoolean(true),
      optionsNode.path("unique").asBoolean(true),
      optionsNode.path("maxLinks").asInt(100)
    );
  }

  private PageAnalysisOptions parsePageAnalysisOptions(JsonNode analysisNode) {
    if (analysisNode == null || analysisNode.isNull()) {
      return new PageAnalysisOptions(true, true, false, false);
    }

    return new PageAnalysisOptions(
      analysisNode.path("metadata").asBoolean(true),
      analysisNode.path("structure").asBoolean(true),
      analysisNode.path("images").asBoolean(false),
      analysisNode.path("performance").asBoolean(false)
    );
  }

  // Records for configuration and results

  record FetchOptions(
    int timeout,
    String userAgent,
    boolean followRedirects,
    boolean includeHeaders,
    boolean textOnly,
    int maxLength
  ) {
  }

  record MultiFetchOptions(
    int timeout,
    boolean textOnly,
    int maxLength,
    boolean includeFailures
  ) {
  }

  record LinkExtractionOptions(
    String filter,
    boolean includeText,
    boolean unique,
    int maxLinks
  ) {
  }

  record PageAnalysisOptions(
    boolean metadata,
    boolean structure,
    boolean images,
    boolean performance
  ) {
  }

  record FetchResult(
    String url,
    boolean success,
    String content,
    String error
  ) {
  }

  record LinkInfo(
    String url,
    String text
  ) {
  }

}
