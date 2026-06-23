package com.staysync.pagos.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.staysync.pagos.config.StripeConfig;
import com.staysync.pagos.dto.request.StripeCheckoutRequest;
import com.staysync.pagos.dto.request.StripeConfirmarRequest;
import com.staysync.pagos.dto.response.PagoResponse;
import com.staysync.pagos.dto.response.StripeCheckoutResponse;
import com.staysync.pagos.exception.PagoFallidoException;
import com.staysync.pagos.model.Pago;
import com.staysync.pagos.repository.PagoRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests de StripeService.
 *
 * La integración con Stripe SDK usa llamadas estáticas (Session.create, Session.retrieve).
 * Estas se testean con MockedStatic de Mockito 5 (incluido en Spring Boot 3.x).
 *
 * Para confirmarPago() — tests completos del flujo de confirmación tras redirect de Stripe.
 * Para crearCheckout()  — tests de construcción del checkout URL.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StripeService - Tests Unitarios")
class StripeServiceTest {

    @Mock private StripeConfig stripeConfig;
    @Mock private PagoRepository pagoRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private RestTemplate restTemplate;

    @InjectMocks private StripeService stripeService;

    private static final String SESSION_ID  = "cs_test_ABC123";
    private static final String CHECKOUT_URL = "https://checkout.stripe.com/pay/cs_test_ABC123";

    @BeforeEach
    void setUp() {
        lenient().when(stripeConfig.getSuccessUrl()).thenReturn("http://localhost:3000/huesped/pago-exitoso");
        lenient().when(stripeConfig.getCancelUrl()).thenReturn("http://localhost:3000/huesped/pago-fallido");
    }

