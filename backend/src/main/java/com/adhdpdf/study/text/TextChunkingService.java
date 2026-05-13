package com.adhdpdf.study.text;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class TextChunkingService {

    private final int defaultMaxCharsPerChunk;

    public TextChunkingService(@Value("${study.chunk.max-chars:5000}") int defaultMaxCharsPerChunk) {
        this.defaultMaxCharsPerChunk = defaultMaxCharsPerChunk;
    }

    /**
     * Splits text using {@link #defaultMaxCharsPerChunk} from configuration.
     */
    public List<TextChunk> splitIntoChunks(String text) {
        return splitIntoChunks(text, defaultMaxCharsPerChunk);
    }

    /**
     * Splits plain text into ordered chunks up to {@code maxCharsPerChunk} characters each.
     * <p>
     * Paragraphs (blocks separated by blank lines) are kept together when they fit; oversized
     * paragraphs are broken on whitespace where possible, otherwise hard-split (e.g. very long words).
     */
    public List<TextChunk> splitIntoChunks(String text, int maxCharsPerChunk) {
        if (maxCharsPerChunk < 1) {
            throw new IllegalArgumentException("maxCharsPerChunk must be at least 1");
        }
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalized = text.strip();
        List<String> paragraphs = Arrays.stream(normalized.split("\\R{2,}"))
                .map(String::strip)
                .filter(StringUtils::hasText)
                .toList();
        if (paragraphs.isEmpty()) {
            return List.of();
        }

        List<String> packed = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            List<String> pieces = paragraph.length() <= maxCharsPerChunk
                    ? List.of(paragraph)
                    : splitOversizedSegment(paragraph, maxCharsPerChunk);

            for (String piece : pieces) {
                if (current.isEmpty()) {
                    current.append(piece);
                    continue;
                }
                String separator = "\n\n";
                if (current.length() + separator.length() + piece.length() <= maxCharsPerChunk) {
                    current.append(separator).append(piece);
                } else {
                    packed.add(current.toString());
                    current.setLength(0);
                    current.append(piece);
                }
            }
        }
        if (!current.isEmpty()) {
            packed.add(current.toString());
        }

        List<TextChunk> chunks = new ArrayList<>(packed.size());
        for (int i = 0; i < packed.size(); i++) {
            chunks.add(new TextChunk(i, packed.get(i)));
        }
        return List.copyOf(chunks);
    }

    /**
     * Breaks a segment longer than {@code maxChars} into parts ≤ {@code maxChars}, preferring
     * breaks at whitespace (spaces, newlines) so words stay intact when possible.
     */
    private List<String> splitOversizedSegment(String segment, int maxChars) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        int len = segment.length();
        while (i < len) {
            int remaining = len - i;
            if (remaining <= maxChars) {
                parts.add(segment.substring(i).strip());
                break;
            }
            int hardEnd = i + maxChars;
            int breakPos = -1;
            for (int j = hardEnd - 1; j > i; j--) {
                if (Character.isWhitespace(segment.charAt(j))) {
                    breakPos = j;
                    break;
                }
            }
            int splitAt = breakPos > i ? breakPos : hardEnd;
            parts.add(segment.substring(i, splitAt).strip());
            i = splitAt;
            while (i < len && Character.isWhitespace(segment.charAt(i))) {
                i++;
            }
        }
        return parts.stream().filter(StringUtils::hasText).toList();
    }
}
