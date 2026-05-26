package main.yuelu_trip.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String message,
        String conversationId
) {}
