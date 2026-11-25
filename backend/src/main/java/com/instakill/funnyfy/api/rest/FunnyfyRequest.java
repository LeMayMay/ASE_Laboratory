package com.instakill.funnyfy.api.rest;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FunnyfyRequest(
        @JsonAlias({"content", "caption"})
        @NotBlank(message = "Text must not be blank")
        @Size(max = 1000, message = "Text must be at most 1000 characters")
        String text
) {
}
