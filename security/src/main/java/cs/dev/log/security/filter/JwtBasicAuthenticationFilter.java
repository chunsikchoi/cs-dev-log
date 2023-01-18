package cs.dev.log.security.filter;

import cs.dev.log.security.provider.JwtTokenProvider;
import cs.dev.log.security.user.UserDetail;
import org.springframework.core.log.LogMessage;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationConverter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Authorization
 * - Type: Basic Auth
 * - Username: id
 * - Password: pw
 * Headers
 * - Authorization: Basic {Username:Password}
 * Document(RFC7617)
 * https://datatracker.ietf.org/doc/html/rfc7617
 */
public class JwtBasicAuthenticationFilter extends BasicAuthenticationFilter {
    private final List<String> patterns;
    private final JwtTokenProvider jwtTokenProvider;

    public JwtBasicAuthenticationFilter(AuthenticationManager authenticationManager, List<String> patterns, JwtTokenProvider jwtTokenProvider) {
        super(authenticationManager);
        this.patterns = patterns;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!patterns.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        try {
            UsernamePasswordAuthenticationToken authRequest = new BasicAuthenticationConverter().convert(request);
            if (authRequest == null) {
                this.logger.info("Did not process authentication request since failed to find username and password in Basic Authorization header");
                chain.doFilter(request, response);
                return;
            }

            String username = authRequest.getName();
            this.logger.info(LogMessage.format("Found username '%s' in Basic Authorization header", username));

            if (this.authenticationIsRequired(username)) {
                UserDetail userDetail = UserDetail.builder()
                        .username(username)
                        .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .build();

                Authentication authResult = new UsernamePasswordAuthenticationToken(userDetail, null, userDetail.getAuthorities());

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authResult);
                SecurityContextHolder.setContext(context);

                if (this.logger.isInfoEnabled()) {
                    this.logger.info(LogMessage.format("Set SecurityContextHolder to %s", authResult));
                }

                this.onSuccessfulAuthentication(request, response, authResult);
            }
        } catch (AuthenticationException e) {
            SecurityContextHolder.clearContext();
            this.logger.error("Failed to process authentication request", e);
            this.onUnsuccessfulAuthentication(request, response, e);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    protected void onSuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, Authentication authResult) throws IOException {
        String token = jwtTokenProvider.generate(authResult.getPrincipal());
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Access-Token", "Bearer " + token);
        response.getWriter().print("{}");
    }

    @Override
    protected void onUnsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("code", HttpServletResponse.SC_UNAUTHORIZED);
        responseBody.put("message", failed.getMessage());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().print(responseBody);
    }

    private boolean authenticationIsRequired(String username) {
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth == null || !existingAuth.isAuthenticated()) {
            return true;
        }
        if (existingAuth instanceof UsernamePasswordAuthenticationToken && !existingAuth.getName().equals(username)) {
            return true;
        }
        return (existingAuth instanceof AnonymousAuthenticationToken);
    }
}
