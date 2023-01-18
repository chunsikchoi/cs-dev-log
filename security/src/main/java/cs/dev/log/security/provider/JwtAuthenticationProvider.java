package cs.dev.log.security.provider;

import cs.dev.log.security.user.UserDetail;
import cs.dev.log.security.user.UserService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

public class JwtAuthenticationProvider implements AuthenticationProvider {
    private final UserService userService;

    public JwtAuthenticationProvider(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) authentication;

        String username = token.getName();
        String password = (String) token.getCredentials();

        UserDetail userDetail = (UserDetail) userService.loadUserByUsername(username);

        if (!userDetail.getPassword().equalsIgnoreCase(password)) {
            throw new BadCredentialsException("Invalid password.");
        }

        return new UsernamePasswordAuthenticationToken(userDetail, null, userDetail.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
