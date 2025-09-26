package com.todoapp.todoapi.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.todoapp.todoapi.model.Todo;

public interface TodoRepository extends JpaRepository<Todo, Long> {
}
