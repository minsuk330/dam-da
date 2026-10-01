package com.khack.review.memory.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FsrsParametersRepository extends JpaRepository<FsrsParameters, Long> {

    Optional<FsrsParameters> findByVersion(int version);

    Optional<FsrsParameters> findTopByOrderByVersionDesc();

    List<FsrsParameters> findAllByOrderByVersionAsc();
}
