package com.ezcloud.service;

import com.ezcloud.repository.DocumentRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

/**
 * Function-calling tools exposed to the LLM. Registered per-request in
 * ChatService via .tools(agentTools). Each @Tool description tells the model
 * when to call it.
 */
@Component
public class AgentTools {

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;

    public AgentTools(VectorStore vectorStore, DocumentRepository documentRepository) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
    }

    @Tool(description = "Evaluate an arithmetic expression. Call this for any calculation instead of "
            + "computing mentally. Supports +, -, *, /, parentheses, and decimal numbers. "
            + "Example input: (12.5 * 4) - 3")
    public double calculator(String expression) {
        return new ExpressionParser(expression).parse();
    }

    @Tool(description = "Get the current date and time in ISO-8601 format with offset. Call this "
            + "whenever the answer depends on today's date or the current time.")
    public String currentDateTime() {
        return OffsetDateTime.now().toString();
    }

    @Tool(description = "Count how many documents are in the knowledge base. Call this when the user "
            + "asks about the size or contents of the document library.")
    public long documentCount() {
        return documentRepository.count();
    }

    @Tool(description = "Search the knowledge base for passages relevant to a query. Call this when "
            + "you need additional supporting material beyond what is already in the conversation "
            + "context. Returns up to 4 passages with their source filenames.")
    public String searchKnowledgeBase(String query) {
        var results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(4)
                .similarityThreshold(0.4)
                .build());
        return Optional.ofNullable(results)
                .filter(not(List::isEmpty))
                .map(docs -> docs.stream()
                        .map(doc -> "[" + doc.getMetadata().getOrDefault("filename", "unknown") + "] " + doc.getText())
                        .collect(Collectors.joining("\n---\n")))
                .orElse("No relevant passages found.");
    }

    /** Minimal recursive-descent parser: expression = term (('+'|'-') term)*, etc. */
    public static final class ExpressionParser {

        private final String input;
        private int pos;

        public ExpressionParser(String input) {
            this.input = Objects.requireNonNullElse(input, "");
        }

        public double parse() {
            double value = parseExpression();
            skipWhitespace();
            if (pos < input.length()) {
                throw new IllegalArgumentException("Unexpected character at position " + pos);
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipWhitespace();
                if (consume('+')) {
                    value += parseTerm();
                } else if (consume('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipWhitespace();
                if (consume('*')) {
                    value *= parseFactor();
                } else if (consume('/')) {
                    value /= parseFactor();
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            skipWhitespace();
            if (consume('(')) {
                double value = parseExpression();
                skipWhitespace();
                if (!consume(')')) {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                return value;
            }
            if (consume('-')) {
                return -parseFactor();
            }
            int start = pos;
            while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("Expected a number at position " + pos);
            }
            return Double.parseDouble(input.substring(start, pos));
        }

        private boolean consume(char expected) {
            if (pos < input.length() && input.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }
    }
}
