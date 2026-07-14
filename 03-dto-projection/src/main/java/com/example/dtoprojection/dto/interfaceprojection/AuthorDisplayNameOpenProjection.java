package com.example.dtoprojection.dto.interfaceprojection;

import org.springframework.beans.factory.annotation.Value;

public interface AuthorDisplayNameOpenProjection {
    String getName();

    @Value("#{target.name + ' ' + target.lastname}")
    String getDisplayName();
}
