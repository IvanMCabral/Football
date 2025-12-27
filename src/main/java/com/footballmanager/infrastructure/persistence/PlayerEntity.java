package com.footballmanager.infrastructure.persistence;

import com.footballmanager.domain.model.Player;
import com.footballmanager.domain.model.PlayerId;
import com.footballmanager.domain.model.PlayerAttributes;
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
                player.getPosition().name(),
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
        return Player.reconstruct(
                PlayerId.of(id),
                name,
                age,
                Player.Position.valueOf(position),
                attributes,
                marketValue,
                energy,
                injured,
                createdAt,
                updatedAt
        );
    }
}
