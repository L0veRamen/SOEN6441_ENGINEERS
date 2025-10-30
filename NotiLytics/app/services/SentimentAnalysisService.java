package services;

import models.Article;
import models.Sentiment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
/*
 * SentimentAnalysisService.java
 *
 * Provides sentiment analysis utilities for news articles and word lists.
 * Supports multilingual positive and negative word detection, and calculates sentiment using the Sentiment enum.
 *
 * Author: Ruochen Qiao
 */

@Singleton
public class SentimentAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(SentimentAnalysisService.class);
    private static final Set<String> POSITIVE_STRINGS = new HashSet<>(Arrays.asList(
            // English
            "happy", "joy", "love", "excellent", "good", "great", "wonderful", "amazing", "awesome", "delighted", "pleased",
            "perfect", "fantastic", "superb", "outstanding", "brilliant", "fabulous", "marvelous", "exceptional", "cheerful",
            "blessed", "excited", "thrilled", "joyful", "ecstatic", "content", "satisfied", "grateful", "thankful", "proud",
            "hopeful", "optimistic", "calm", "stable", "strong", "peaceful", "secure", "improving", "growing", "resilient",
            "booming", "thriving", "recovered", "reborn", "restored", "empowered", "harmonious", "balanced", "cooperative",
            "united", "prosperous", "encouraging", "supportive", "bright", "positive", "constructive", "beneficial",
            "innovative", "green", "sustainable", "clean", "safe", "protected", "affordable", "inclusive", "transparent",
            "fair", "free", "democratic", "reform", "progress", "stability", "growth", "success", "achievement", "victory",
            "peace", "agreement", "deal", "breakthrough", "improvement", "recovery", "prosperity", "economic boom",
            "bull market", "expansion", "renewable", "carbon neutral", "climate action", "green energy", "clean air",
            "clean water", "biodiversity", "innovation", "technological progress", "collaboration", "solidarity",
            "mutual respect", "global cooperation",
            // French
            "heureux", "content", "joyeux", "magnifique", "excellent", "formidable", "merveilleux", "parfait", "génial",
            "splendide", "incroyable", "charmant", "satisfait", "agréable", "positif",
            // German
            "glücklich", "froh", "wunderbar", "toll", "ausgezeichnet", "prima", "hervorragend", "fantastisch",
            "begeistert", "zufrieden", "erfolgreich", "liebenswert", "positiv",
            // Russian
            "счастливый", "радостный", "прекрасный", "отличный", "замечательный", "великолепный", "потрясающий",
            "довольный", "воодушевлённый", "улыбчивый",
            // Chinese
            "开心", "高兴", "快乐", "幸福", "满意", "棒", "优秀", "精彩", "出色", "感动", "感恩", "自豪", "赞", "平静", "希望",
            "充实", "振奋", "美好", "安心", "幸福感",
            // Japanese
            "嬉しい", "幸せ", "楽しい", "最高", "素晴らしい", "すごい", "いいね", "感動", "満足", "平和", "ポジティブ",
            // Spanish
            "feliz", "alegre", "encantado", "excelente", "genial", "maravilloso", "fantástico", "contento", "positivo",
            // Emoticons and Emojis
            ":-)", ":)", ":D", "^_^", "😊", "😁", "😄", "😃", "😀", "🥰", "😍", "🤗", "👍", "💪", "✨", "🌟", "⭐", "💯", "🎉", "🎊", "🌈", "😺",
            // Expressions
            "well done", "bravo", "kudos", "congrats", "hooray", "yay", "woohoo", "keep it up", "nice job", "awesome work",
            "good vibes", "feeling great", "what a joy", "so proud"
    ));
    private static final Set<String> NEGATIVE_STRINGS = new HashSet<>(Arrays.asList(
            // English
            "sad", "bad", "terrible", "hate", "angry", "upset", "disappointed", "awful", "horrible", "miserable", "unhappy",
            "tragic", "devastating", "dreadful", "poor", "inferior", "worst", "lousy", "unpleasant", "disaster", "failing",
            "frustrating", "annoying", "irritating", "depressing", "gloomy", "painful", "distressing", "worried", "anxious",
            "unstable", "volatile", "decline", "collapse", "crisis", "recession", "inflation", "unemployment", "layoffs",
            "shutdown", "conflict", "war", "violence", "corruption", "scandal", "unrest", "protest", "tension", "chaos",
            "uncertainty", "pollution", "contamination", "shortage", "poverty", "inequality", "debt", "default", "bankrupt",
            "loss", "fraud", "collapse", "meltdown", "falling", "plunge", "bear market", "stagnation", "trade war",
            "human rights violation", "censorship", "restriction", "dictatorship", "authoritarian", "propaganda",
            "famine", "drought", "heatwave", "wildfire", "earthquake", "storm", "hurricane", "flood", "climate crisis",
            "global warming", "polluted", "smog", "dirty", "unsafe", "dangerous", "toxic", "unhealthy", "fatal",
            "fear", "panic", "anger", "rage", "outrage", "hatred", "division", "polarization", "violence", "terrorism",
            "cyberattack", "data breach", "power outage", "shortfall", "instability", "inefficiency", "corruption scandal",
            // French
            "triste", "mauvais", "terrible", "horrible", "malheureux", "déteste", "déçu", "affreux", "catastrophique",
            "énervé", "fâché", "inquiet", "fatigué", "déprimé", "désespéré",
            // German
            "traurig", "schlecht", "schrecklich", "furchtbar", "entsetzlich", "miserabel", "enttäuscht", "wütend",
            "verärgert", "besorgt", "ängstlich", "hoffnungslos", "depressiv",
            // Russian
            "грустный", "плохой", "ужасный", "печальный", "разочарованный", "несчастный", "гневный", "злой",
            "уставший", "тревожный", "напуганный", "беспомощный", "разбитый",
            // Chinese
            "难过", "伤心", "糟糕", "生气", "失望", "烦", "崩溃", "害怕", "焦虑", "悲伤", "痛苦", "孤独", "沮丧", "绝望", "气愤",
            "恐惧", "尴尬", "无助", "疲惫", "压抑", "烦躁",
            // Japanese
            "悲しい", "つらい", "最悪", "嫌い", "怒ってる", "疲れた", "落ち込む", "怖い", "不安", "失望", "痛い", "恥ずかしい",
            // Spanish
            "triste", "malo", "terrible", "horrible", "decepcionado", "enojado", "frustrado", "ansioso", "cansado", "solo",
            "asustado", "infeliz", "molesto", "negativo",
            // Emoticons and Emojis
            ":-(", ":(", ":'(", "😢", "😭", "😤", "😠", "😡", "😞", "😟", "😔", "😣", "😖", "💔", "👎", "😫", "😩", "😰", "😱", "☹️",
            // Expressions
            "not good", "too bad", "what a shame", "unfortunately", "alas", "oh no", "how terrible", "i hate this",
            "so disappointed", "feeling down", "in pain", "so sad", "this sucks"
    ));

    public static Sentiment analyzeWordList(List<String> wordList) {
        if (wordList == null || wordList.isEmpty()) {
//            log.debug("Word list is null or empty");
            return Sentiment.NEUTRAL;
        }

        List<String> processedWords = wordList.stream()
                .map(word -> word.toLowerCase().replaceAll("[^a-zA-Z0-9\\u0080-\\uffff]", ""))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toList());

        double positiveCount = processedWords.stream()
                .filter(word -> POSITIVE_STRINGS.stream()
                        .anyMatch(pos -> pos.toLowerCase().equals(word)))
                .count();

        double negativeCount = processedWords.stream()
                .filter(word -> NEGATIVE_STRINGS.stream()
                        .anyMatch(neg -> neg.toLowerCase().equals(word)))
                .count();

        double total = negativeCount + positiveCount;
        log.debug("Original words: {}, Processed words: {}", wordList.size(), processedWords.size());
        log.debug("Positive words: {}, Negative words: {}, Total sentiment words: {}",
                 positiveCount, negativeCount, total);

        if (total == 0) {
            return Sentiment.NEUTRAL;
        }
        double positiveRatio = positiveCount / total;
        double negativeRatio = negativeCount / total;
        log.debug("Sentiment ratios - Positive: {}, Negative: {}", positiveRatio, negativeRatio);
        return Sentiment.fromScores(positiveRatio, negativeRatio);
    }

    public static Sentiment analyzeArticles(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return Sentiment.NEUTRAL;
        }

        List<String> wordList = articles.stream()
                .filter(article -> article != null && article.description() != null)
                .map(article -> article.description().trim().toLowerCase().split("\\s+"))
                .flatMap(Arrays::stream)
                .filter(word -> !word.trim().isEmpty())
                .collect(Collectors.toList());

        return analyzeWordList(wordList);
    }
}
