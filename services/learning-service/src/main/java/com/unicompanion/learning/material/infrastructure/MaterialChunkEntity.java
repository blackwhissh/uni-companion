package com.unicompanion.learning.material.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "material_chunks")
public class MaterialChunkEntity {

    @Id
    private UUID id;

    @Column(name = "material_id", nullable = false)
    private UUID materialId;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 768)
    private float[] embedding;

    protected MaterialChunkEntity() {
    }

    public static MaterialChunkEntity create(
            UUID id,
            UUID materialId,
            int pageNumber,
            int chunkIndex,
            String content,
            float[] embedding
    ) {
        MaterialChunkEntity chunk = new MaterialChunkEntity();
        chunk.id = id;
        chunk.materialId = materialId;
        chunk.pageNumber = pageNumber;
        chunk.chunkIndex = chunkIndex;
        chunk.content = content;
        chunk.embedding = embedding;
        return chunk;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }
}
