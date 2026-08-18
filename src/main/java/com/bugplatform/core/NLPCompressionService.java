package com.bugplatform.core;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@Service
public class NLPCompressionService {
    private StanfordCoreNLP pipeline;
    private Set<String> stopWords;

    public NLPCompressionService() {
        Properties props = new Properties();
        // Use tokenization, sentence splitting, part-of-speech tagging, and lemmatization
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        // Optimize for speed if possible
        props.setProperty("tokenize.options", "untokenizable=noneDelete");
        this.pipeline = new StanfordCoreNLP(props);
        
        // Basic stop words for code/text compression
        this.stopWords = new HashSet<>(Arrays.asList(
            "the", "is", "in", "at", "of", "on", "a", "an", "and", "or", "but", "if", "then", "else", "when",
            "for", "to", "with", "by", "as", "it", "this", "that"
        ));
    }

    public String compressText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        // Limit the input size to avoid memory issues with NLP processing itself
        if (text.length() > 50000) {
            text = text.substring(0, 50000);
        }

        Annotation document = new Annotation(text);
        pipeline.annotate(document);

        StringBuilder compressed = new StringBuilder();
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);

        for (CoreMap sentence : sentences) {
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
                
                // Filter out punctuation and common stop words
                if (lemma != null && !lemma.matches("\\p{Punct}") && !stopWords.contains(lemma.toLowerCase())) {
                    compressed.append(lemma).append(" ");
                }
            }
        }
        return compressed.toString().trim();
    }
}
