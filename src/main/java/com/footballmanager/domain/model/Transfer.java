package com.footballmanager.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Transfer {
    private final UUID id;
    private final PlayerId playerId;
    private final TeamId fromTeamId;
    private final TeamId toTeamId;
    private final BigDecimal offerAmount;
    private TransferStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public enum TransferStatus {
        PENDING, ACCEPTED, REJECTED, COMPLETED, CANCELLED
    }

    private Transfer(UUID id, PlayerId playerId, TeamId fromTeamId, TeamId toTeamId,
                    BigDecimal offerAmount, TransferStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Transfer ID cannot be null");
        this.playerId = Objects.requireNonNull(playerId, "PlayerId cannot be null");
        this.fromTeamId = Objects.requireNonNull(fromTeamId, "From TeamId cannot be null");
        this.toTeamId = Objects.requireNonNull(toTeamId, "To TeamId cannot be null");
        
        validateOfferAmount(offerAmount);
        validateTeams(fromTeamId, toTeamId);
        
        this.offerAmount = offerAmount;
        this.status = status != null ? status : TransferStatus.PENDING;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Transfer create(PlayerId playerId, TeamId fromTeamId, TeamId toTeamId, BigDecimal offerAmount) {
        return new Transfer(UUID.randomUUID(), playerId, fromTeamId, toTeamId, offerAmount,
                          TransferStatus.PENDING, Instant.now(), Instant.now());
    }

    public static Transfer reconstruct(UUID id, PlayerId playerId, TeamId fromTeamId, TeamId toTeamId,
                                      BigDecimal offerAmount, TransferStatus status, 
                                      Instant createdAt, Instant updatedAt) {
        return new Transfer(id, playerId, fromTeamId, toTeamId, offerAmount, 
                          status, createdAt, updatedAt);
    }

    private void validateOfferAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Offer amount cannot be negative");
        }
    }

    private void validateTeams(TeamId fromTeamId, TeamId toTeamId) {
        if (fromTeamId.equals(toTeamId)) {
            throw new IllegalArgumentException("Cannot transfer player to the same team");
        }
    }

    public void accept() {
        if (status != TransferStatus.PENDING) {
            throw new IllegalStateException("Can only accept pending transfers");
        }
        this.status = TransferStatus.ACCEPTED;
        this.updatedAt = Instant.now();
    }

    public void reject() {
        if (status != TransferStatus.PENDING) {
            throw new IllegalStateException("Can only reject pending transfers");
        }
        this.status = TransferStatus.REJECTED;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (status != TransferStatus.ACCEPTED) {
            throw new IllegalStateException("Can only complete accepted transfers");
        }
        this.status = TransferStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (status == TransferStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed transfers");
        }
        this.status = TransferStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public boolean isPending() {
        return status == TransferStatus.PENDING;
    }

    public boolean isCompleted() {
        return status == TransferStatus.COMPLETED;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public PlayerId getPlayerId() {
        return playerId;
    }

    public TeamId getFromTeamId() {
        return fromTeamId;
    }

    public TeamId getToTeamId() {
        return toTeamId;
    }

    public BigDecimal getOfferAmount() {
        return offerAmount;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transfer transfer = (Transfer) o;
        return Objects.equals(id, transfer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Transfer{id=%s, player=%s, from=%s, to=%s, amount=%s, status=%s}",
                id, playerId, fromTeamId, toTeamId, offerAmount, status);
    }
}
