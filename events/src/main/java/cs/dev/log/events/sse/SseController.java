package cs.dev.log.events.sse;

import cs.dev.log.events.dto.SseEmitterDto;
import cs.dev.log.events.handler.SseEmitterHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequestMapping(value = "/sse")
@RequiredArgsConstructor
@RestController
public class SseController {
    private final SseEmitterHandler sseEmitterHandler;

    @GetMapping(produces = {MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<SseEmitter> getSse(@RequestHeader("event-id") String eventId, @RequestHeader(value = "last-event-id", required = false, defaultValue = "") String lastEventId) {
        return ResponseEntity.ok(sseEmitterHandler.subscribe(eventId, lastEventId));
    }

    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> postSse(@RequestBody SseEmitterDto request) {
        sseEmitterHandler.send(request);
        return ResponseEntity.ok().build();
    }
}
