package com.khack.review.tools.verify;

import com.khack.review.tools.verify.TurnComparison.Extra;
import com.khack.review.tools.verify.TurnComparison.Missing;
import com.khack.review.tools.verify.TurnComparison.Verdict;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class TurnComparator {

    private TurnComparator() {
    }

    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).replaceAll("\\s+", " ").strip();
    }

    public static TurnComparison compare(List<String> expected, List<String> actual) {
        Set<Integer> used = new HashSet<>();
        List<Integer> matchedPositions = new ArrayList<>();
        List<Integer> unmatchedExpected = new ArrayList<>();
        int exact = 0;
        int normalized = 0;

        for (int i = 0; i < expected.size(); i++) {
            String text = expected.get(i);
            int j = indexOf(actual, used, a -> a.equals(text));
            if (j >= 0) {
                exact++;
            } else {
                String target = normalize(text);
                j = indexOf(actual, used, a -> normalize(a).equals(target));
            }
            if (j >= 0) {
                normalized++;
                used.add(j);
                matchedPositions.add(j);
            } else {
                unmatchedExpected.add(i);
            }
        }

        List<Missing> missing = new ArrayList<>();
        for (int i : unmatchedExpected) {
            String closest = i < actual.size() && !used.contains(i) ? actual.get(i) : null;
            missing.add(new Missing(i + 1, expected.get(i), closest));
        }
        List<Extra> extra = new ArrayList<>();
        for (int k = 0; k < actual.size(); k++) {
            if (!used.contains(k)) {
                extra.add(new Extra(k + 1, actual.get(k)));
            }
        }
        boolean orderPreserved = true;
        for (int i = 1; i < matchedPositions.size(); i++) {
            if (matchedPositions.get(i) <= matchedPositions.get(i - 1)) {
                orderPreserved = false;
                break;
            }
        }
        boolean sameCount = expected.size() == actual.size();

        Verdict verdict = Verdict.DIVERGED;
        if (sameCount && orderPreserved && exact == expected.size()) {
            verdict = Verdict.IDENTICAL;
        } else if (sameCount && orderPreserved && normalized == expected.size()) {
            verdict = Verdict.WHITESPACE_ONLY;
        }

        return new TurnComparison(expected.size(), actual.size(), exact, normalized,
                List.copyOf(missing), List.copyOf(extra), orderPreserved, verdict);
    }

    private static int indexOf(List<String> actual, Set<Integer> used, Predicate<String> match) {
        for (int k = 0; k < actual.size(); k++) {
            if (!used.contains(k) && match.test(actual.get(k))) {
                return k;
            }
        }
        return -1;
    }
}
