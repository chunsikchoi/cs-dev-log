package cs.dev.log.security.config;

import cs.dev.log.security.filter.JwtBasicAuthenticationFilter;
import cs.dev.log.security.filter.JwtBearerAuthenticationFilter;
import cs.dev.log.security.provider.JwtAuthenticationProvider;
import cs.dev.log.security.provider.JwtTokenProvider;
import cs.dev.log.security.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.time.Duration;
import java.util.List;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
@Configuration
public class WebSecurityConfig {
    private static final String BEARER_REQUEST_URL = "/auth/bearer";
    private static final List<String> BASIC_REQUEST_URL = List.of("/auth/basic");
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations())
                .antMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/favicon.ico"
                )
                .mvcMatchers(HttpMethod.OPTIONS, "/**");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf().disable();
        http.formLogin().disable();
        http.logout().disable();
        http.httpBasic().disable();

        http.authorizeHttpRequests(auth -> auth
                .antMatchers("/error/**").permitAll()
                .anyRequest().authenticated()
        );

        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        http.addFilterBefore(this.jwtBearerAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(this.jwtBasicAuthenticationFilter(), BasicAuthenticationFilter.class);
        http.addFilter(this.corsFilter());

        return http.build();
    }

    @Bean
    public JwtBearerAuthenticationFilter jwtBearerAuthenticationFilter() {
        return new JwtBearerAuthenticationFilter(this.authenticationManager(), BEARER_REQUEST_URL, jwtTokenProvider);
    }

    @Bean
    public JwtBasicAuthenticationFilter jwtBasicAuthenticationFilter() {
        return new JwtBasicAuthenticationFilter(this.authenticationManager(), BASIC_REQUEST_URL, jwtTokenProvider);
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(new JwtAuthenticationProvider(userService));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.addAllowedOriginPattern("*");
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setMaxAge(Duration.ofDays(3600));
        configuration.setExposedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsFilter(source);
    }
}
