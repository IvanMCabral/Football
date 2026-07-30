package com.footballmanager.infrastructure.world.legacyseed;

import com.footballmanager.application.service.world.SeedResourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ClasspathSeedResourceLoader implements SeedResourceLoader {

    @Override
    public InputStream open(String resourcePath) throws IOException {
        return new ClassPathResource(resourcePath).getInputStream();
    }
}
