import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Compress {
    private static final int bufferSize = 1 << 16;
    private static final int maxToken = 1 << 16;
    private static final String[] single = new String[256];
    static {
        for (int i = 0; i < 256; i++) {
            single[i] = new String(new byte[] {(byte) i}, StandardCharsets.ISO_8859_1);
        }
    }
    private interface TokenSink {
        void accept(String token) throws IOException;
    }
    private static final class Entry {
        final String word;
        long count;
        int rank;

        Entry(String word) {
            this.word = word;
        }
    }

    public static void compress(String filepath, String outputDir) {
        Path input = Paths.get(filepath);
        String name = input.getFileName().toString().replace('.', '_') + ".space";
        Path out = Paths.get(outputDir, name);
        Path tmp = out.resolveSibling(name + ".tmp");

        try {
            Files.createDirectories(out.getParent());
            Map<String, Entry> table = new HashMap<>();
            long tokenCount = tokenize(input, t -> table.computeIfAbsent(t, Entry::new).count++);
            List<Entry> dict = new ArrayList<>(table.values());
            dict.sort((a, b) -> {
                int c = Long.compare(b.count * b.word.length(), a.count * a.word.length());
                return c != 0 ? c : a.word.compareTo(b.word);
            });
            for (int i = 0; i < dict.size(); i++) dict.get(i).rank = i;
            try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(tmp), bufferSize)) {
                put(os, dict.size());
                for (Entry e : dict) {
                    byte[] b = e.word.getBytes(StandardCharsets.ISO_8859_1);
                    put(os, b.length);
                    os.write(b, 0, b.length);
                }
                put(os, tokenCount);
                long written = tokenize(input, t -> {
                    Entry e = table.get(t);
                    if (e == null) throw new IllegalStateException("input changed during compression");
                    put(os, e.rank);
                });
                if (written != tokenCount) {
                    throw new IllegalStateException("input changed during compression");
                }
            }

            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }
    private static long tokenize(Path file, TokenSink sink) throws IOException {
        byte[] chunk = new byte[bufferSize];
        byte[] word = new byte[maxToken];
        int wordLen = 0;
        long tokens = 0;
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(chunk)) != -1) {
                for (int i = 0; i < read; i++) {
                    byte b = chunk[i];
                    if (isSpace(b)) {
                        if (wordLen > 0) {
                            sink.accept(toToken(word, wordLen));
                            tokens++;
                            wordLen = 0;
                        }
                        sink.accept(single[b & 0xFF]);
                        tokens++;
                    } else {
                        word[wordLen++] = b;
                        if (wordLen == maxToken) {
                            sink.accept(toToken(word, wordLen));
                            tokens++;
                            wordLen = 0;
                        }
                    }
                }
            }
        }
        if (wordLen > 0) {
            sink.accept(toToken(word, wordLen));
            tokens++;
        }
        return tokens;
    }
    private static boolean isSpace(byte b) {
        return b == ' ' || (b >= 9 && b <= 13);
    }
    private static String toToken(byte[] buf, int len) {
        return len == 1 ? single[buf[0] & 0xFF] : new String(buf, 0, len, StandardCharsets.ISO_8859_1);
    }
    // n=1: 1xxxxxxx ; n=2: 01xxxxxx xxxxxxxx ; n=3: 001xxxxx xxxxxxxx xxxxxxxx ...
    private static void put(OutputStream os, long value) throws IOException {
        long offset = value;
        int n = 1;
        while (offset >= (1L << (7 * n))) {
            offset -= (1L << (7 * n));
            n++;
        }
        long code = (1L << (7 * n)) | offset;
        for (int i = n - 1; i >= 0; i--) os.write((int) (code >>> (8 * i)) & 0xFF);
    }
}