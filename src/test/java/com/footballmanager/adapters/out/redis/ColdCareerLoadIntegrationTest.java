package com.footballmanager.adapters.out.redis;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.ports.out.career.CareerIndexLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ColdCareerLoadIntegrationTest extends AbstractIntegrationTest {
    @Autowired private RedisCareerRepository repository;

    private CareerSave create(UUID owner) {
        CareerSave career = new CareerSave();
        career.setUserId(owner);
        career.getData().setCareerId(UUID.randomUUID().toString());
        repository.createInitialCareer(career).block();
        return career;
    }

    @Test void coldReadRepairsAndRenewsIndexWithoutRewritingCareer() {
        UUID owner = UUID.randomUUID();
        CareerSave career = create(owner);
        String root = reactiveRedisTemplate.opsForValue().get("career:" + owner).block();
        String index = "user:" + owner + ":career-ids";
        reactiveRedisTemplate.delete(index).block();
        CareerSave loaded = repository.findById(owner.toString()).block().orElseThrow();
        assertEquals(career.getCareerId(), loaded.getCareerId());
        assertEquals(reactiveRedisTemplate.opsForValue().get("career-generation:" + career.getCareerId()).block(), loaded.getLifecycleGeneration());
        assertTrue(reactiveRedisTemplate.opsForSet().isMember(index, career.getCareerId()).block());
        assertTrue(reactiveRedisTemplate.getExpire(index).block().toDays() >= 30);
        reactiveRedisTemplate.expire(index, Duration.ofSeconds(5)).block();
        repository.findById(owner.toString()).block();
        assertTrue(reactiveRedisTemplate.getExpire(index).block().toDays() >= 30);
        assertEquals(root, reactiveRedisTemplate.opsForValue().get("career:" + owner).block());
    }

    @Test void missingGenerationFailsBeforeRepairingIndex() {
        UUID owner = UUID.randomUUID();
        CareerSave career = create(owner);
        String index = "user:" + owner + ":career-ids";
        reactiveRedisTemplate.delete("career-generation:" + career.getCareerId()).block();
        reactiveRedisTemplate.delete(index).block();
        assertThrows(IllegalStateException.class, () -> repository.findById(owner.toString()).block());
        assertFalse(reactiveRedisTemplate.hasKey(index).block());
    }

    @Test void fullIndexRejectsNewEntryWithoutChangesButAllowsExistingEntry() {
        UUID owner = UUID.randomUUID();
        CareerSave career = create(owner);
        String index = "user:" + owner + ":career-ids";
        reactiveRedisTemplate.delete(index).block();
        for (int i = 0; i < 256; i++) reactiveRedisTemplate.opsForSet().add(index, "other-" + i).block();
        assertThrows(CareerIndexLimitException.class, () -> repository.findById(owner.toString()).block());
        assertEquals(256L, reactiveRedisTemplate.opsForSet().size(index).block());
        assertFalse(reactiveRedisTemplate.opsForSet().isMember(index, career.getCareerId()).block());
        reactiveRedisTemplate.opsForSet().remove(index, "other-0").block();
        reactiveRedisTemplate.opsForSet().add(index, career.getCareerId()).block();
        assertTrue(repository.findById(owner.toString()).block().isPresent());
        assertEquals(256L, reactiveRedisTemplate.opsForSet().size(index).block());
    }
}
