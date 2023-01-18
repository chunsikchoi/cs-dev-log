package cs.dev.log.security.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.jsonwebtoken.*;
import lombok.SneakyThrows;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {
    private static final String ISSUER = "cschoi";
    private static final String SECRET = "web-develop";
    private static final Long EXPIRATION = 3600000L;
    private static final String HEADER_NAME = "Authorization";
    private static final String HEADER_VALUES = "Bearer ";

    @SneakyThrows
    public String generate(Object principal) {
        Map<String, Object> claims = this.convertObject(principal);
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + EXPIRATION);
        return Jwts.builder()
                .setClaims(claims)
                .setIssuer(ISSUER)
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .signWith(SignatureAlgorithm.HS256, SECRET)
                .compact();
    }

    public String resolve(HttpServletRequest request) {
        String header = request.getHeader(HEADER_NAME);
        if (header != null && header.startsWith(HEADER_VALUES)) {
            return header.substring(7);
        }
        return "";
    }

    public String refresh(String token) {
        if (this.getClaims(token, Claims::getExpiration).getTime() <= EXPIRATION) {
            return this.generate(getClaims(token));
        }
        return "";
    }

    public Boolean validate(String token) {
        try {
            return this.getClaims(token, Claims::getExpiration).after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        Map<String, Object> claimsMap = this.getClaimsToMap(token);
        List<Map<String, Object>> authorities = (List<Map<String, Object>>) claimsMap.get("authorities");
        String authority = authorities.get(0).get("authority").toString();
        return new UsernamePasswordAuthenticationToken(
                claimsMap,
                null,
                Collections.singletonList(new SimpleGrantedAuthority(authority))
        );
    }

    public Map<String, Object> getClaimsToMap(String token) {
        return this.getClaims(token).entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private <T> T getClaims(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(this.getClaims(token));
    }

    private Claims getClaims(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(SECRET)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (SecurityException | MalformedJwtException e) {
            throw new RuntimeException("Malformed json web token exception.");
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Expired json web token exception.");
        } catch (UnsupportedJwtException e) {
            throw new RuntimeException("Unsupported json web token exception.");
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("IllegalArgument json web token exception.");
        }
    }

    private <T> T convertObject(Object fromValue) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"));
        LocalDateTimeSerializer localDateTimeSerializer = new LocalDateTimeSerializer(dateTimeFormatter);
        LocalDateTimeDeserializer localDateTimeDeserializer = new LocalDateTimeDeserializer(dateTimeFormatter);

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, localDateTimeSerializer);
        javaTimeModule.addDeserializer(LocalDateTime.class, localDateTimeDeserializer);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(javaTimeModule);
        objectMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return objectMapper.convertValue(fromValue, new TypeReference<T>() {
        });
    }
}
