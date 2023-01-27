package cs.dev.log.webflux.router;

import cs.dev.log.webflux.handler.WebfluxHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class WebfluxRouter {
    private final WebfluxHandler webfluxHandler;

    public WebfluxRouter(WebfluxHandler webfluxHandler) {
        this.webfluxHandler = webfluxHandler;
    }

    @Bean
    public RouterFunction<ServerResponse> routerFunction() {
        return RouterFunctions.route()
                .POST("/webflux", webfluxHandler::save)
                .build();
    }
}
