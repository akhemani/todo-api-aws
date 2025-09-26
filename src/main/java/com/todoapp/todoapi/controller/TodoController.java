package com.todoapp.todoapi.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.todoapp.todoapi.model.Todo;
import com.todoapp.todoapi.repo.TodoRepository;

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
	public ResponseEntity<Todo> one(@PathVariable Long id) {
		return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/{id}")
	public ResponseEntity<Todo> update(@PathVariable Long id, @RequestBody Todo t) {
		return repo.findById(id).map(existing -> {
			existing.setTitle(t.getTitle());
			existing.setDone(t.isDone());
			return ResponseEntity.ok(repo.save(existing));
		}).orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!repo.existsById(id))
			return ResponseEntity.notFound().build();
		repo.deleteById(id);
		return ResponseEntity.noContent().build();
	}
}
