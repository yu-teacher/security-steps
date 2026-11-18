package com.kdt.steps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostWriteRequest(
        @NotBlank String loginId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String body
) {
}
