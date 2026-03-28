package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.output.sunat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class SunatCodeInferer {
    private static final double ACCEPTANCE_THRESHOLD = 0.78d;
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9 ]");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    private static final SunatCatalogRoot CATALOG =
            SunatCatalogLoader.load(new ObjectMapper());

    private SunatCodeInferer() {
    }

    public static String infer(String productName, String categoryName) {
        String normalizedProduct = normalize(productName);
        String normalizedCategory = normalize(categoryName);

        String exactProductCode = findExactProductCode(normalizedProduct, normalizedCategory);
        if (exactProductCode != null) {
            return exactProductCode;
        }

        ScoredCandidate bestRule = rankRules(normalizedProduct, normalizedCategory, CATALOG.rules());
        if (bestRule != null && bestRule.score() >= ACCEPTANCE_THRESHOLD) {
            return bestRule.code();
        }

        String categoryDefault = findCategoryDefault(normalizedCategory);
        if (categoryDefault != null) {
            return categoryDefault;
        }

        if (bestRule != null) {
            return bestRule.code();
        }

        throw new IllegalStateException("No se pudo inferir código SUNAT para producto=" + productName);
    }

    private static String findExactProductCode(String normalizedProduct, String normalizedCategory) {
        for (SunatCatalogRoot.ProductEntry product : CATALOG.products()) {
            if (normalize(product.normalizedName()).equals(normalizedProduct)
                    && normalize(product.categoryNormalized()).equals(normalizedCategory)
                    && product.suggestedSunatCode() != null
                    && !product.suggestedSunatCode().isBlank()) {
                return product.suggestedSunatCode();
            }
        }
        return null;
    }

    private static String findCategoryDefault(String normalizedCategory) {
        for (SunatCatalogRoot.CategoryDefaultEntry entry : CATALOG.categoryDefaults()) {
            if (normalize(entry.categoryNormalized()).equals(normalizedCategory)) {
                return entry.defaultCode();
            }
        }
        return null;
    }

    private static ScoredCandidate rankRules(String normalizedProduct,
                                             String normalizedCategory,
                                             List<SunatCatalogRoot.RuleEntry> rules) {
        ScoredCandidate best = null;

        for (SunatCatalogRoot.RuleEntry rule : rules) {
            double lexical = lexicalScore(normalizedProduct, rule);
            double fuzzy = fuzzyScore(normalizedProduct, rule);
            double category = categoryScore(normalizedCategory, rule);
            double confidence = rule.confidence() == null ? 0.0d : rule.confidence();

            double total = (0.45d * lexical) + (0.20d * fuzzy) + (0.20d * category) + (0.15d * confidence);

            if (best == null || total > best.score()) {
                best = new ScoredCandidate(rule.code(), total);
            }
        }

        return best;
    }

    private static double lexicalScore(String normalizedProduct, SunatCatalogRoot.RuleEntry rule) {
        Set<String> productTokens = tokenize(normalizedProduct);
        Set<String> ruleTokens = new HashSet<>();

        if (rule.keywords() != null) {
            for (String keyword : rule.keywords()) {
                ruleTokens.addAll(tokenize(normalize(keyword)));
            }
        }

        if (productTokens.isEmpty() || ruleTokens.isEmpty()) {
            return 0.0d;
        }

        int matches = 0;
        for (String token : productTokens) {
            if (ruleTokens.contains(token)) {
                matches++;
            }
        }

        return Math.min((double) matches / (double) productTokens.size(), 1.0d);
    }

    private static double fuzzyScore(String normalizedProduct, SunatCatalogRoot.RuleEntry rule) {
        double best = 0.0d;

        if (rule.keywords() != null) {
            for (String keyword : rule.keywords()) {
                best = Math.max(best, normalizedSimilarity(normalizedProduct, normalize(keyword)));
            }
        }

        return best;
    }

    private static double categoryScore(String normalizedCategory, SunatCatalogRoot.RuleEntry rule) {
        if (rule.categories() == null) {
            return 0.0d;
        }

        for (String category : rule.categories()) {
            if (normalize(category).equals(normalizedCategory)) {
                return 1.0d;
            }
        }

        return 0.0d;
    }

    private static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        for (String token : text.split(" ")) {
            String t = token.trim();
            if (t.length() >= 2) {
                tokens.add(t);
            }
        }

        return tokens;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .trim();

        normalized = normalized.replace("C/", "CON ");
        normalized = normalized.replace("STIKER", "STICKER");
        normalized = normalized.replace("VACUM", "VACUUM");
        normalized = normalized.replace("TOMATODO", "BOTELLA");

        normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll(" ");
        normalized = MULTIPLE_SPACES.matcher(normalized).replaceAll(" ").trim();

        return normalized;
    }

    private static double normalizedSimilarity(String a, String b) {
        if (a.isBlank() || b.isBlank()) {
            return 0.0d;
        }

        int distance = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 1.0d : 1.0d - ((double) distance / (double) max);
    }

    private static int levenshtein(String left, String right) {
        int[][] dp = new int[left.length() + 1][right.length() + 1];

        for (int i = 0; i <= left.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[left.length()][right.length()];
    }

    private record ScoredCandidate(String code, double score) {
    }
}