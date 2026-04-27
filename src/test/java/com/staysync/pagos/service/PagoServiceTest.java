package com.staysync.pagos.service;

import com.staysync.pagos.dto.request.ProcesarPagoRequest;
import com.staysync.pagos.dto.response.PagoResponse;
import com.staysync.pagos.exception.PagoFallidoException;
import com.staysync.pagos.exception.PagoNotFoundException;
import com.staysync.pagos.model.Pago;
import com.staysync.pagos.model.Pago.EstadoPago;
import com.staysync.pagos.model.Pago.MetodoPago;
import com.staysync.pagos.repository.PagoRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PagoService - Tests Unitarios")
class PagoServiceTest {

    @Mock private PagoRepository pagoRepository;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private PagoService pagoService;

    private Pago pagoBase;

    @BeforeEach
    void setUp() {
        pagoBase = Pago.builder()
                .id(1L).referencia("PAY-ABCD1234")
                .reservaId(1L).usuarioId(1L)
                .monto(BigDecimal.valueOf(200)).moneda("USD")
                .metodoPago(MetodoPago.TARJETA_CREDITO)
                .estado(EstadoPago.COMPLETADO)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("procesar() - debe completar pago cuando gateway token es válido")
    void debeProcesarPagoExitoso() {
        ProcesarPagoRequest request = new ProcesarPagoRequest();
        request.setReservaId(1L);
        request.setUsuarioId(1L);
        request.setMonto(BigDecimal.valueOf(200));
        request.setMetodoPago(MetodoPago.TARJETA_CREDITO);
        request.setGatewayToken("token-valido");

        Pago pagoPendiente = Pago.builder()
                .id(1L).referencia("PAY-TEST1234").reservaId(1L).usuarioId(1L)
                .monto(BigDecimal.valueOf(200)).moneda("USD")
                .metodoPago(MetodoPago.TARJETA_CREDITO)
                .estado(EstadoPago.PROCESANDO)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(pagoRepository.existsByReferencia(anyString())).thenReturn(false);
        when(pagoRepository.save(any())).thenReturn(pagoPendiente).thenReturn(pagoBase);

        PagoResponse response = pagoService.procesar(request);

        assertThat(response).isNotNull();
        verify(rabbitTemplate).convertAndSend(anyString(), eq("pago.completado"), anyMap());
    }

    @Test
    @DisplayName("procesar() - debe lanzar PagoFallidoException cuando no hay token")
    void debeLanzarExcepcionSinToken() {
        ProcesarPagoRequest request = new ProcesarPagoRequest();
        request.setReservaId(1L);
        request.setUsuarioId(1L);
        request.setMonto(BigDecimal.valueOf(100));
        request.setMetodoPago(MetodoPago.TARJETA_CREDITO);
        request.setGatewayToken(null);

        Pago pagoPendiente = Pago.builder()
                .id(2L).referencia("PAY-FAIL1234").reservaId(1L).usuarioId(1L)
                .monto(BigDecimal.valueOf(100)).moneda("USD")
                .metodoPago(MetodoPago.TARJETA_CREDITO)
                .estado(EstadoPago.PROCESANDO)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(pagoRepository.existsByReferencia(anyString())).thenReturn(false);
        when(pagoRepository.save(any())).thenReturn(pagoPendiente);

        assertThatThrownBy(() -> pagoService.procesar(request))
                .isInstanceOf(PagoFallidoException.class)
                .hasMessageContaining("fallido");

        verify(rabbitTemplate).convertAndSend(anyString(), eq("pago.fallido"), anyMap());
    }

    @Test
    @DisplayName("obtenerPorId() - debe retornar pago existente")
    void debeRetornarPagoPorId() {
        when(pagoRepository.findById(1L)).thenReturn(Optional.of(pagoBase));

        PagoResponse response = pagoService.obtenerPorId(1L);

        assertThat(response).isNotNull();
        assertThat(response.getReferencia()).isEqualTo("PAY-ABCD1234");
    }

    @Test
    @DisplayName("obtenerPorId() - debe lanzar excepción si no existe")
    void debeLanzarExcepcionPagoNoExiste() {
        when(pagoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pagoService.obtenerPorId(99L))
                .isInstanceOf(PagoNotFoundException.class);
    }

    @Test
    @DisplayName("obtenerPorReserva() - debe retornar lista de pagos")
    void debeRetornarPagosPorReserva() {
        when(pagoRepository.findByReservaId(1L)).thenReturn(List.of(pagoBase));

        List<PagoResponse> result = pagoService.obtenerPorReserva(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReservaId()).isEqualTo(1L);
    }
}
