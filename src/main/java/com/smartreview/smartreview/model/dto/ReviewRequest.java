package com.smartreview.smartreview.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ReviewRequest {

    @NotBlank(message = "Repository URL is required")
    @Pattern(
            regexp = "https?://.*\\.git|https?://github\\.com/.*|https?://gitlab\\.com/.*|https?://bitbucket\\.org/.*",
            message = "Please provide a valid Git repository URL"
    )
    private String repoUrl;

    @NotBlank(message = "Provider is required")
    private String provider;

    private String apiKey;
}
