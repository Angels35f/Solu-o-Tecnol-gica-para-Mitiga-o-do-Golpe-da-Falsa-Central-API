package com.projeto.antifraud.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/*
  Entidade que representa uma transação.
  - Campos principais: amount, senderAccountId, receiverAccountId, timestamp.
  - Campos auxiliares para análise de risco: channel, deviceId, ipAddress, geoLocation, authAttempts.
  - Campos de auditoria: status, createdAt, updatedAt.
*/
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal amount;
    private String currency;
    private String senderAccountId;
    private String receiverAccountId;
    private String customerId;        
    private String channel;           
    private String deviceId;          
    private String ipAddress;         
    private String geoLocation;       
    private Integer authAttempts;   
    private LocalDateTime timestamp;

    private boolean isSuspicious;
    private String riskReason;

    private String status;            // "PENDING","APPROVED","REJECTED"
    private LocalDateTime createdAt;  
    private LocalDateTime updatedAt;  

    public Transaction() {}

    public Transaction(BigDecimal amount, String senderAccountId, String receiverAccountId, LocalDateTime timestamp) {
        this.amount = amount;
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.timestamp = timestamp;
        this.isSuspicious = false;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    // Getters e Setters 
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getSenderAccountId() { return senderAccountId; }
    public void setSenderAccountId(String senderAccountId) { this.senderAccountId = senderAccountId; }

    public String getReceiverAccountId() { return receiverAccountId; }
    public void setReceiverAccountId(String receiverAccountId) { this.receiverAccountId = receiverAccountId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getGeoLocation() { return geoLocation; }
    public void setGeoLocation(String geoLocation) { this.geoLocation = geoLocation; }

    public Integer getAuthAttempts() { return authAttempts; }
    public void setAuthAttempts(Integer authAttempts) { this.authAttempts = authAttempts; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isSuspicious() { return isSuspicious; }
    public void setSuspicious(boolean isSuspicious) { this.isSuspicious = isSuspicious; }

    public String getRiskReason() { return riskReason; }
    public void setRiskReason(String riskReason) { this.riskReason = riskReason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}