package com.khack.review.practice.domain;

import java.util.regex.Pattern;

/**
 * 의미 없는 답 판별 (스펙 §6.4.5). 한글 자모만 친 답("ㅁㄴㅇㄹ", "ㄴㄴ"), 기호·숫자 없이 같은 글자만 반복한 답("sss"),
 * 기호만 있는 답은 떠올리지 못한 것으로 보고 Jev 없이 코드가 `not_met`으로 채점한다. Jev는 이런 답에 판정 불가를 골라
 * 기억 상태가 바뀌지 않고 멀쩡한 문제가 재검사로 넘어갔다.
 * 한 글자짜리 실제 답("힙", "B", "3")은 의미 없는 답으로 보지 않는다.
 */
public final class NonAnswer {

    /** 한글 자모(호환 자모)·공백·기호만. */
    private static final Pattern JAMO_ONLY = Pattern.compile("[\\u3131-\\u318E\\s\\p{Punct}]+");
    /** 기호·공백만. */
    private static final Pattern SYMBOLS_ONLY = Pattern.compile("[\\s\\p{Punct}\\p{IsPunctuation}\\p{S}]+");

    private NonAnswer() {
    }

    public static boolean is(String answer) {
        String text = answer.strip();
        if (text.isEmpty() || SYMBOLS_ONLY.matcher(text).matches()) {
            return true;
        }
        if (JAMO_ONLY.matcher(text).matches()) {
            return true;
        }
        return isRepeatedLetter(text);
    }

    /** 영문자 한 종류만 세 번 이상 반복("sss", "aaaa"). */
    private static boolean isRepeatedLetter(String text) {
        String letters = text.replaceAll("\\s", "");
        if (letters.length() < 3 || !letters.chars().allMatch(Character::isLetter)) {
            return false;
        }
        int first = Character.toLowerCase(letters.charAt(0));
        return letters.chars().allMatch(c -> Character.toLowerCase(c) == first) && first < 128;
    }
}
