package com.example.batchsize.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "publishers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Publisher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // Deliberately has no @BatchSize. It is the control association for baseline/global-profile demos.
    @OneToMany(mappedBy = "publisher", fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<Book> books = new ArrayList<>();
}
