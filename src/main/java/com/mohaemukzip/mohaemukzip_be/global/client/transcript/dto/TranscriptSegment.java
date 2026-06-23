package com.mohaemukzip.mohaemukzip_be.global.client.transcript.dto;

public record TranscriptSegment(
        String text,
        Double offset,
        Double duration
) {}
