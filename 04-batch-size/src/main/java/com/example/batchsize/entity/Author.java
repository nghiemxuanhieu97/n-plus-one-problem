package com.example.batchsize.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "authors")
@BatchSize(size = 5)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "books")
@EqualsAndHashCode(of = "id")
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // @BatchSize: when Hibernate needs to initialize this lazy collection,
    // it batches up to 5 authors' book collections into a single IN-clause query.
    // Instead of N queries: SELECT * FROM books WHERE author_id = ?  (x5)
    // It runs 1 query:      SELECT * FROM books WHERE author_id IN (1, 2, 3, 4, 5)
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 5)
    @Builder.Default
    private List<Book> books = new ArrayList<>();
}
