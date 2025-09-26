package com.todoapp.todoapi.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	@GetMapping("/health")
	String ok() {
		return "OK";
	}
}
