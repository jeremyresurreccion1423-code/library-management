package com.smartlibrary.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(name = "ebooks")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "book")
public class EbookAsset extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", unique = true)
    private Book book;

    @Column(nullable = false, length = 512)
    private String originalFilename;

    @Column(nullable = false, length = 1024)
    private String storedPath;
}
