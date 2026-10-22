package com.example.cache.model;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

public final class SortedSetValue implements Value {
    private final TreeSet<ScoredMember> members = new TreeSet<>();
    private final Map<String, Double> scores = new HashMap<>();

    public boolean contains(String member) {
        return scores.containsKey(member);
    }

    public boolean add(double score, String member) {
        Double previousScore = scores.put(member, score);
        if (previousScore != null) {
            members.remove(new ScoredMember(previousScore, member));
        }
        members.add(new ScoredMember(score, member));
        return previousScore == null || Double.compare(previousScore, score) != 0;
    }

    public int size() {
        return members.size();
    }

    public int rank(String member) {
        if (!scores.containsKey(member)) {
            return -1;
        }
        int index = 0;
        for (ScoredMember item : members) {
            if (item.member().equals(member)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public ScoredMember get(int index) {
        if (index < 0 || index >= members.size()) {
            throw new IndexOutOfBoundsException("Sorted-set index out of range");
        }
        int current = 0;
        for (ScoredMember item : members) {
            if (current++ == index) {
                return item;
            }
        }
        throw new IndexOutOfBoundsException("Sorted-set index out of range");
    }
}
