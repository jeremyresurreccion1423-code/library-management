package com.smartlibrary.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "authors")
@Data
@EqualsAndHashCode(callSuper = true)
public class Author extends BaseEntity {

    @NotBlank(message = "Author name cannot be blank")
    @Column(nullable = false, length = 255)
    private String name;
}
