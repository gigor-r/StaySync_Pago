package com.staysync.pagos.service;

import com.staysync.pagos.dto.request.ProcesarPagoRequest;
import com.staysync.pagos.dto.response.PagoResponse;
import com.staysync.pagos.exception.PagoFallidoException;
import com.staysync.pagos.exception.PagoNotFoundException;
import com.staysync.pagos.model.Pago;
import com.staysync.pagos.model.Pago.EstadoPago;
import com.staysync.pagos.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PagoService {

    private final PagoRepository pagoRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;

    @Value("${services.reservas.url}")
    private String reservasUrl;

    private static final String EXCHANGE_PAGOS = "staysync.pagos.exchange";

    @Transactional
    public PagoResponse procesar(ProcesarPagoRequest request) {
        String referencia = generarReferencia();

        Pago pago = Pago.builder()
                .referencia(referencia)
                .reservaId(request.getReservaId())
                .usuarioId(request.getUsuarioId())
                .monto(request.getMonto())
                .moneda(request.getMoneda() != null ? request.getMoneda() : "USD")
                .metodoPago(request.getMetodoPago())
                .descripcion(request.getDescripcion())
                .estado(EstadoPago.PROCESANDO)
                .build();

        pago = pagoRepository.save(pago);

        // Simular llamada al gateway de pago externo
        boolean pagoExitoso = procesarConGateway(request.getGatewayToken(), request.getMonto());

        if (pagoExitoso) {
            pago.setEstado(EstadoPago.COMPLETADO);
            pago.setGatewayId("GW-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
            pagoRepository.save(pago);
            confirmarReserva(request.getReservaId());
            publicarEvento("pago.completado", buildEvento(pago));
            log.info("Pago completado: {}", pago.getReferencia());
        } else {
            pago.setEstado(EstadoPago.FALLIDO);
            pagoRepository.save(pago);
            publicarEvento("pago.fallido", Map.of(
                    "pagoId",    pago.getId(),
                    "reservaId", pago.getReservaId(),
                    "motivo",    "Rechazado por gateway"
            ));
            throw new PagoFallidoException("El gateway de pago rechazó la transacción");
        }

        return toResponse(pago);
    }

    public PagoResponse obtenerPorId(Long id) {
        return toResponse(pagoRepository.findById(id)
                .orElseThrow(() -> new PagoNotFoundException(id)));
    }

    public PagoResponse obtenerPorReferencia(String referencia) {
        return toResponse(pagoRepository.findByReferencia(referencia)
                .orElseThrow(() -> new PagoNotFoundException(referencia)));
    }

    public List<PagoResponse> obtenerPorReserva(Long reservaId) {
        return pagoRepository.findByReservaId(reservaId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public PagoResponse solicitarReembolso(Long pagoId) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new PagoNotFoundException(pagoId));
        if (pago.getEstado() != Pago.EstadoPago.COMPLETADO) {
            throw new PagoFallidoException(
                    "Solo se pueden reembolsar pagos COMPLETADOS. Estado actual: " + pago.getEstado());
        }
        pago.setEstado(Pago.EstadoPago.REEMBOLSADO);
        pagoRepository.save(pago);
        publicarEvento("pago.reembolsado", Map.of(
                "pagoId",    pago.getId(),
                "reservaId", pago.getReservaId(),
                "usuarioId", pago.getUsuarioId(),
                "monto",     pago.getMonto()
        ));
        log.info("Reembolso procesado: {}", pago.getReferencia());
        return toResponse(pago);
    }

    private boolean procesarConGateway(String token, java.math.BigDecimal monto) {
        // Simulación local: siempre aprueba el pago
        return true;
    }

    private String generarReferencia() {
        String ref;
        do {
            ref = "PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        } while (pagoRepository.existsByReferencia(ref));
        return ref;
    }

    private void confirmarReserva(Long reservaId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("estado", "CONFIRMADA"), headers);
            restTemplate.exchange(
                    reservasUrl + "/api/v1/reservas/" + reservaId + "/estado",
                    HttpMethod.PATCH,
                    entity,
                    Object.class);
            log.info("Reserva {} confirmada tras pago exitoso", reservaId);
        } catch (Exception e) {
            log.error("No se pudo confirmar la reserva {}: {}", reservaId, e.getMessage());
        }
    }

    private void publicarEvento(String routingKey, Map<String, Object> payload) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE_PAGOS, routingKey, payload);
            log.info("Evento publicado: {} -> {}", EXCHANGE_PAGOS, routingKey);
        } catch (Exception e) {
            log.error("Error publicando evento de pago: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildEvento(Pago p) {
        return Map.of(
                "pagoId",    p.getId(),
                "reservaId", p.getReservaId(),
                "usuarioId", p.getUsuarioId(),
                "monto",     p.getMonto(),
                "moneda",    p.getMoneda(),
                "referencia",p.getReferencia()
        );
    }

    private PagoResponse toResponse(Pago p) {
        return PagoResponse.builder()
                .id(p.getId()).referencia(p.getReferencia())
                .reservaId(p.getReservaId()).usuarioId(p.getUsuarioId())
                .monto(p.getMonto()).moneda(p.getMoneda())
                .metodoPago(p.getMetodoPago()).estado(p.getEstado())
                .gatewayId(p.getGatewayId()).descripcion(p.getDescripcion())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
                .build();
    }
}
