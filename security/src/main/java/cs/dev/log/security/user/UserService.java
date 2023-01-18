package cs.dev.log.security.user;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UserService implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Map<String, Object> detailsMap = new LinkedHashMap<>();
        detailsMap.put("name", "관리자");
        return UserDetail.builder()
                .username(username)
                .password(username)
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .details(detailsMap)
                .build();
    }
}
