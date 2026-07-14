package com.example.entitygraph.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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
        )
})
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

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Book> books = new ArrayList<>();
}
