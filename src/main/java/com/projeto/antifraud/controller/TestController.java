package com.projeto.antifraud.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController{
    
    @GetMapping("/test")
    public String healthCheck(){
        return "O sistema antifraude está operacional.";
    }
}