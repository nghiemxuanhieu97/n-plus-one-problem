package com.example.entitygraph.repository;

import com.example.entitygraph.entity.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {
}
