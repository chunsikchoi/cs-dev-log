package cs.dev.log.webflux.handler;

import cs.dev.log.webflux.dto.WebfluxDto;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class WebfluxHandler {
    private final ReactiveHashOperations<String, String, Object> reactiveHashOperations;

    public WebfluxHandler(ReactiveRedisOperations<String, Object> reactiveRedisOperations) {
        this.reactiveHashOperations = reactiveRedisOperations.opsForHash();
    }

    public Mono<ServerResponse> save(ServerRequest request) {
        return request.bodyToMono(WebfluxDto.class)
                .flatMap(dto -> {
                    reactiveHashOperations.put(dto.getId(), dto.getName(), dto).subscribe();
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(reactiveHashOperations.values(dto.getId()), Object.class);
                });
    }
}
