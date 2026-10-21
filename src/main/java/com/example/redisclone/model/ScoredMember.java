package com.example.redisclone.model;

public record ScoredMember(double score, String member) implements Comparable<ScoredMember> {
    @Override
    public int compareTo(ScoredMember other) {
        int scoreComparison = Double.compare(score, other.score);
        return scoreComparison != 0 ? scoreComparison : member.compareTo(other.member);
    }
}
