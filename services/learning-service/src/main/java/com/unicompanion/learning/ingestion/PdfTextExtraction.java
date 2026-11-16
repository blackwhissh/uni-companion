package com.unicompanion.learning.ingestion;

import com.unicompanion.learning.config.IngestProperties;
import com.unicompanion.learning.config.ObservabilitySupport;
import com.unicompanion.learning.material.infrastructure.LocalPdfStorage;
import com.unicompanion.learning.material.infrastructure.MaterialChunkEntity;
import com.unicompanion.learning.material.infrastructure.MaterialChunkRepository;
import com.unicompanion.learning.material.infrastructure.MaterialEntity;
import com.unicompanion.learning.material.infrastructure.MaterialRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PdfTextExtraction {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtraction.class);

    private final MaterialRepository materials;
    private final MaterialChunkRepository chunks;
    private final LocalPdfStorage storage;
    private final PdfTextReader reader;
    private final EmbeddingModel embeddings;
    private final IngestProperties ingest;
    private final ObservationRegistry observations;
    private final PdfTextExtraction self;

    public PdfTextExtraction(
            MaterialRepository materials,
            MaterialChunkRepository chunks,
            LocalPdfStorage storage,
            PdfTextReader reader,
            EmbeddingModel embeddings,
            IngestProperties ingest,
            ObservationRegistry observations,
            @org.springframework.context.annotation.Lazy PdfTextExtraction self
    ) {
        this.materials = materials;
        this.chunks = chunks;
        this.storage = storage;
        this.reader = reader;
        this.embeddings = embeddings;
        this.ingest = ingest;
        this.observations = observations;
        this.self = self;
    }

    /**
     * Kick off indexing. Production runs on {@code ingestExecutor}; tests can set
     * {@code unicompanion.ingest.async=false} to finish before the HTTP response returns.
     */
    public void schedule(UUID materialId) {
        if (ingest.async()) {
            MaterialEntity material = materials.findById(materialId).orElseThrow();
            material.markProcessing(Instant.now());
            materials.save(material);
            log.info("Scheduling async PDF ingest material={}", materialId);
            self.extractAsync(materialId);
            return;
        }
        // Must go through the Spring proxy so @Transactional on extract() applies.
        self.extract(materialId);
    }

    @Async("ingestExecutor")
    public void extractAsync(UUID materialId) {
        extract(materialId);
    }

    @Transactional
    public void extract(UUID materialId) {
        Observation observation = ObservabilitySupport.start(observations, "learning.pdf.ingest");
        observation.lowCardinalityKeyValue("embedding.model", embeddings.getClass().getSimpleName());
        try {
            extractObserved(materialId, observation);
        } catch (RuntimeException ex) {
            observation.error(ex);
            throw ex;
        } finally {
            observation.stop();
        }
    }

    private void extractObserved(UUID materialId, Observation observation) {
        MaterialEntity material = materials.findById(materialId).orElseThrow();
        material.markProcessing(Instant.now());
        log.info("Ingest started material={} embedding={}", materialId, embeddings.getClass().getSimpleName());
        try {
            List<PdfTextReader.PageText> pages = reader.read(storage.read(material.getStoragePath()));
            if (pages.isEmpty()) {
                material.markFailed("The PDF has no extractable text.", Instant.now());
                observation.lowCardinalityKeyValue("status", "FAILED");
                log.warn("Ingest failed material={}: no extractable text", materialId);
                return;
            }
            chunks.deleteByMaterialId(materialId);
            List<String> pageTexts = pages.stream().map(PdfTextReader.PageText::text).toList();
            List<float[]> vectors = embeddings.embedAll(pageTexts);
            if (vectors.size() != pages.size()) {
                chunks.deleteByMaterialId(materialId);
                material.markFailed("The PDF text could not be embedded.", Instant.now());
                observation.lowCardinalityKeyValue("status", "FAILED");
                log.warn("Ingest failed material={}: embedding count {} != pages {}", materialId, vectors.size(), pages.size());
                return;
            }
            int index = 0;
            for (int i = 0; i < pages.size(); i++) {
                float[] embedding = vectors.get(i);
                PdfTextReader.PageText page = pages.get(i);
                if (embedding == null || embedding.length != EmbeddingModel.DIMENSIONS) {
                    chunks.deleteByMaterialId(materialId);
                    material.markFailed("The PDF text could not be embedded.", Instant.now());
                    observation.lowCardinalityKeyValue("status", "FAILED");
                    log.warn("Ingest failed material={}: embedding size invalid on page={}", materialId, page.pageNumber());
                    return;
                }
                chunks.save(MaterialChunkEntity.create(
                        UUID.randomUUID(),
                        materialId,
                        page.pageNumber(),
                        index++,
                        page.text(),
                        embedding
                ));
            }
            material.markReady(Instant.now());
            observation.lowCardinalityKeyValue("status", "READY");
            observation.highCardinalityKeyValue("pages", String.valueOf(pages.size()));
            log.info("Ingest ready material={} pages={} chunks={}", materialId, pages.size(), index);
        } catch (EmbeddingException ex) {
            observation.error(ex);
            observation.lowCardinalityKeyValue("status", "FAILED");
            log.warn("PDF embedding failed for material {}: {}", materialId, ex.getMessage(), ex);
            chunks.deleteByMaterialId(materialId);
            material.markFailed(ex.getMessage(), Instant.now());
        } catch (RuntimeException | IOException ex) {
            observation.error(ex);
            observation.lowCardinalityKeyValue("status", "FAILED");
            log.warn("PDF extraction failed for material {}", materialId, ex);
            chunks.deleteByMaterialId(materialId);
            material.markFailed(failureReason(ex), Instant.now());
        }
    }

    static String failureReason(Throwable ex) {
        if (QuotaAwareRetry.isRetryable(ex)) {
            return EmbeddingException.quota(ex).getMessage();
        }
        return "The PDF could not be read.";
    }
}
