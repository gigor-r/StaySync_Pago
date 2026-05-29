package com.staysync.pagos.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@Getter
public class StripeConfig {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.back-urls.success}")
    private String successUrl;

    @Value("${stripe.back-urls.cancel}")
    private String cancelUrl;

    @PostConstruct
    public void init() {
        if (secretKey.isBlank() || secretKey.startsWith("sk_test_PEGA")) {
            log.error("STRIPE: secret-key no configurado. Ve a dashboard.stripe.com → Developers → API keys.");
            return;
        }
        Stripe.apiKey = secretKey;
        log.info("Stripe SDK inicializado — modo {}", secretKey.startsWith("sk_test_") ? "TEST" : "PRODUCCIÓN");
    }
}
