package com.footballmanager.infrastructure.persistence.entity;

import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.valueobject.PlayerId;
import com.footballmanager.domain.model.entity.PlayerAttributes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("players")
public class PlayerEntity {
    @Id
    private UUID id;
    private String name;
    private int age;
    private String position;
    private int attack;
    private int defense;
    private int technique;
    private int speed;
    private int stamina;
    private int mentality;
    private BigDecimal marketValue;
    private int energy;
    private boolean injured;
    private Instant createdAt;
    private Instant updatedAt;

    public static PlayerEntity fromDomain(Player player) {
        PlayerAttributes attrs = player.getAttributes();
        return new PlayerEntity(
            player.getId().getValue(),
            player.getName(),
            player.getAge(),
            player.getPosition().name(), // Position now matches GK, LB, CB, etc.
            attrs.getAttack(),
            attrs.getDefense(),
            attrs.getTechnique(),
            attrs.getSpeed(),
            attrs.getStamina(),
            attrs.getMentality(),
            player.getMarketValue(),
            player.getEnergy(),
            player.isInjured(),
            player.getCreatedAt(),
            player.getUpdatedAt()
        );
    }

    public static PlayerEntity fromDomainForInsert(Player player) {
        PlayerAttributes attrs = player.getAttributes();
        return new PlayerEntity(
            player.getId() != null ? player.getId().getValue() : null,
            player.getName(),
            player.getAge(),
            player.getPosition().name(), // Position now matches GK, LB, CB, etc.
            attrs.getAttack(),
            attrs.getDefense(),
            attrs.getTechnique(),
            attrs.getSpeed(),
            attrs.getStamina(),
            attrs.getMentality(),
            player.getMarketValue(),
            player.getEnergy(),
            player.isInjured(),
            player.getCreatedAt(),
            player.getUpdatedAt()
        );
    }

    public Player toDomain() {
        PlayerAttributes attributes = PlayerAttributes.of(
            attack, defense, technique, speed, stamina, mentality
        );
        // injuryState basado en el boolean injured
        Player.InjuryState injuryState = injured
            ? Player.InjuryState.INJURED_SERIOUS
            : Player.InjuryState.HEALTHY;

        return Player.reconstruct(
            PlayerId.of(id),
            name,
            age,
            Player.Position.valueOf(position),
            attributes,
            marketValue,
            energy,
            injuryState,
            injured,
            createdAt,
            updatedAt
        );
    }
}


