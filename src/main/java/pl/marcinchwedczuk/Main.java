package pl.marcinchwedczuk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.jsoup.parser.Parser.*;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    private static final int ERRORCODE_SUCCESS = 0;
    private static final int ERRORCODE_WRONG_ARGUMENTS = 1;
    private static final int ERRORCODE_EXCEPTION = 2;
    private static final int ERRORCODE_OUTPUT_ALREADY_EXISTS = 3;
    private static final int ERRORCODE_READING_DICTIONARY_FAILED = 4;

    public static void main(String[] args) {
        // args = new String[] { "/Users/mc/dev/daj-akcent/test-data/ru1.epub" };

        if (args.length != 1 || args[0] == null || args[0].isBlank()) {
            printUsage();
            System.exit(ERRORCODE_WRONG_ARGUMENTS);
        }

        String inputEpub = args[0].trim();
        if (!inputEpub.endsWith(".epub")) {
            logger.error("Invalid input file extension, .epub expected.");
            printUsage();
            System.exit(ERRORCODE_WRONG_ARGUMENTS);
        }

        String outputEpub = inputEpub.replace(".epub", "-with-accent.epub");
        if (Files.exists(Paths.get(outputEpub))) {
            logger.error("Output file {} already exists.", outputEpub);
            System.exit(ERRORCODE_OUTPUT_ALREADY_EXISTS);
        }

        try {
            AccentDictionary accentDictionary = AccentDictionary.loadFromResources();

            EpubHtmlMapper addAccentMapper = new EpubHtmlMapper(inputEpub, outputEpub) {
                @Override
                protected String mapEntryHtml(String html) {
                    try {
                        return addAccents(html, accentDictionary, true);
                    } catch (Exception e) {
                        logger.info("Parsing contents as XML failed, retrying with HTML parser...", e);
                        return addAccents(html, accentDictionary, false);
                    }
                }
            };

            addAccentMapper.mapContent();

            System.exit(ERRORCODE_SUCCESS);
        } catch (Exception e) {
            logger.error("Processing failed.", e);
            deleteFileQuietly(outputEpub);
        }
    }

    private static String addAccents(String html, AccentDictionary accentDictionary, boolean strictXml) {
        Document document = Jsoup.parse(html, "", strictXml ? xmlParser() : htmlParser() );

        document.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode textNode) {
                    String text = textNode.text();
                    textNode.text(accentDictionary.addAccentsToText(text));
                }
            }

            @Override
            public void tail(Node node, int depth) { }
        });

        return document.html().replaceAll("(.)@@", "<b>$1</b>");
    }

    private static void deleteFileQuietly(String file) {
        try {
            Files.delete(Paths.get(file));
        } catch (Exception e) {
            logger.warn("Cannot remove temporary file {}", file, e);
        }
    }

    private static void printUsage() {
        System.err.println("Missing parameter: input");
        System.err.println("Usage:");
        System.err.printf("\tdaj-akcent input.epub%n");
    }
}