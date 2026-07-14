package com.example.dtoprojection.dto.interfaceprojection;

import org.springframework.beans.factory.annotation.Value;

public interface AuthorBrokenDisplayNameOpenProjection {
    String getName();

    @Value("#{target.name + ' ' + target.firstname}")
    String getDisplayName();
}
