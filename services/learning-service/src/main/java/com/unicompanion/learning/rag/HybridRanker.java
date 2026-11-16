package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reciprocal Rank Fusion over ordered retrieval lists (vector + lexical).
 */
final class HybridRanker {

    private static final int RRF_K = 60;

    private HybridRanker() {
    }

    /**
     * Merges ranked lists by chunk id; earlier lists are not weighted differently (classic RRF).
     * Returns up to {@code limit} chunks, preferring the first occurrence's payload when ids collide.
     */
    @SafeVarargs
    static List<ChatModel.RetrievedChunk> fuse(int limit, List<ChatModel.RetrievedChunk>... rankedLists) {
        if (limit < 1 || rankedLists == null || rankedLists.length == 0) {
            return List.of();
        }
        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, ChatModel.RetrievedChunk> byId = new LinkedHashMap<>();
        for (List<ChatModel.RetrievedChunk> list : rankedLists) {
            if (list == null) {
                continue;
            }
            for (int rank = 0; rank < list.size(); rank++) {
                ChatModel.RetrievedChunk chunk = list.get(rank);
                if (chunk == null || chunk.chunkId() == null) {
                    continue;
                }
                byId.putIfAbsent(chunk.chunkId(), chunk);
                scores.merge(chunk.chunkId(), 1.0 / (RRF_K + rank + 1), Double::sum);
            }
        }
        List<Map.Entry<UUID, Double>> ordered = new ArrayList<>(scores.entrySet());
        ordered.sort(Comparator
                .<Map.Entry<UUID, Double>>comparingDouble(Map.Entry::getValue).reversed()
                .thenComparing(entry -> entry.getKey().toString()));
        List<ChatModel.RetrievedChunk> out = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : ordered) {
            ChatModel.RetrievedChunk chunk = byId.get(entry.getKey());
            if (chunk != null) {
                out.add(chunk);
            }
            if (out.size() >= limit) {
                break;
            }
        }
        return List.copyOf(out);
    }
}
