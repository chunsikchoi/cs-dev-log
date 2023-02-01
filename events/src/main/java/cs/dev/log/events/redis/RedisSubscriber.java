package cs.dev.log.events.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import cs.dev.log.events.dto.SseEmitterDto;
import cs.dev.log.events.handler.SseEmitterHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@RequiredArgsConstructor
@Service
public class RedisSubscriber implements MessageListener {
    private final SseEmitterHandler sseEmitterHandler;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        SseEmitterDto sseEmitterDto = this.getBody(message.getBody(), SseEmitterDto.class);
        sseEmitterHandler.send(sseEmitterDto);
    }

    public <T> T getBody(byte[] src, Class<T> valueType) {
        try {
            return new ObjectMapper().readValue(src, valueType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
