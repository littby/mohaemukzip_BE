package com.mohaemukzip.mohaemukzip_be.global.client.transcript.dto;

import java.util.List;

public record RapidApiTranscriptResponse(
        boolean success,
        List<TranscriptSegment> transcript
) {}
