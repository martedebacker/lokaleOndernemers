package com.example.lokaleondernemers.repository;

import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.OndernemingStatus;
import com.example.lokaleondernemers.model.Regio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OndernemingRepository extends JpaRepository<Onderneming, Long> {

    Optional<Onderneming> findBySlug(String slug);

    Optional<Onderneming> findByEigenaar(Gebruiker eigenaar);

    boolean existsBySlug(String slug);

    long countByStatus(OndernemingStatus status);

    long countByRegioAndStatus(Regio regio, OndernemingStatus status);

    long countByRegio(Regio regio);

    List<Onderneming> findByRegio(Regio regio);

    List<Onderneming> findByStatusOrderByNaamAsc(OndernemingStatus status);

    List<Onderneming> findAllByOrderByAangemaaktOpDesc();

    @Query("""
            select o from Onderneming o
            where o.status = :status
              and (:regio is null or o.regio = :regio)
              and (:zoek is null
                   or lower(o.naam) like lower(concat('%', :zoek, '%'))
                   or lower(o.gemeente) like lower(concat('%', :zoek, '%'))
                   or lower(o.slogan) like lower(concat('%', :zoek, '%'))
                   or lower(o.beschrijving) like lower(concat('%', :zoek, '%')))
            order by o.naam
            """)
    List<Onderneming> zoek(@Param("status") OndernemingStatus status,
                           @Param("regio") Regio regio,
                           @Param("zoek") String zoek);
}
