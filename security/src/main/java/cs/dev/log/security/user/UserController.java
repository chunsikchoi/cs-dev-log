package cs.dev.log.security.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Tag(name = "Authentication", description = "인증")
@RequestMapping(value = "/auth", produces = {MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@RestController
public class UserController {
    @Operation(summary = "Bearer 로그인", description = "ID/PW 인증을 통한 토큰 생성")
    @ApiResponse(responseCode = "201", description = "정상")
    @PostMapping(value = "/bearer", consumes = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> postBearer(@RequestBody UserDto request) {
        System.out.println(request);
        return ResponseEntity.created(URI.create(ServletUriComponentsBuilder.fromCurrentRequestUri().toUriString())).build();
    }

    @Operation(summary = "로그인", description = "ID/PW 인증을 통한 토큰 생성", security = @SecurityRequirement(name = "Basic-Auth"))
    @ApiResponse(responseCode = "201", description = "정상")
    @PostMapping(value = "/basic", consumes = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> postBasic(@RequestBody Object request) {
        System.out.println(request);
        return ResponseEntity.created(URI.create(ServletUriComponentsBuilder.fromCurrentRequestUri().toUriString())).build();
    }
}
