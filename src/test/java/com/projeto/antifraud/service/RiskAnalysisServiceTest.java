package com.projeto.antifraud.service;

import com.projeto.antifraud.entity.Transaction;
import com.projeto.antifraud.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
  Testes unitários para RiskAnalysisService.
  - Objetivo: validar cada regra heurística isoladamente.
  - Abordagem: mockar TransactionRepository com Mockito para controlar
    o histórico de transações e verificar a razão (riskReason) retornada.
*/
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskAnalysisServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private RiskAnalysisService riskAnalysisService;

    // Helper: cria uma transação base com campos padrão
    private Transaction baseTx(String sender, String receiver, LocalDateTime ts) {
        Transaction t = new Transaction(new BigDecimal("10.00"), sender, receiver, ts);
        t.setChannel("APP");
        t.setDeviceId("dev-1");
        t.setGeoLocation("BR");
        t.setIpAddress("1.1.1.1");
        t.setAuthAttempts(0);
        return t;
    }

    // Teste: múltiplas tentativas de autenticação disparam alerta crítico
    @Test
    void whenAuthAttemptsHigh_thenMarkSuspicious() {
        LocalDateTime ts = LocalDateTime.of(2025,11,27,10,0);
        Transaction tx = baseTx("s1","r1", ts);
        tx.setAuthAttempts(3);

        when(transactionRepository.findBySenderAccountIdAndTimestampAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.findTopBySenderAccountIdOrderByTimestampDesc(anyString()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByReceiverAccountIdAndTimestampAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction out = riskAnalysisService.analyzeTransaction(tx);

        assertTrue(out.isSuspicious());
        assertEquals("ALERTA: Múltiplas tentativas de autenticação falhadas.", out.getRiskReason());
    }

    // Teste: modo pânico (múltiplas transações em 5 minutos)
    @Test
    void whenPanicMode_thenMarkSuspicious() {
        LocalDateTime ts = LocalDateTime.of(2025,11,27,12,0);
        Transaction tx = baseTx("s2","r2", ts);

        List<Transaction> recent = Arrays.asList(
                baseTx("s2","rX", ts.minusMinutes(1)),
                baseTx("s2","rY", ts.minusMinutes(2)),
                baseTx("s2","rZ", ts.minusMinutes(3))
        );

        when(transactionRepository.findBySenderAccountIdAndTimestampAfter(eq("s2"), any(LocalDateTime.class)))
                .thenReturn(recent);
        when(transactionRepository.findTopBySenderAccountIdOrderByTimestampDesc(anyString()))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction out = riskAnalysisService.analyzeTransaction(tx);

        assertTrue(out.isSuspicious());
        assertEquals("ALERTA: Possível ataque em modo pânico. Transações demais em curto período.", out.getRiskReason());
    }

    // Teste: alteração de dispositivo deve ser marcada como suspeita
    @Test
    void whenDeviceChanged_thenMarkSuspicious() {
        LocalDateTime ts = LocalDateTime.of(2025,11,27,14,0);
        Transaction last = baseTx("s3","r-old", ts.minusMinutes(30));
        last.setDeviceId("device-old");

        Transaction tx = baseTx("s3","r-new", ts);
        tx.setDeviceId("device-new");

        when(transactionRepository.findBySenderAccountIdAndTimestampAfter(eq("s3"), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.findTopBySenderAccountIdOrderByTimestampDesc(eq("s3")))
                .thenReturn(Optional.of(last));
        when(transactionRepository.findByReceiverAccountIdAndTimestampAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction out = riskAnalysisService.analyzeTransaction(tx);

        assertTrue(out.isSuspicious());
        assertEquals("Dispositivo diferente do último registrado para a conta.", out.getRiskReason());
    }

    // Teste: mudança de canal para PHONE é considerada suspeita
    @Test
    void whenChannelChangedToPhone_thenMarkSuspicious() {
        LocalDateTime ts = LocalDateTime.of(2025,11,27,15,0);
        Transaction last = baseTx("s4","r-old", ts.minusMinutes(10));
        last.setChannel("APP");

        Transaction tx = baseTx("s4","r-new", ts);
        tx.setChannel("PHONE");

        when(transactionRepository.findBySenderAccountIdAndTimestampAfter(eq("s4"), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.findTopBySenderAccountIdOrderByTimestampDesc(eq("s4")))
                .thenReturn(Optional.of(last));
        when(transactionRepository.findByReceiverAccountIdAndTimestampAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction out = riskAnalysisService.analyzeTransaction(tx);

        assertTrue(out.isSuspicious());
        assertTrue(out.getRiskReason().contains("udança de canal incomum"));
    }

    // Teste: muitos receptores distintos em 1 hora -> padrão suspeito
    @Test
    void whenManyDistinctReceiversInHour_thenMarkSuspicious() {
        LocalDateTime ts = LocalDateTime.of(2025,11,27,16,0);
        Transaction tx = baseTx("s5","r3", ts);

        List<Transaction> lastHour = Arrays.asList(
                baseTx("s5","r1", ts.minusMinutes(50)),
                baseTx("s5","r2", ts.minusMinutes(30))
        );

        when(transactionRepository.findBySenderAccountIdAndTimestampAfter(eq("s5"), any(LocalDateTime.class)))
                .thenReturn(lastHour);
        when(transactionRepository.findTopBySenderAccountIdOrderByTimestampDesc(anyString()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByReceiverAccountIdAndTimestampAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction out = riskAnalysisService.analyzeTransaction(tx);

        assertTrue(out.isSuspicious());
        assertEquals("Padrão suspeito: múltiplos recebedores distintos em 1 hora.", out.getRiskReason());
    }

    // Teste: receptor novo e montante alto -> suspeita
    @Test
    void whenReceiverIsNewAndHighAmount_thenMarkSuspicious() {
        LocalDateTime ts = LocalDateTime.of(2025,11,27,17,0);
        Transaction tx = baseTx("s6","newReceiver", ts);
        tx.setAmount(new BigDecimal("1500.00"));

        when(transactionRepository.findBySenderAccountIdAndTimestampAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.findTopBySenderAccountIdOrderByTimestampDesc(anyString()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByReceiverAccountIdAndTimestampAfter(eq("newReceiver"), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction out = riskAnalysisService.analyzeTransaction(tx);

        assertTrue(out.isSuspicious());
        assertEquals("Recebedor novo e montante elevado.", out.getRiskReason());
    }

    // Teste: montante alto durante a noite -> alerta crítico (prioridade)
    @Test
    void whenHighAmountAtNight_thenCriticalAlert() {
        LocalDateTime ts = LocalDateTime.of(2025,11,27,23,30);
        Transaction tx = baseTx("s7","r7", ts);
        tx.setAmount(new BigDecimal("3000.00"));

        when(transactionRepository.findBySenderAccountIdAndTimestampAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.findTopBySenderAccountIdOrderByTimestampDesc(anyString()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByReceiverAccountIdAndTimestampAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction out = riskAnalysisService.analyzeTransaction(tx);

        assertTrue(out.isSuspicious());
        assertEquals("ALERTA CRÍTICO: Transação de alto valor em horário atípico.", out.getRiskReason());
    }

    // Teste: transação normal deve ser aprovada
    @Test
    void whenNormalTransaction_thenApproved() {
        LocalDateTime ts = LocalDateTime.of(2025,11,27,11,0);
        Transaction tx = baseTx("s8","r8", ts);
        tx.setAmount(new BigDecimal("50.00"));

        when(transactionRepository.findBySenderAccountIdAndTimestampAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.findTopBySenderAccountIdOrderByTimestampDesc(anyString()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByReceiverAccountIdAndTimestampAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction out = riskAnalysisService.analyzeTransaction(tx);

        assertFalse(out.isSuspicious());
        assertEquals("Transação aprovada.", out.getRiskReason());
    }
}