package cs.dev.log.security.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AuthDto {
    @Schema(example = "admin")
    private String username;
    @Schema(example = "admin")
    private String password;
}
