package com.unicompanion.learning.ingestion;

import com.unicompanion.learning.material.infrastructure.LocalPdfStorage;
import com.unicompanion.learning.material.infrastructure.MaterialChunkRepository;
import com.unicompanion.learning.material.infrastructure.MaterialEntity;
import com.unicompanion.learning.material.infrastructure.MaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfTextExtractionTest {

    @Mock
    private MaterialRepository materials;
    @Mock
    private MaterialChunkRepository chunks;
    @Mock
    private LocalPdfStorage storage;
    @Mock
    private PdfTextReader reader;
    @Mock
    private EmbeddingModel embeddings;

    @Test
    void reportsQuotaErrorsInsteadOfGenericReadFailure() throws Exception {
        UUID materialId = UUID.randomUUID();
        MaterialEntity material = MaterialEntity.create(
                materialId,
                UUID.randomUUID(),
                "Raft",
                "raft.pdf",
                "raft.pdf",
                java.time.Instant.parse("2026-09-29T00:00:00Z")
        );
        when(materials.findById(materialId)).thenReturn(Optional.of(material));
        when(storage.read("raft.pdf")).thenReturn(new byte[]{1, 2, 3});
        when(reader.read(any())).thenReturn(List.of(new PdfTextReader.PageText(1, "Raft elects a leader.")));
        doThrow(EmbeddingException.quota(tooManyRequests())).when(embeddings).embedAll(any());

        new PdfTextExtraction(materials, chunks, storage, reader, embeddings).extract(materialId);

        assertThat(material.getProcessingStatus().name()).isEqualTo("FAILED");
        assertThat(material.getFailureReason()).contains("quota");
        verify(chunks, times(2)).deleteByMaterialId(materialId);
        verify(chunks, never()).save(any());
    }

    @Test
    void embedsPagesInOneBatch() throws Exception {
        UUID materialId = UUID.randomUUID();
        MaterialEntity material = MaterialEntity.create(
                materialId,
                UUID.randomUUID(),
                "Raft",
                "raft.pdf",
                "raft.pdf",
                java.time.Instant.parse("2026-09-29T00:00:00Z")
        );
        float[] vector = new float[EmbeddingModel.DIMENSIONS];
        when(materials.findById(materialId)).thenReturn(Optional.of(material));
        when(storage.read("raft.pdf")).thenReturn(new byte[]{1});
        when(reader.read(any())).thenReturn(List.of(
                new PdfTextReader.PageText(1, "page one"),
                new PdfTextReader.PageText(2, "page two")
        ));
        when(embeddings.embedAll(List.of("page one", "page two"))).thenReturn(List.of(vector, vector));

        new PdfTextExtraction(materials, chunks, storage, reader, embeddings).extract(materialId);

        assertThat(material.getProcessingStatus().name()).isEqualTo("READY");
        verify(embeddings).embedAll(List.of("page one", "page two"));
        verify(chunks, times(2)).save(any());
    }

    @Test
    void mapsRawTooManyRequestsToQuotaMessage() {
        assertThat(PdfTextExtraction.failureReason(tooManyRequests())).contains("quota");
        assertThat(PdfTextExtraction.failureReason(new IllegalStateException("corrupt")))
                .isEqualTo("The PDF could not be read.");
    }

    private static HttpClientErrorException tooManyRequests() {
        return HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                null,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }
}
