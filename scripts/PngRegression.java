import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;

/** Deterministic pixel and luminance regression gate with dependency-free PNG decoding. */
public final class PngRegression {
    private static final double MAX_MEAN_CHANNEL_ERROR = 1.5;
    private static final double MAX_CHANGED_PIXEL_RATIO = 0.008;
    private static final double MAX_MEAN_LUMINANCE_ERROR = 1.0;
    private static final int MATERIAL_PIXEL_DELTA = 12;

    private PngRegression() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("usage: PngRegression GOLDENS ACTUAL DIFFS REPORT");
        }
        Path goldenDirectory = Path.of(args[0]);
        Path actualDirectory = Path.of(args[1]);
        Path diffDirectory = Path.of(args[2]);
        Path report = Path.of(args[3]);
        Files.createDirectories(diffDirectory);

        List<Path> goldens = pngs(goldenDirectory);
        if (goldens.isEmpty()) {
            throw new IllegalStateException("No screenshot goldens found in " + goldenDirectory);
        }
        Set<String> goldenNames = names(goldens);
        Set<String> actualNames = names(pngs(actualDirectory));
        Set<String> missing = new HashSet<>(goldenNames);
        missing.removeAll(actualNames);
        Set<String> unexpected = new HashSet<>(actualNames);
        unexpected.removeAll(goldenNames);

        boolean failed = !missing.isEmpty() || !unexpected.isEmpty();
        StringBuilder json = new StringBuilder("{\n  \"screenshots\": [\n");
        boolean first = true;
        for (Path golden : goldens) {
            String name = golden.getFileName().toString();
            Path actual = actualDirectory.resolve(name);
            if (!Files.isRegularFile(actual)) {
                continue;
            }
            Result result = compare(golden, actual, diffDirectory.resolve(name));
            failed |= !result.passed();
            if (!first) {
                json.append(",\n");
            }
            first = false;
            json.append(result.toJson(name));
            System.out.printf(
                    Locale.ROOT,
                    "%s: mean=%.4f changed=%.5f luma=%.4f passed=%s%n",
                    name,
                    result.meanChannelError(),
                    result.changedPixelRatio(),
                    result.meanLuminanceError(),
                    result.passed());
        }
        json.append("\n  ],\n  \"missing\": ")
                .append(stringArray(missing))
                .append(",\n  \"unexpected\": ")
                .append(stringArray(unexpected))
                .append("\n}\n");
        Files.writeString(report, json.toString(), StandardCharsets.UTF_8);
        if (failed) {
            throw new AssertionError("Screenshot regression detected; inspect " + report + " and " + diffDirectory);
        }
    }

    private static Result compare(Path goldenPath, Path actualPath, Path diffPath) throws IOException {
        BufferedImage golden = requireImage(goldenPath);
        BufferedImage actual = requireImage(actualPath);
        if (golden.getWidth() != actual.getWidth() || golden.getHeight() != actual.getHeight()) {
            return new Result(Double.POSITIVE_INFINITY, 1.0, Double.POSITIVE_INFINITY, false);
        }
        int width = golden.getWidth();
        int height = golden.getHeight();
        long pixels = (long) width * height;
        long absoluteChannelError = 0;
        double absoluteLuminanceError = 0;
        long changedPixels = 0;
        boolean anyDifference = false;
        BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int expected = golden.getRGB(x, y);
                int observed = actual.getRGB(x, y);
                int expectedRed = (expected >>> 16) & 0xff;
                int expectedGreen = (expected >>> 8) & 0xff;
                int expectedBlue = expected & 0xff;
                int observedRed = (observed >>> 16) & 0xff;
                int observedGreen = (observed >>> 8) & 0xff;
                int observedBlue = observed & 0xff;
                int red = Math.abs(expectedRed - observedRed);
                int green = Math.abs(expectedGreen - observedGreen);
                int blue = Math.abs(expectedBlue - observedBlue);
                int maximum = Math.max(red, Math.max(green, blue));
                absoluteChannelError += red + green + blue;
                absoluteLuminanceError += Math.abs(
                        luminance(expectedRed, expectedGreen, expectedBlue)
                                - luminance(observedRed, observedGreen, observedBlue));
                if (maximum > MATERIAL_PIXEL_DELTA) {
                    changedPixels++;
                }
                if (maximum > 0) {
                    anyDifference = true;
                    int intensity = Math.min(255, maximum * 8);
                    diff.setRGB(x, y, 0xffff2000 | intensity);
                } else {
                    int quiet = (observedRed + observedGreen + observedBlue) / 12;
                    diff.setRGB(x, y, 0xff000000 | (quiet << 16) | (quiet << 8) | quiet);
                }
            }
        }
        double meanChannelError = absoluteChannelError / (pixels * 3.0);
        double changedPixelRatio = changedPixels / (double) pixels;
        double meanLuminanceError = absoluteLuminanceError / pixels;
        boolean passed =
                meanChannelError <= MAX_MEAN_CHANNEL_ERROR
                        && changedPixelRatio <= MAX_CHANGED_PIXEL_RATIO
                        && meanLuminanceError <= MAX_MEAN_LUMINANCE_ERROR;
        if (anyDifference) {
            ImageIO.write(diff, "png", diffPath.toFile());
        }
        return new Result(meanChannelError, changedPixelRatio, meanLuminanceError, passed);
    }

    private static double luminance(int red, int green, int blue) {
        return red * 0.2126 + green * 0.7152 + blue * 0.0722;
    }

    private static BufferedImage requireImage(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Invalid PNG: " + path);
        }
        return image;
    }

    private static List<Path> pngs(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static Set<String> names(List<Path> paths) {
        Set<String> result = new HashSet<>();
        for (Path path : paths) {
            result.add(path.getFileName().toString());
        }
        return result;
    }

    private static String stringArray(Set<String> values) {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(String::compareTo);
        StringBuilder value = new StringBuilder("[");
        for (int index = 0; index < sorted.size(); index++) {
            if (index > 0) {
                value.append(", ");
            }
            value.append('\"').append(sorted.get(index).replace("\\", "\\\\").replace("\"", "\\\"")).append('\"');
        }
        return value.append(']').toString();
    }

    private record Result(
            double meanChannelError,
            double changedPixelRatio,
            double meanLuminanceError,
            boolean passed) {
        String toJson(String name) {
            return String.format(
                    Locale.ROOT,
                    "    {\"name\":\"%s\",\"meanChannelError\":%.6f,\"changedPixelRatio\":%.8f,\"meanLuminanceError\":%.6f,\"passed\":%s}",
                    name,
                    meanChannelError,
                    changedPixelRatio,
                    meanLuminanceError,
                    passed);
        }
    }
}
