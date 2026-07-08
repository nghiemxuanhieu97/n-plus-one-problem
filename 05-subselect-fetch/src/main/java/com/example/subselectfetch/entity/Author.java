package com.example.subselectfetch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "authors")
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

    // @Fetch(SUBSELECT): when any book collection is first accessed,
    // Hibernate loads collections for ALL authors in the current session
    // using a subquery — always exactly 2 queries total regardless of N.
    //
    // SQL #2: SELECT * FROM books WHERE author_id IN (SELECT id FROM authors)
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private List<Book> books = new ArrayList<>();
}
