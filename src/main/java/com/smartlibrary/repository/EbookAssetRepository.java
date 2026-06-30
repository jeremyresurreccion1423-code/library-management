package com.smartlibrary.repository;

import com.smartlibrary.entity.EbookAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EbookAssetRepository extends JpaRepository<EbookAsset, Long> {

    Optional<EbookAsset> findByBook_Id(Long bookId);
}
