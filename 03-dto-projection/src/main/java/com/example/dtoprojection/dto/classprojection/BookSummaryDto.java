package com.example.dtoprojection.dto.classprojection;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BookSummaryDto {
    private Long id;
    private String title;
    private String region;

}
