import java.io.*;
import java.nio.file.*;

public class Extract {
    private static final int bufferSize = 1 << 16;
    public static void extract(String spacePath, String outputDir) {
        String name = Paths.get(spacePath).getFileName().toString();
        if (name.endsWith(".space")) name = name.substring(0, name.length() - 6);
        Path out = Paths.get(outputDir, name + "_extracted.txt");
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        try {
            Files.createDirectories(out.getParent());
            try (DataInputStream in = new DataInputStream(
                     new BufferedInputStream(Files.newInputStream(Paths.get(spacePath)), bufferSize));
                 OutputStream os = new BufferedOutputStream(Files.newOutputStream(tmp), bufferSize)) {
                int dictSize = (int) get(in);
                byte[][] dict = new byte[dictSize][];
                for (int i = 0; i < dictSize; i++) {
                    dict[i] = new byte[(int) get(in)];
                    in.readFully(dict[i]);
                }
                long tokenCount = get(in);
                for (long t = 0; t < tokenCount; t++) {
                    long idx = get(in);
                    if (idx >= dictSize) throw new IllegalStateException("corrupted .space file");
                    os.write(dict[(int) idx]);
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
    private static long get(DataInputStream in) throws IOException {
        int first = in.readUnsignedByte();
        if (first == 0) throw new IllegalStateException("corrupted .space file");
        int n = Integer.numberOfLeadingZeros(first) - 24 + 1;
        long value = first & ((1 << (8 - n)) - 1);
        for (int i = 1; i < n; i++) value = (value << 8) | in.readUnsignedByte();
        for (int k = 1; k < n; k++) value += (1L << (7 * k));
        return value;
    }
}