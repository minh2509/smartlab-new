package com.smartlab.service;

import java.util.Map;
import java.util.Optional;

public interface PostContentRenderer {

    /**
     * Returns server-generated, sanitized HTML derived only from canonical
     * contentJson. Optional.empty() means rendering is currently unavailable.
     *
     * Implementations must never trust client-submitted HTML.
     */
    Optional<String> renderAndSanitize(Map<String, Object> contentJson);
}
