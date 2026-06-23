package com.mohaemukzip.mohaemukzip_be.global.client.transcript;

import com.mohaemukzip.mohaemukzip_be.global.client.transcript.dto.TranscriptSegment;
import java.util.List;

public interface TranscriptClient {
    List<TranscriptSegment> fetchTranscript(String videoId);
}
