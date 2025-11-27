package com.projeto.antifraud.repository;

import com.projeto.antifraud.entity.Transaction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/*
  Repositório Spring Data JPA para transações.
  - Consultas customizadas úteis para regras de velocidade e histórico.
*/
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findBySenderAccountIdAndTimestampAfter(String senderAccountId, LocalDateTime timestamp);
    List<Transaction> findBySenderAccountIdAndTimestampBetween(String senderAccountId, LocalDateTime start, LocalDateTime end);
    List<Transaction> findByIpAddressAndTimestampAfter(String ipAddress, LocalDateTime after);
    List<Transaction> findByReceiverAccountIdAndTimestampAfter(String receiverAccountId, LocalDateTime after);

    // Obter a última transação do remetente 
    Optional<Transaction> findTopBySenderAccountIdOrderByTimestampDesc(String senderAccountId);
}