package com.todo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.todo.entity.Todo;
import com.todo.repo.TodoRepository;

@RestController
@RequestMapping("/api/todos")
public class TodoController {
	
	private final TodoRepository repo;

	public TodoController(TodoRepository repo) {
		this.repo = repo;
	}

	@GetMapping
	public List<Todo> all() {
		return repo.findAll();
	}

	@PostMapping
	public Todo create(@RequestBody Todo t) {
		return repo.save(t);
	}

	@GetMapping("/{id}")
	public Todo one(@PathVariable Long id) {
		return repo.findById(id).orElseThrow();
	}

	@PatchMapping("/{id}")
	public Todo toggle(@PathVariable Long id) {
		Todo t = repo.findById(id).orElseThrow();
		t.setDone(!t.isDone());
		return repo.save(t);
	}
}
