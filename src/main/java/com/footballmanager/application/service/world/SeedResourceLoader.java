package com.footballmanager.application.service.world;

import java.io.IOException;
import java.io.InputStream;

public interface SeedResourceLoader {

    InputStream open(String resourcePath) throws IOException;
}
