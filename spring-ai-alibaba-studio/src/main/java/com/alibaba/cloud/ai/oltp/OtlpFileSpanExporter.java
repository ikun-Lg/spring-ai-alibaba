/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.alibaba.cloud.ai.oltp;

import com.alibaba.cloud.ai.utils.JsonUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.opentelemetry.exporter.internal.otlp.traces.ResourceSpansMarshaler;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link SpanExporter} which writes {@linkplain SpanData spans} to a {@link Logger} in
 * OTLP JSON format. Each log line will include a single {@code ResourceSpans}.
 */
public final class OtlpFileSpanExporter implements SpanExporter {

	private static final Logger logger = Logger.getLogger(OtlpFileSpanExporter.class.getName());

	private final AtomicBoolean isShutdown = new AtomicBoolean();

	private final String outputFile = "spring-ai-alibaba-studio/spans.json";

	private final Path outputPath = Paths.get(outputFile);

	private final ObjectMapper objectMapper;

	private static final String LINE_SEPARATOR = System.lineSeparator();


	/** Returns a new {@link OtlpFileSpanExporter}. */
	public static SpanExporter create() {
		return new OtlpFileSpanExporter();
	}

	private OtlpFileSpanExporter() {
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public CompletableResultCode export(Collection<SpanData> spans) {
		if (isShutdown.get()) {
			return CompletableResultCode.ofFailure();
		}

		ResourceSpansMarshaler[] allResourceSpans = ResourceSpansMarshaler.create(spans);

		try {
			if (!Files.exists(outputPath)) {
				Files.createFile(outputPath);
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Failed to create file.", outputPath);
		}

		try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardOpenOption.APPEND)) {
			StringBuilder sb = new StringBuilder();
			for (ResourceSpansMarshaler resourceSpans : allResourceSpans) {
				SegmentedStringWriter sw = new SegmentedStringWriter(JsonUtil.JSON_FACTORY._getBufferRecycler());

				try (JsonGenerator gen = JsonUtil.create(sw)) {
					resourceSpans.writeJsonTo(gen);
				} catch (IOException e) {
					logger.log(Level.WARNING, "Error generating OTLP JSON spans", e);
					continue;
				}

				String json = sw.getAndClear();
				sb.append(json).append(LINE_SEPARATOR);

				if (sb.length() > 1024 * 1024) {
					writer.write(sb.toString());
					sb.setLength(0);
				}
			}

			if (!sb.isEmpty()) {
				writer.write(sb.toString());
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Error opening output file for writing", e);
			return CompletableResultCode.ofFailure();
		}

		return CompletableResultCode.ofSuccess();
	}


	@Override
	public CompletableResultCode flush() {
		return CompletableResultCode.ofSuccess();
	}

	@Override
	public CompletableResultCode shutdown() {
		if (!isShutdown.compareAndSet(false, true)) {
			logger.log(Level.INFO, "Calling shutdown() multiple times.");
		}
		return CompletableResultCode.ofSuccess();
	}

	/**
	 * Reads and returns all JSON content from the output file as a JsonArray. Assumes
	 * that the file contains multiple JSON objects, one per line.
	 * @return ArrayNode containing all JSON entries in the file.
	 */
	public ArrayNode readJsonFromFile() {
		ArrayNode jsonArray = objectMapper.createArrayNode();

		if (!outputPath.toFile().isFile()) {
			logger.log(Level.WARNING, "Invalid file path: " + outputFile);
			return jsonArray;
		}

		try {
			for (String line : Files.readAllLines(outputPath)) {
				try {
					JsonNode jsonNode = objectMapper.readTree(line);
					jsonArray.add(jsonNode);
				}
				catch (IOException e) {
					logger.log(Level.WARNING, "Invalid JSON entry in file: " + line, e);
				}
			}
		} catch (IOException e) {
			logger.log(Level.WARNING, "Error reading JSON from file", e);
		}

		return jsonArray;
	}

	/**
	 * Retrieves a JsonNode by traceId from the JSON data in the file.
	 * @param traceId the traceId to search for.
	 * @return ArrayNode containing the matching span or an empty ArrayNode if not found.
	 */
	public JsonNode getJsonNodeByTraceId(String traceId) {
		ArrayNode resultArray = objectMapper.createArrayNode();
		ArrayNode jsonArray = readJsonFromFile();

		for (JsonNode jsonNode : jsonArray) {
			JsonNode scopeSpans = jsonNode.path("scopeSpans");
			for (JsonNode scopeSpan : scopeSpans) {
				JsonNode spans = scopeSpan.path("spans");
				for (JsonNode span : spans) {
					if (span.path("traceId").asText().equals(traceId)) {
						return jsonNode;
					}
				}
			}
		}

		return resultArray;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ListResponse(@JsonProperty("traceId") String traceId,
			@JsonProperty("startTimeUnixNano") String startTimeUnixNano,
			@JsonProperty("endTimeUnixNano") String endTimeUnixNano) {
	}

	public List<ListResponse> extractSpansWithoutParentSpanId() {
		List<ListResponse> spanDataList = new ArrayList<>();
		ArrayNode jsonArray = readJsonFromFile();

		for (JsonNode jsonNode : jsonArray) {
			JsonNode scopeSpansNode = jsonNode.path("scopeSpans");

			// Flag to track if we've found a span without parentSpanId
			boolean found = false;

			for (JsonNode scopeSpan : scopeSpansNode) {
				JsonNode spansNode = scopeSpan.path("spans");

				// Skip further processing if already found a valid span
				if (found)
					break;

				for (JsonNode span : spansNode) {
					// Check if the span has no parentSpanId
					if (!span.has("parentSpanId")) {
						String traceId = span.path("traceId").asText();
						String startTimeUnixNano = span.path("startTimeUnixNano").asText();
						String endTimeUnixNano = span.path("endTimeUnixNano").asText();

						ListResponse spanData = new ListResponse(traceId, startTimeUnixNano, endTimeUnixNano);
						spanDataList.add(spanData);

						// Mark that we found a valid span and stop further searches
						found = true;
						break;
					}
				}
			}
		}

		// Return the list of spans without parentSpanId
		return spanDataList;
	}

	public String clearExportContent() {
		try {
			Files.deleteIfExists(outputPath);
			logger.log(Level.INFO, "File content cleared.");
			return "File content cleared successfully.";
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Error clearing the file content", e);
			return "Error clearing the file content: " + e.getMessage();
		}
	}

}
