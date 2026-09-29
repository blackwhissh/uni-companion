package com.unicompanion.learning.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class ChunkRetrievalRepository {

    private final JdbcTemplate jdbc;

    public ChunkRetrievalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ChatModel.RetrievedChunk> findTopK(
            UUID courseId,
            List<UUID> materialIds,
            float[] queryEmbedding,
            int limit
    ) {
        if (materialIds == null || materialIds.isEmpty()) {
            return List.of();
        }
        String vector = toVectorLiteral(queryEmbedding);
        List<UUID> distinctIds = materialIds.stream().distinct().toList();
        String placeholders = distinctIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(courseId);
        args.addAll(distinctIds);
        args.add(vector);
        args.add(limit);
        return jdbc.query(
                """
                        SELECT c.id, c.material_id, m.title, c.page_number, c.content
                        FROM material_chunks c
                        INNER JOIN materials m ON m.id = c.material_id
                        INNER JOIN courses co ON co.id = m.course_id
                        WHERE m.course_id = ?
                          AND m.id IN (%s)
                          AND m.visibility = 'PUBLISHED'
                          AND m.processing_status = 'READY'
                          AND co.visibility = 'PUBLISHED'
                        ORDER BY c.embedding <=> CAST(? AS vector)
                        LIMIT ?
                        """.formatted(placeholders),
                (rs, rowNum) -> mapChunk(rs),
                args.toArray()
        );
    }

    public List<ChatModel.RetrievedChunk> samplePublished(UUID courseId, int limit) {
        return samplePublished(courseId, null, limit);
    }

    public List<ChatModel.RetrievedChunk> samplePublished(UUID courseId, List<UUID> materialIds, int limit) {
        if (materialIds != null && materialIds.isEmpty()) {
            return List.of();
        }
        if (materialIds == null) {
            return jdbc.query(
                    """
                            SELECT c.id, c.material_id, m.title, c.page_number, c.content
                            FROM material_chunks c
                            INNER JOIN materials m ON m.id = c.material_id
                            INNER JOIN courses co ON co.id = m.course_id
                            WHERE m.course_id = ?
                              AND m.visibility = 'PUBLISHED'
                              AND m.processing_status = 'READY'
                              AND co.visibility = 'PUBLISHED'
                            ORDER BY m.title ASC, c.page_number ASC, c.chunk_index ASC
                            LIMIT ?
                            """,
                    (rs, rowNum) -> mapChunk(rs),
                    courseId,
                    limit
            );
        }
        String placeholders = materialIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(courseId);
        args.addAll(materialIds);
        args.add(limit);
        return jdbc.query(
                """
                        WITH ranked AS (
                            SELECT c.id,
                                   c.material_id,
                                   m.title,
                                   c.page_number,
                                   c.content,
                                   c.chunk_index,
                                   row_number() OVER (
                                       PARTITION BY m.id
                                       ORDER BY c.page_number ASC, c.chunk_index ASC
                                   ) AS material_rank
                            FROM material_chunks c
                            INNER JOIN materials m ON m.id = c.material_id
                            INNER JOIN courses co ON co.id = m.course_id
                            WHERE m.course_id = ?
                              AND m.id IN (%s)
                              AND m.visibility = 'PUBLISHED'
                              AND m.processing_status = 'READY'
                              AND co.visibility = 'PUBLISHED'
                        )
                        SELECT id, material_id, title, page_number, content
                        FROM ranked
                        ORDER BY material_rank ASC, title ASC, page_number ASC, chunk_index ASC
                        LIMIT ?
                        """.formatted(placeholders),
                (rs, rowNum) -> mapChunk(rs),
                args.toArray()
        );
    }

    public int countPublishedReadyMaterials(UUID courseId, List<UUID> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return 0;
        }
        List<UUID> distinctIds = materialIds.stream().distinct().toList();
        String placeholders = distinctIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(courseId);
        args.addAll(distinctIds);
        Integer count = jdbc.queryForObject(
                """
                        SELECT count(*)
                        FROM materials m
                        INNER JOIN courses c ON c.id = m.course_id
                        WHERE m.course_id = ?
                          AND m.id IN (%s)
                          AND m.visibility = 'PUBLISHED'
                          AND m.processing_status = 'READY'
                          AND c.visibility = 'PUBLISHED'
                        """.formatted(placeholders),
                Integer.class,
                args.toArray()
        );
        return count == null ? 0 : count;
    }

    public List<ChatModel.RetrievedChunk> representativePublished(
            UUID courseId,
            List<UUID> materialIds,
            int maxChunks,
            int characterBudget
    ) {
        if (materialIds == null || materialIds.isEmpty() || maxChunks <= 0 || characterBudget <= 0) {
            return List.of();
        }
        List<UUID> distinctIds = materialIds.stream().distinct().toList();
        String placeholders = distinctIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(courseId);
        args.addAll(distinctIds);
        List<ChatModel.RetrievedChunk> all = jdbc.query(
                """
                        SELECT c.id, c.material_id, m.title, c.page_number, c.content
                        FROM material_chunks c
                        INNER JOIN materials m ON m.id = c.material_id
                        INNER JOIN courses co ON co.id = m.course_id
                        WHERE m.course_id = ?
                          AND m.id IN (%s)
                          AND m.visibility = 'PUBLISHED'
                          AND m.processing_status = 'READY'
                          AND co.visibility = 'PUBLISHED'
                        ORDER BY m.title ASC, c.page_number ASC, c.chunk_index ASC
                        """.formatted(placeholders),
                (rs, rowNum) -> mapChunk(rs),
                args.toArray()
        );

        Map<UUID, List<ChatModel.RetrievedChunk>> byMaterial = new LinkedHashMap<>();
        for (ChatModel.RetrievedChunk chunk : all) {
            byMaterial.computeIfAbsent(chunk.materialId(), ignored -> new ArrayList<>()).add(chunk);
        }
        if (byMaterial.isEmpty()) {
            return List.of();
        }

        int targetChunks = Math.min(
                maxChunks,
                Math.max(byMaterial.size(), characterBudget / 2_500)
        );
        int quota = Math.max(1, targetChunks / byMaterial.size());
        List<List<ChatModel.RetrievedChunk>> sampledByMaterial = byMaterial.values().stream()
                .map(materialChunks -> evenlySpaced(materialChunks, quota))
                .toList();
        int candidateCount = sampledByMaterial.stream().mapToInt(List::size).sum();
        int perChunkBudget = Math.max(500, characterBudget / Math.max(1, candidateCount));
        List<ChatModel.RetrievedChunk> selected = new ArrayList<>();
        int usedCharacters = 0;
        for (int round = 0; selected.size() < maxChunks; round++) {
            boolean addedInRound = false;
            for (List<ChatModel.RetrievedChunk> materialSample : sampledByMaterial) {
                if (round >= materialSample.size() || selected.size() >= maxChunks) {
                    continue;
                }
                ChatModel.RetrievedChunk chunk = materialSample.get(round);
                int remaining = characterBudget - usedCharacters;
                if (remaining <= 0) {
                    return List.copyOf(selected);
                }
                String content = chunk.content() == null ? "" : chunk.content().trim();
                int allowed = Math.min(6_000, Math.min(perChunkBudget, remaining));
                if (content.length() > allowed) {
                    content = content.substring(0, Math.max(0, allowed - 1)).trim() + "…";
                }
                if (!content.isBlank()) {
                    selected.add(new ChatModel.RetrievedChunk(
                            chunk.chunkId(),
                            chunk.materialId(),
                            chunk.title(),
                            chunk.pageNumber(),
                            content
                    ));
                    usedCharacters += content.length();
                    addedInRound = true;
                }
            }
            if (!addedInRound) {
                break;
            }
        }
        return List.copyOf(selected);
    }

    private static List<ChatModel.RetrievedChunk> evenlySpaced(
            List<ChatModel.RetrievedChunk> chunks,
            int requested
    ) {
        if (chunks.size() <= requested) {
            return List.copyOf(chunks);
        }
        if (requested == 1) {
            return List.of(chunks.getFirst());
        }
        List<ChatModel.RetrievedChunk> selected = new ArrayList<>(requested);
        for (int i = 0; i < requested; i++) {
            int index = (int) Math.round((double) i * (chunks.size() - 1) / (requested - 1));
            selected.add(chunks.get(index));
        }
        return List.copyOf(selected);
    }

    private static ChatModel.RetrievedChunk mapChunk(ResultSet rs) throws SQLException {
        return new ChatModel.RetrievedChunk(
                rs.getObject("id", UUID.class),
                rs.getObject("material_id", UUID.class),
                rs.getString("title"),
                rs.getInt("page_number"),
                rs.getString("content")
        );
    }

    private static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 8);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}
