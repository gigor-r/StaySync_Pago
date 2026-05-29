package com.staysync.pagos.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StripeCheckoutResponse {
    private String sessionId;
    private String checkoutUrl;
    private Long reservaId;
    private String tituloReserva;
}
