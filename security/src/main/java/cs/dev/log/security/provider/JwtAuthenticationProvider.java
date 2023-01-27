package cs.dev.log.security.provider;

import cs.dev.log.security.auth.AuthDetails;
import cs.dev.log.security.auth.AuthDetailsService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

public class JwtAuthenticationProvider implements AuthenticationProvider {
    private final AuthDetailsService authDetailsService;

    public JwtAuthenticationProvider(AuthDetailsService authDetailsService) {
        this.authDetailsService = authDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) authentication;

        String username = token.getName();
        String password = (String) token.getCredentials();

        AuthDetails authDetails = (AuthDetails) authDetailsService.loadUserByUsername(username);

        if (!authDetails.getPassword().equalsIgnoreCase(password)) {
            throw new BadCredentialsException("Invalid password.");
        }

        return new UsernamePasswordAuthenticationToken(authDetails, null, authDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
