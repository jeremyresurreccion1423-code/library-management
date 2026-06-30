package com.smartlibrary.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "categories")
@Data
@EqualsAndHashCode(callSuper = true)
public class Category extends BaseEntity {

    @NotBlank(message = "Category name cannot be blank")
    @Column(nullable = false, unique = true, length = 128)
    private String name;
}
