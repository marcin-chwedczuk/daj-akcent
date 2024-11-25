package pl.marcinchwedczuk;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.tartarus.snowball.ext.RussianStemmer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class AccentDictionary {
    private static final RussianStemmer stemmer = new RussianStemmer();

    private static String stem(String word) {
        stemmer.setCurrent(word);
        stemmer.stem();
        return stemmer.getCurrent();
    }

    public static AccentDictionary loadFromResources() {
        Map<String, String> wordsDict = new HashMap<>();
        Map<String, String> stemDict = new HashMap<>();
        Map<String, String> formsDict = new HashMap<>();

        loadWords("/russian3-words.csv", wordsDict, stemDict, 2, 3);

        for (int partId = 1; partId <= 2; partId++) {
            loadWords("/russian3-words_forms" + partId + ".csv", formsDict, stemDict, 5, 4);
        }

        return new AccentDictionary(
                Collections.unmodifiableMap(wordsDict),
                Collections.unmodifiableMap(formsDict),
                Collections.unmodifiableMap(stemDict));
    }

    private static void loadWords(String resourcePath, Map<String, String> dict, Map<String, String> stemDict, int wordColumn, int accentColumn) {
        InputStream resource = AccentDictionary.class.getResourceAsStream("/russian3-words.csv");
        if (resource == null)
            throw new RuntimeException("Resource " + resourcePath + " not found.");

        try {
            try (CSVParser csvParser = new CSVParser(new InputStreamReader(resource, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
                for (CSVRecord record : csvParser) {
                    String word = record.get(wordColumn);
                    String accent = record.get(accentColumn);

                    // Resolve the collision by assuming the more popular word is first in the CSV dictionary
                    if (dict.containsKey(word)) continue;;
                    // Due to human errors some accent words do not possess accent, we skip them here
                    if (accent.indexOf('\'') == -1) continue;

                    dict.put(word, accent);

                    // Add the word stem with accent to stemDict
                    String stem = stem(word);
                    int accentPosition = accent.indexOf("'");

                    if (accentPosition != -1 && accentPosition <= stem.length()) {
                        // Accent is within the stem
                        String stemWithAccent = stem.substring(0, accentPosition) + "'" + stem.substring(accentPosition);
                        stemDict.putIfAbsent(stem, stemWithAccent);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot load dictionary from resource: " + resourcePath, e);
        }
    }

    private final Map<String, String> wordsDict;
    private final Map<String, String> formsDict;
    private final Map<String, String> stemDict;

    private AccentDictionary(Map<String, String> wordsDict,
                     Map<String, String> formsDict,
                     Map<String, String> stemDict) {
        this.wordsDict = Collections.unmodifiableMap(wordsDict);
        this.formsDict = Collections.unmodifiableMap(formsDict);
        this.stemDict = Collections.unmodifiableMap(stemDict);
    }

    public String addAccentsToText(String text) {
        StringBuilder output = new StringBuilder(text.length());

        // Simple \b is not working with Unicode
        String[] parts = text.split("(?<=[\\s,.:;\"']|^)|(?=[\\s,.:;\"']|$)");
        for (String part : parts) {
            output.append(addAccent(part));
        }

        return output.toString();
    }

     public String addAccent(String part) {
        if (part.isBlank()) {
            return part;
        }

        String partLC = part.toLowerCase();

        if (wordsDict.containsKey(partLC)) {
            return (addAccentPreservingCasing(part, wordsDict.get(partLC)));
        } else if (formsDict.containsKey(partLC)) {
            return (addAccentPreservingCasing(part, formsDict.get(partLC)));
        } else {
            // Heuristics, we don't have the word in our dicts, but maybe we may use word stem to find the accent?
            String stem = stem(part).toLowerCase();
            if (stemDict.containsKey(stem)) {
                return (addAccentPreservingCasing(part, stemDict.get(stem)));
            } else {
                return (part);
            }
        }
    }

    private static String addAccentPreservingCasing(String originalWorld, String accentedWorld) {
        int accentIndex = accentedWorld.indexOf('\'');
        if (accentIndex == -1) return originalWorld;
        return originalWorld.substring(0, accentIndex) + "@@" + originalWorld.substring(accentIndex);
    }}
