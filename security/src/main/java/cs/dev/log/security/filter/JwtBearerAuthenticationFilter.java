package cs.dev.log.security.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cs.dev.log.security.provider.JwtTokenProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/*
 * Authorization
 * - Type: Bearer Token
 * - Token: B64(Header).B64(Payload).B64(Sig)
 * Headers
 * - Authorization: Bearer {Token}
 * Document(RFC7519)
 * https://datatracker.ietf.org/doc/html/rfc7519
 */
public class JwtBearerAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtBearerAuthenticationFilter(AuthenticationManager authenticationManager, String pattern, JwtTokenProvider jwtTokenProvider) {
        this.setAuthenticationManager(authenticationManager);
        this.setRequiresAuthenticationRequestMatcher(new AntPathRequestMatcher(pattern, "POST"));
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        if (!HttpMethod.POST.matches(request.getMethod()) || !MediaType.APPLICATION_JSON_VALUE.matches(request.getContentType())) {
            throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod() + " (or) content type: " + request.getContentType());
        }

        Map<String, String> bodyMap = this.getRequestBody(request);
        String username = bodyMap.get(USERNAME_KEY);
        String password = bodyMap.get(PASSWORD_KEY);

        UsernamePasswordAuthenticationToken authRequest = UsernamePasswordAuthenticationToken.unauthenticated(username, password);
        authRequest.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return this.getAuthenticationManager().authenticate(authRequest);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        String token = jwtTokenProvider.generate(authResult.getPrincipal());
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Access-Token", "Bearer " + token);
        response.getWriter().print("{}");
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("code", HttpServletResponse.SC_UNAUTHORIZED);
        responseBody.put("message", failed.getMessage());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().print(responseBody);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String token = jwtTokenProvider.resolve(httpRequest);

        if (!token.isBlank() && jwtTokenProvider.validate(token)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        super.doFilter(request, response, chain);
    }

    private Map<String, String> getRequestBody(HttpServletRequest request) {
        try {
            return new ObjectMapper().readValue(request.getInputStream(), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new AuthenticationServiceException("Authentication body is empty.");
        }
    }
}
