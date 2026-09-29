package com.unicompanion.learning.material.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MaterialChunkRepository extends JpaRepository<MaterialChunkEntity, UUID> {

    void deleteByMaterialId(UUID materialId);
}
