package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "security.shop")
public class ShopAdminProperties {

    private Set<Long> adminUserIds = new LinkedHashSet<>();

    public boolean canWrite(Long userId) {
        return userId != null && adminUserIds.contains(userId);
    }
}
