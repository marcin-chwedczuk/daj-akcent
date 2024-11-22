package pl.marcinchwedczuk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public abstract class EpubHtmlMapper {
    private static final Logger logger = LogManager.getLogger(EpubHtmlMapper.class);

    private final String inputFile;
    private final String outputFile;

    protected EpubHtmlMapper(String inputFile, String outputFile) {
        this.inputFile = Objects.requireNonNull(inputFile);
        this.outputFile = Objects.requireNonNull(outputFile);
    }

    public void mapContent() {
        try {
            var zipOut = new ZipOutputStream(new FileOutputStream(outputFile));

            try (ZipFile zipIn = new ZipFile(inputFile)) {
                Enumeration<? extends ZipEntry> entries = zipIn.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry inputEntry = entries.nextElement();

                    createMappedEntry(inputEntry, zipOut, zipIn);
                }
            }

            zipOut.close();

        } catch (Exception e) {
            throw new RuntimeException("Error while mapping EPUB file content.", e);
        }
    }

    private void createMappedEntry(ZipEntry inputEntry, ZipOutputStream zipOut, ZipFile zipIn) throws IOException {
        ZipEntry outputEntry = new ZipEntry(inputEntry.getName());
        copyAttributes(inputEntry, outputEntry);
        zipOut.putNextEntry(outputEntry);

        if (!inputEntry.isDirectory()) {
            logger.info("Processing file {}", inputEntry.getName());

            try (var reader = zipIn.getInputStream(inputEntry)) {
                if (isHtmlFile(inputEntry.getName())) {
                    String html = readEntryAsString(reader);
                    html = mapEntryHtml(html);
                    writeStringAsEntry(zipOut, html);
                } else {
                    copyEntry(reader, zipOut);
                }
            }
        }

        zipOut.closeEntry();
    }

    protected abstract String mapEntryHtml(String html);

    private static void copyAttributes(ZipEntry inputEntry, ZipEntry outputEntry) {
        if (inputEntry.getComment() != null)
            outputEntry.setComment(inputEntry.getComment());

        if (inputEntry.getCreationTime() != null)
            outputEntry.setCreationTime(inputEntry.getCreationTime());

        if (inputEntry.getLastAccessTime() != null)
            outputEntry.setLastAccessTime(inputEntry.getLastAccessTime());

        if (inputEntry.getLastModifiedTime() != null)
            outputEntry.setLastModifiedTime(inputEntry.getLastModifiedTime());

        if (inputEntry.getExtra() != null)
            outputEntry.setExtra(inputEntry.getExtra());
    }

    private static void writeStringAsEntry(ZipOutputStream zipOut, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        zipOut.write(bytes, 0, bytes.length);
    }

    private static String readEntryAsString(InputStream reader) throws IOException {
        return new String(reader.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void copyEntry(InputStream inputEntryBytes, ZipOutputStream zipOut) throws IOException {
        byte[] buff = new byte[1024];
        int size;
        while ((size = inputEntryBytes.read(buff, 0, buff.length)) > 0) {
            zipOut.write(buff, 0, size);
        }
    }

    private static boolean isHtmlFile(String name) {
        if (name == null || name.isBlank()) return false;
        name = name.toLowerCase();
        return name.endsWith(".html") || name.endsWith(".xhtml");
    }
}
