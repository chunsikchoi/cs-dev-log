package cs.dev.log.events.controller;

import cs.dev.log.events.dto.SseEmitterDto;
import cs.dev.log.events.handler.SseEmitterHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequestMapping(value = "/events")
@RequiredArgsConstructor
@RestController
public class EventsController {
    private final SseEmitterHandler sseEmitterHandler;

    @GetMapping(produces = {MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<SseEmitter> get(@RequestHeader("event-id") String eventId, @RequestHeader(value = "last-event-id", required = false, defaultValue = "") String lastEventId) {
        return ResponseEntity.ok(sseEmitterHandler.subscribe(eventId, lastEventId));
    }

    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> post(@RequestBody SseEmitterDto request) {
        sseEmitterHandler.send(request);
        return ResponseEntity.ok().build();
    }
}
