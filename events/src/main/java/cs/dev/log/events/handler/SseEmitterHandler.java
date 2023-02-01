package cs.dev.log.events.handler;

import cs.dev.log.events.dto.SseEmitterDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class SseEmitterHandler {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> caches = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String eventId, String lastEventId) {
        String emitterId = eventId + "_" + System.currentTimeMillis();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        this.emitters.put(emitterId, emitter);

        emitter.onCompletion(() -> this.emitters.remove(emitterId));

        emitter.onTimeout(() -> {
            emitter.complete();
            this.emitters.remove(emitterId);
        });

        if (!lastEventId.isEmpty()) {
            Map<String, Map<String, Object>> cacheMap = this.caches.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(eventId))
                    .filter(entry -> lastEventId.compareTo(entry.getKey()) < 0)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            cacheMap.forEach((id, data) -> {
                String name = data.keySet().stream().findFirst().orElse("lost");
                this.send(emitter, id, name, data.get(name));
                this.caches.remove(id);
            });

            if (cacheMap.keySet().size() < 1) {
                this.caches.keySet().forEach(this.caches::remove);
            }
        }

        return emitter;
    }

    public void send(SseEmitterDto request) {
        this.emitters.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(request.getId()))
                .forEach(entry -> {
                    Map<String, Object> dataMap = new LinkedHashMap<>();
                    dataMap.put(request.getName(), request.getData());
                    this.caches.put(entry.getKey(), dataMap);
                    this.send(entry.getValue(), entry.getKey(), request.getName(), request.getData());
                });
    }

    private void send(SseEmitter emitter, String id, String name, Object data) {
        try {
            SseEmitter.SseEventBuilder sseEventBuilder = SseEmitter.event()
                    .id(id)
                    .name(name)
                    .data(data, MediaType.APPLICATION_JSON);
            emitter.send(sseEventBuilder);
        } catch (IOException e) {
            this.emitters.remove(id);
            emitter.completeWithError(e);
        }
    }
}
