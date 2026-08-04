package com.smartreview.smartreview.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ReviewProgressService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String jobId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitters.put(jobId, emitter);
        emitter.onCompletion(() -> emitters.remove(jobId));
        emitter.onTimeout(() -> emitters.remove(jobId));
        emitter.onError(e -> emitters.remove(jobId));
        return emitter;
    }

    public void sendProgress(String jobId, int processed, int total, String currentFile, String status) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter == null) return;

        try {
            Map<String, Object> data = Map.of(
                    "processed", processed,
                    "total", total,
                    "currentFile", currentFile,
                    "status", status,
                    "percent", total > 0 ? (processed * 100 / total) : 0
            );
            emitter.send(SseEmitter.event().name("progress").data(data));
        } catch (IOException e) {
            emitters.remove(jobId);
        }
    }

    public void sendComplete(String jobId) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("complete").data("done"));
            emitter.complete();
        } catch (IOException e) {
            emitters.remove(jobId);
        }
    }

    public void sendError(String jobId, String message) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
            emitter.complete();
        } catch (IOException e) {
            emitters.remove(jobId);
        }
    }
}