package com.example.entitygraph.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "authors")
@NamedEntityGraphs({
        @NamedEntityGraph(
                name = "Author.withBooks",
                attributeNodes = @NamedAttributeNode("books")
        ),
        @NamedEntityGraph(
                name = "Author.withBooksAndPublisher",
                attributeNodes = @NamedAttributeNode(value = "books", subgraph = "books.publisher"),
                subgraphs = @NamedSubgraph(
                        name = "books.publisher",
                        attributeNodes = @NamedAttributeNode("publisher")
                )
        ),
        @NamedEntityGraph(
                name = "Author.withBooksAndAwards",
                attributeNodes = {
                        @NamedAttributeNode("books"),
                        @NamedAttributeNode("awards")
                }
        )
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"country", "books", "awards"})
@EqualsAndHashCode(of = "id")
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /*
     * Intentionally EAGER only so the demo can make FETCH vs LOAD observable.
     * Production mappings should normally stay LAZY and choose a fetch plan per use case.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "country_id")
    private Country country;

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Book> books = new ArrayList<>();

    // Set avoids MultipleBagFetchException so the demo can execute and expose row multiplication.
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Award> awards = new HashSet<>();
}
