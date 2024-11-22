package pl.marcinchwedczuk;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    private static final int ERRORCODE_SUCCESS = 0;
    private static final int ERRORCODE_WRONG_ARGUMENTS = 1;
    private static final int ERRORCODE_EXCEPTION = 2;
    private static final int ERRORCODE_OUTPUT_ALREADY_EXISTS = 3;
    private static final int ERRORCODE_READING_DICTIONARY_FAILED = 4;

    public static void main(String[] args) {
        Map<String, String> accentDictionary = new HashMap<>();
        try {
            InputStream dictionaryIS = Main.class.getResourceAsStream("/russian3-words.csv");
            try (CSVParser csvParser = new CSVParser(new InputStreamReader(dictionaryIS, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
                int progress = 0;
                for (CSVRecord record : csvParser) {
                    progress++;
                    if (progress % 1000 == 0) {
                        logger.info("accent dictionary: {} records processed...", progress);
                    }

                    accentDictionary.put(record.get(2), record.get(3));
                }
            }
        } catch (Exception e) {
            logger.error("Reading dictionary failed", e);
            System.exit(ERRORCODE_READING_DICTIONARY_FAILED);
        }
    }

    public static void main2(String[] args) {
        args = new String[] { "/Users/mc/dev/daj-akcent/test-data/epub3-example.epub" };

        if (args.length != 1 || args[0] == null || args[0].isBlank()) {
            printUsage();
            System.exit(ERRORCODE_WRONG_ARGUMENTS);
        }

        String epubFilename = args[0].trim();
        if (!epubFilename.endsWith(".epub")) {
            printUsage();
            System.exit(ERRORCODE_WRONG_ARGUMENTS);
        }

        String outputEpubFile = epubFilename.replace(".epub", "-with-accent.epub");
        if (Files.exists(Paths.get(outputEpubFile))) {
            logger.error("Output file {} already exists.", outputEpubFile);
            System.exit(ERRORCODE_OUTPUT_ALREADY_EXISTS);
        }

        try {
            var zipOut = new ZipOutputStream(new FileOutputStream(outputEpubFile));

            try (ZipFile inputEpub = new ZipFile(epubFilename)) {
                Enumeration<? extends ZipEntry> entries = inputEpub.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry inputEntry = entries.nextElement();

                    ZipEntry outputEntry = new ZipEntry(inputEntry.getName());
                    outputEntry.setComment(inputEntry.getComment());
                    // TODO: Other attributes
                    zipOut.putNextEntry(outputEntry);

                    if (!inputEntry.isDirectory()) {
                        // Handle file
                        logger.info("Processing file {}", inputEntry.getName());
                        try (var reader = inputEpub.getInputStream(inputEntry)) {

                            byte[] buff = new byte[1024];
                            int size;
                            while ((size = reader.read(buff, 0, buff.length)) >= 0) {
                                zipOut.write(buff, 0, size);
                            }
                        }
                    }

                    zipOut.closeEntry();
                }
            }

            zipOut.close();

        } catch (Exception e) {
            logger.error("Processing epub files failed.", e);
            System.exit(ERRORCODE_EXCEPTION);
        }

        logger.info("Hello world!");
        System.exit(ERRORCODE_SUCCESS);
    }

    private static void printUsage() {
        System.err.println("Missing parameter: input");
        System.err.println("Usage:");
        System.err.printf("\tdaj-akcent input.epub%n");
    }
}