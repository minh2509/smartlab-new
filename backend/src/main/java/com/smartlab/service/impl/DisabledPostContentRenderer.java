package com.smartlab.service.impl;

import com.smartlab.service.PostContentRenderer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class DisabledPostContentRenderer implements PostContentRenderer {

    @Override
    public Optional<String> renderAndSanitize(Map<String, Object> contentJson) {
        return Optional.empty();
    }
}
