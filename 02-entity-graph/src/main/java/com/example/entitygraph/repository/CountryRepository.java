package com.example.entitygraph.repository;

import com.example.entitygraph.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, Long> {
}