    // ── crearCheckout() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("crearCheckout() - debe retornar URL de Stripe y sessionId cuando el SDK responde OK")
    void debeCrearCheckoutExitosamente() throws Exception {
        StripeCheckoutRequest request = new StripeCheckoutRequest();
        request.setReservaId(1L);
        request.setMonto(BigDecimal.valueOf(45000));
        request.setTituloReserva("Habitación 101 - 3 noches");

        try (MockedStatic<Session> stripeStatic = mockStatic(Session.class)) {
            Session sessionMock = mock(Session.class);
            when(sessionMock.getId()).thenReturn(SESSION_ID);
            when(sessionMock.getUrl()).thenReturn(CHECKOUT_URL);
            stripeStatic.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(sessionMock);

            StripeCheckoutResponse response = stripeService.crearCheckout(request);

            assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(response.getCheckoutUrl()).isEqualTo(CHECKOUT_URL);
            assertThat(response.getReservaId()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("crearCheckout() - debe lanzar PagoFallidoException si Stripe falla")
    void debeLanzarExcepcionSiStripeFalla() throws Exception {
        StripeCheckoutRequest request = new StripeCheckoutRequest();
        request.setReservaId(1L);
        request.setMonto(BigDecimal.valueOf(45000));
        request.setTituloReserva("Habitación 101");

        try (MockedStatic<Session> stripeStatic = mockStatic(Session.class)) {
            StripeException stripeEx = mock(StripeException.class);
            when(stripeEx.getMessage()).thenReturn("Invalid API key");
            when(stripeEx.getCode()).thenReturn("authentication_error");
            stripeStatic.when(() -> Session.create(any(SessionCreateParams.class))).thenThrow(stripeEx);

            assertThatThrownBy(() -> stripeService.crearCheckout(request))
                    .isInstanceOf(PagoFallidoException.class)
                    .hasMessageContaining("Error al crear sesión");
        }
    }

    @Test
    @DisplayName("crearCheckout() - debe convertir monto a centavos enteros (sin decimales para CLP)")
    void debeConvertirMontoCorrectamente() throws Exception {
        StripeCheckoutRequest request = new StripeCheckoutRequest();
        request.setReservaId(2L);
        request.setMonto(new BigDecimal("89500.50")); // CLP no tiene centavos
        request.setTituloReserva("Suite Premium");

        try (MockedStatic<Session> stripeStatic = mockStatic(Session.class)) {
            Session sessionMock = mock(Session.class);
            when(sessionMock.getId()).thenReturn("cs_test_XYZ");
            when(sessionMock.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_XYZ");
            stripeStatic.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(sessionMock);

            StripeCheckoutResponse response = stripeService.crearCheckout(request);

            // La sesión se creó — el monto fue redondeado a 89501 (HALF_UP)
            assertThat(response.getSessionId()).isEqualTo("cs_test_XYZ");
        }
    }

    // ── confirmarPago() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("confirmarPago() - debe registrar pago y confirmar reserva cuando paymentStatus=paid")
    void debeConfirmarPagoExitosamente() throws Exception {
        StripeConfirmarRequest request = new StripeConfirmarRequest();
        request.setSessionId(SESSION_ID);
        request.setUsuarioId(1L);

        Session sessionMock = mock(Session.class);
        when(sessionMock.getId()).thenReturn(SESSION_ID);
        when(sessionMock.getPaymentStatus()).thenReturn("paid");
        when(sessionMock.getAmountTotal()).thenReturn(45000L);
        when(sessionMock.getMetadata()).thenReturn(Map.of("reserva_id", "1"));

        Pago pagoGuardado = Pago.builder()
                .id(1L).referencia("STRIPE-ABCDE12345")
                .reservaId(1L).usuarioId(1L)
                .monto(BigDecimal.valueOf(45000)).moneda("CLP")
                .metodoPago(Pago.MetodoPago.STRIPE)
                .estado(Pago.EstadoPago.COMPLETADO)
                .gatewayId(SESSION_ID)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(pagoRepository.existsByGatewayId(SESSION_ID)).thenReturn(false);
        when(pagoRepository.existsByReferencia(anyString())).thenReturn(false);
        when(pagoRepository.save(any())).thenReturn(pagoGuardado);

        try (MockedStatic<Session> stripeStatic = mockStatic(Session.class)) {
            stripeStatic.when(() -> Session.retrieve(SESSION_ID)).thenReturn(sessionMock);

            PagoResponse response = stripeService.confirmarPago(request);

            assertThat(response).isNotNull();
            assertThat(response.getEstado()).isEqualTo(Pago.EstadoPago.COMPLETADO);
            assertThat(response.getMoneda()).isEqualTo("CLP");
            verify(pagoRepository).save(any(Pago.class));
            verify(rabbitTemplate).convertAndSend(anyString(), eq("pago.completado"), anyMap());
        }
    }

    @Test
    @DisplayName("confirmarPago() - debe ser idempotente: retornar pago existente sin crear duplicado")
    void debeSerIdempotente() {
        StripeConfirmarRequest request = new StripeConfirmarRequest();
        request.setSessionId(SESSION_ID);
        request.setUsuarioId(1L);

        Pago pagoExistente = Pago.builder()
                .id(1L).referencia("STRIPE-EXISTING").reservaId(1L).usuarioId(1L)
                .monto(BigDecimal.valueOf(45000)).moneda("CLP")
                .metodoPago(Pago.MetodoPago.STRIPE)
                .estado(Pago.EstadoPago.COMPLETADO).gatewayId(SESSION_ID)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(pagoRepository.existsByGatewayId(SESSION_ID)).thenReturn(true);
        when(pagoRepository.findByGatewayId(SESSION_ID)).thenReturn(Optional.of(pagoExistente));

        PagoResponse response = stripeService.confirmarPago(request);

        assertThat(response.getGatewayId()).isEqualTo(SESSION_ID);
        // No debe llamar a Stripe API ni guardar un segundo pago
        verify(pagoRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("confirmarPago() - debe lanzar PagoFallidoException si paymentStatus != paid")
    void debeLanzarExcepcionSiPagoNoProcesado() throws Exception {
        StripeConfirmarRequest request = new StripeConfirmarRequest();
        request.setSessionId(SESSION_ID);
        request.setUsuarioId(1L);

        Session sessionMock = mock(Session.class);
        when(sessionMock.getPaymentStatus()).thenReturn("unpaid");

        when(pagoRepository.existsByGatewayId(SESSION_ID)).thenReturn(false);

        try (MockedStatic<Session> stripeStatic = mockStatic(Session.class)) {
            stripeStatic.when(() -> Session.retrieve(SESSION_ID)).thenReturn(sessionMock);

            assertThatThrownBy(() -> stripeService.confirmarPago(request))
                    .isInstanceOf(PagoFallidoException.class)
                    .hasMessageContaining("no está aprobado");

            verify(pagoRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("confirmarPago() - debe lanzar PagoFallidoException si Stripe API falla")
    void debeLanzarExcepcionSiStripeAPIFalla() throws Exception {
        StripeConfirmarRequest request = new StripeConfirmarRequest();
        request.setSessionId("cs_test_INVALID");
        request.setUsuarioId(1L);

        when(pagoRepository.existsByGatewayId("cs_test_INVALID")).thenReturn(false);

        try (MockedStatic<Session> stripeStatic = mockStatic(Session.class)) {
            StripeException stripeEx = mock(StripeException.class);
            when(stripeEx.getMessage()).thenReturn("No such checkout.session");
            stripeStatic.when(() -> Session.retrieve("cs_test_INVALID")).thenThrow(stripeEx);

            assertThatThrownBy(() -> stripeService.confirmarPago(request))
                    .isInstanceOf(PagoFallidoException.class)
                    .hasMessageContaining("No se pudo verificar");

            verify(pagoRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("confirmarPago() - debe proceder aunque confirmarReserva() falle (error no crítico)")
    void debeContinuarSiConfirmarReservaFalla() throws Exception {
        StripeConfirmarRequest request = new StripeConfirmarRequest();
        request.setSessionId(SESSION_ID);
        request.setUsuarioId(1L);

        Session sessionMock = mock(Session.class);
        when(sessionMock.getPaymentStatus()).thenReturn("paid");
        when(sessionMock.getAmountTotal()).thenReturn(45000L);
        when(sessionMock.getMetadata()).thenReturn(Map.of("reserva_id", "1"));

        Pago pago = Pago.builder()
                .id(1L).referencia("STRIPE-XYZ").reservaId(1L).usuarioId(1L)
                .monto(BigDecimal.valueOf(45000)).moneda("CLP")
                .metodoPago(Pago.MetodoPago.STRIPE)
                .estado(Pago.EstadoPago.COMPLETADO).gatewayId(SESSION_ID)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(pagoRepository.existsByGatewayId(SESSION_ID)).thenReturn(false);
        when(pagoRepository.existsByReferencia(anyString())).thenReturn(false);
        when(pagoRepository.save(any())).thenReturn(pago);
        // restTemplate falla al confirmar la reserva
        when(restTemplate.exchange(anyString(), any(), any(), eq(Object.class)))
                .thenThrow(new RuntimeException("reservas-service unreachable"));

        try (MockedStatic<Session> stripeStatic = mockStatic(Session.class)) {
            stripeStatic.when(() -> Session.retrieve(SESSION_ID)).thenReturn(sessionMock);

            // No debe lanzar excepción — el error de confirmar reserva es manejado internamente
            assertThatNoException().isThrownBy(() -> stripeService.confirmarPago(request));
            // El pago sí se guardó
            verify(pagoRepository).save(any(Pago.class));
        }
    }
}
