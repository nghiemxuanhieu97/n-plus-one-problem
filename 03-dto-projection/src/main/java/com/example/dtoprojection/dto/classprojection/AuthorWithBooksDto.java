package com.example.dtoprojection.dto.classprojection;

import com.example.dtoprojection.dto.interfaceprojection.BookSummaryProjection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthorWithBooksDto {
    private  Long id;
    private  String name;
    private  List<BookSummaryDto> books;

}
