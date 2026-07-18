package com.aakriti.registration.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import lombok.extern.slf4j.Slf4j;

@RestController
@CrossOrigin(origins = "*")
@Slf4j
public class HealthController {

    @GetMapping({"/health", "/"})
    public String healthCheck() {
        log.info("Wake-up / Keep-alive ping received. Server is active.");
        return "Server active";
    }
}
