package pl.marcinchwedczuk;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;
import org.tartarus.snowball.ext.RussianStemmer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
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

    private static RussianStemmer ruStemmer = new RussianStemmer();

    private static String stem(String word) {
        ruStemmer.setCurrent(word);
        ruStemmer.stem();
        return ruStemmer.getCurrent();
    }

    record AccentDictionary(Map<String, String> wordsAccent,
                            Map<String, String> wordFormsAccent,
                            Map<String, String> heuristicalStemAccent) {

    }

    public static AccentDictionary loadAccentDictionary() {
        Map<String, String> wordsAccent = new HashMap<>();
        Map<String, String> heuristicalStemAccent = new HashMap<>();
        Map<String, String> wordFormsAccent = new HashMap<>();

        try {
            // Process words
            InputStream resource = Main.class.getResourceAsStream("/russian3-words.csv");

            try (CSVParser csvParser = new CSVParser(new InputStreamReader(resource, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
                int progress = 0;
                for (CSVRecord record : csvParser) {
                    progress++;
                    if (progress % 1000 == 0) {
                        logger.info("accent dictionary: {} records processed...", progress);
                    }

                    wordsAccent.put(record.get(2), record.get(3));

                    String w = record.get(2);
                    String wa = record.get(3);
                    String stem = stem(w);
                    int accentPosition = wa.indexOf("'");
                    if (accentPosition != -1 && accentPosition <= stem.length()) {
                        String astem = stem.substring(0, accentPosition) + "'" + stem.substring(accentPosition);
                        heuristicalStemAccent.put(stem, astem);
                    }
                }
            }

            for (int partId = 1; partId <= 2; partId++) {
                // Process inclination forms
                // Stupid Github policy limits big files to 100MB, don't want to impl compresssion so I just split the file
                resource = Main.class.getResourceAsStream("/russian3-words_forms" + partId + ".csv");

                try (CSVParser csvParser = new CSVParser(new InputStreamReader(resource, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
                    int progress = 0;
                    for (CSVRecord record : csvParser) {
                        progress++;
                        if (progress % 1000 == 0) {
                            logger.info("forms accent dictionary: {} records processed...", progress);
                            break;
                        }


                        String accented = record.get(4);
                        String word = record.get(5);
                        if (accented.contains("'")) {
                            wordFormsAccent.put(word, accented);
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Reading dictionary failed", e);
            System.exit(ERRORCODE_READING_DICTIONARY_FAILED);
        }

        return new AccentDictionary(
                Collections.unmodifiableMap(wordsAccent),
                Collections.unmodifiableMap(wordFormsAccent),
                Collections.unmodifiableMap(heuristicalStemAccent));
    }

    public static void main(String[] args) {
        args = new String[] { "/Users/mc/dev/daj-akcent/test-data/ru1.epub" };

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

        AccentDictionary accentDictionary = loadAccentDictionary();

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
                            if (isContentFile(inputEntry.getName())) {
                                String html = readEntryAsString(reader);
                                html = addAccents(html, accentDictionary);
                                writeStringAsEntry(zipOut, html);
                            } else {
                                copyEntry(reader, zipOut);
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

        System.exit(ERRORCODE_SUCCESS);
    }

    private static void writeStringAsEntry(ZipOutputStream zipOut, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        zipOut.write(bytes, 0, bytes.length);
    }

    private static String addAccents(String html, AccentDictionary accentDictionary) {
        // Parse the XHTML document
        Document document = Jsoup.parse(html, "",
                org.jsoup.parser.Parser.xmlParser() ); // TODO: Fallback to HTML parser on error

        // Traverse and process all text nodes
        document.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                // Check if the node is a TextNode
                if (node instanceof TextNode textNode) {
                    String text = textNode.text();
                    textNode.text(addAccentsToString(text.toLowerCase(), accentDictionary));
                }
            }

            @Override
            public void tail(Node node, int depth) {
                // No action needed on the way back
            }
        });

        return document.html();
    }


    private static String addAccentsToString(String text, AccentDictionary accentDictionary) {
        StringBuilder output = new StringBuilder(text.length());

        // Simple \b is not working with Unicode
        String[] parts = text.split("(?<=[\\s,.:;\"']|^)|(?=[\\s,.:;\"']|$)");
        for (String part : parts) {
            if (part.isBlank()) {
                output.append(part);
            } else if (accentDictionary.wordsAccent.containsKey(part)) {
                output.append(accentDictionary.wordsAccent.get(part));
            } else if (accentDictionary.wordFormsAccent.containsKey(part)) {
                output.append(accentDictionary.wordFormsAccent.get(part));
            } else {
                // Przypadek odmiany, staramy się znaleść stem i na nim wyszukać akcent
                String stem = stem(part);
                if (accentDictionary.heuristicalStemAccent.containsKey(stem)) {
                    output.append(accentDictionary.heuristicalStemAccent.get(stem));
                    output.append(part.substring(stem.length()));
                } else {
                    output.append(part);
                }
            }
        }

        return output.toString();
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

    private static boolean isContentFile(String name) {
        if (name == null || name.isBlank()) return false;
        name = name.toLowerCase();
        return name.endsWith(".html") || name.endsWith(".xhtml");
    }

    private static void printUsage() {
        System.err.println("Missing parameter: input");
        System.err.println("Usage:");
        System.err.printf("\tdaj-akcent input.epub%n");
    }
}