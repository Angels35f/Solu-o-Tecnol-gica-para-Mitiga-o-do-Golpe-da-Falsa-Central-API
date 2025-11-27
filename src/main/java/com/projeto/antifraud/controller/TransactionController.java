package com.projeto.antifraud.controller;

import com.projeto.antifraud.entity.Transaction;
import com.projeto.antifraud.service.RiskAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions") 
public class TransactionController {

    @Autowired
    private RiskAnalysisService riskAnalysisService;

    // Endpoint para analizar una transacción nueva
    // Método: POST
    // URL: http://localhost:8080/api/transactions/analyze
    @PostMapping("/analyze")
    public Transaction analyzeTransaction(@RequestBody Transaction transaction) {
        return riskAnalysisService.analyzeTransaction(transaction);
    }
}