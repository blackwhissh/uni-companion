package com.unicompanion.learning.study.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "flashcards")
public class FlashcardEntity {

    @Id
    private UUID id;

    @Column(name = "deck_id", nullable = false)
    private UUID deckId;

    @Column(nullable = false, length = 500)
    private String front;

    @Column(nullable = false, columnDefinition = "text")
    private String back;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected FlashcardEntity() {
    }

    public static FlashcardEntity create(UUID id, UUID deckId, String front, String back, int sortOrder) {
        FlashcardEntity card = new FlashcardEntity();
        card.id = id;
        card.deckId = deckId;
        card.front = front;
        card.back = back;
        card.sortOrder = sortOrder;
        return card;
    }

    public UUID getId() {
        return id;
    }

    public String getFront() {
        return front;
    }

    public String getBack() {
        return back;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
