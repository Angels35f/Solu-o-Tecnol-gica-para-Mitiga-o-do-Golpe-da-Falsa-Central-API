package com.projeto.antifraud.service;

import com.projeto.antifraud.entity.Transaction;
import com.projeto.antifraud.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/*
  Serviço principal de análise de risco.
  - A função analyzeTransaction aplica várias regras heurísticas
    (autenticação, velocidade, histórico de dispositivo/canal/geo,
     múltiplos receptores, receptor novo, montante/hora).
  - As regras são avaliadas em ordem de prioridade e a primeira que
    corresponder marca a transação como suspeita e persiste o resultado.
  - Para evolução: considerar acumular sinais e retornar um score/enum
    de severidade em vez de retornos imediatos.
*/
@Service
public class RiskAnalysisService {

    @Autowired
    private TransactionRepository transactionRepository;

    public Transaction analyzeTransaction(Transaction transaction) {
        BigDecimal highLimit = new BigDecimal("2000.00");
        BigDecimal highAmountForNewReceiver = new BigDecimal("1000.00");

        int hour = transaction.getTimestamp().getHour();
        boolean isNightTime = (hour >= 22 || hour <= 6);

        // Regra: muitas tentativas de autenticação -> risco crítico imediato
        Integer authAttempts = transaction.getAuthAttempts() != null ? transaction.getAuthAttempts() : 0;
        if (authAttempts >= 3) {
            transaction.setSuspicious(true);
            transaction.setRiskReason("ALERTA: Múltiplas tentativas de autenticação falhadas.");
            return transactionRepository.save(transaction);
        }

        // Regra: velocidade (panic mode) - últimas 5 minutos
        LocalDateTime fiveMinutesAgo = transaction.getTimestamp().minusMinutes(5);
        var recentTransactions = transactionRepository.findBySenderAccountIdAndTimestampAfter(
                transaction.getSenderAccountId(),
                fiveMinutesAgo
        );
        if (recentTransactions.size() >= 3) {
            transaction.setSuspicious(true);
            transaction.setRiskReason("ALERTA: Possível ataque em modo pânico. Transações demais em curto período.");
            return transactionRepository.save(transaction);
        }

        // Regras baseadas na última transação do remetente (canal, dispositivo, geo)
        Optional<Transaction> lastOpt = transactionRepository.findTopBySenderAccountIdOrderByTimestampDesc(transaction.getSenderAccountId());
        if (lastOpt.isPresent()) {
            Transaction lastTx = lastOpt.get();

            // Mudança de canal inusitada
            String lastChannel = lastTx.getChannel();
            String currentChannel = transaction.getChannel();
            if (lastChannel != null && currentChannel != null && !lastChannel.equalsIgnoreCase(currentChannel)) {
                if (currentChannel.equalsIgnoreCase("PHONE") || !lastChannel.equalsIgnoreCase(currentChannel)) {
                    transaction.setSuspicious(true);
                    transaction.setRiskReason("Mudança de canal incomum: antes " + lastChannel + " agora " + currentChannel + ".");
                    return transactionRepository.save(transaction);
                }
            }

            // Mudança de dispositivo
            String lastDevice = lastTx.getDeviceId();
            String curDevice = transaction.getDeviceId();
            if (lastDevice != null && curDevice != null && !lastDevice.equals(curDevice)) {
                transaction.setSuspicious(true);
                transaction.setRiskReason("Dispositivo diferente do último registrado para a conta.");
                return transactionRepository.save(transaction);
            }

            // Mismatch de geolocalização em pouco tempo (com valor significativo)
            String lastGeo = lastTx.getGeoLocation();
            String curGeo = transaction.getGeoLocation();
            if (lastGeo != null && curGeo != null && !lastGeo.equalsIgnoreCase(curGeo)) {
                if (transaction.getAmount().compareTo(new BigDecimal("200.00")) > 0) {
                    transaction.setSuspicious(true);
                    transaction.setRiskReason("Alteração de geolocalização em relação à última transação.");
                    return transactionRepository.save(transaction);
                }
            }
        }

        // Padrão comportamental: múltiplos receptores distintos em 1 hora
        LocalDateTime oneHourAgo = transaction.getTimestamp().minusHours(1);
        var txsLastHour = transactionRepository.findBySenderAccountIdAndTimestampAfter(transaction.getSenderAccountId(), oneHourAgo);
        Set<String> distinctReceivers = txsLastHour.stream()
                .map(Transaction::getReceiverAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (transaction.getReceiverAccountId() != null) {
            distinctReceivers.add(transaction.getReceiverAccountId());
        }
        if (distinctReceivers.size() >= 3) {
            transaction.setSuspicious(true);
            transaction.setRiskReason("Padrão suspeito: múltiplos recebedores distintos em 1 hora.");
            return transactionRepository.save(transaction);
        }

        // Priorizar regra crítica: montante alto em horário noturno
        String receiver = transaction.getReceiverAccountId();
        if (transaction.getAmount().compareTo(highLimit) > 0 && isNightTime) {
            transaction.setSuspicious(true);
            transaction.setRiskReason("ALERTA CRÍTICO: Transação de alto valor em horário atípico.");
            return transactionRepository.save(transaction);
        } else if (transaction.getAmount().compareTo(highLimit) > 0) {
            transaction.setSuspicious(true);
            transaction.setRiskReason("Atenção: O valor excede o limite normal.");
            return transactionRepository.save(transaction);
        }

        // Receptor novo com montante elevado
        if (receiver != null) {
            LocalDateTime distantPast = LocalDateTime.now().minusYears(100);
            var receiverHistory = transactionRepository.findByReceiverAccountIdAndTimestampAfter(receiver, distantPast);
            if ((receiverHistory == null || receiverHistory.isEmpty()) && transaction.getAmount().compareTo(highAmountForNewReceiver) > 0) {
                transaction.setSuspicious(true);
                transaction.setRiskReason("Recebedor novo e montante elevado.");
                return transactionRepository.save(transaction);
            }
        }

        // Se nenhuma regra, pode aprovar
        transaction.setSuspicious(false);
        transaction.setRiskReason("Transação aprovada.");

        return transactionRepository.save(transaction);
    }
}