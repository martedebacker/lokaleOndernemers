package com.example.lokaleondernemers.repository;

import com.example.lokaleondernemers.model.Evenement;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.OndernemingStatus;
import com.example.lokaleondernemers.model.Regio;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EvenementRepository extends JpaRepository<Evenement, Long> {

    /**
     * Komende (of lopende) evenementen die klanten mogen zien: van het platform, of van een
     * onderneming die actief is. Optioneel beperkt tot één regio.
     */
    @Query("""
            select e from Evenement e left join e.organisator o
            where (o is null or o.status = :actief)
              and coalesce(e.eindMoment, e.startMoment) >= :nu
              and (:regio is null or e.regio = :regio)
            order by e.startMoment
            """)
    List<Evenement> komende(@Param("actief") OndernemingStatus actief,
                            @Param("regio") Regio regio,
                            @Param("nu") LocalDateTime nu,
                            Pageable pageable);

    /** Komende evenementen waar een onderneming (als organisator of deelnemer) bij betrokken is. */
    @Query("""
            select distinct e from Evenement e left join e.organisator o
            where (o is null or o.status = :actief)
              and coalesce(e.eindMoment, e.startMoment) >= :nu
              and (o = :onderneming or :onderneming member of e.ondernemingen)
            order by e.startMoment
            """)
    List<Evenement> komendeVan(@Param("actief") OndernemingStatus actief,
                               @Param("onderneming") Onderneming onderneming,
                               @Param("nu") LocalDateTime nu);

    /** Alle evenementen (ook voorbije) van een onderneming, voor het beheer. */
    @Query("""
            select distinct e from Evenement e
            where e.organisator = :onderneming or :onderneming member of e.ondernemingen
            order by e.startMoment desc
            """)
    List<Evenement> allesVan(@Param("onderneming") Onderneming onderneming);

    List<Evenement> findAllByOrderByStartMomentDesc();

    List<Evenement> findByRegio(Regio regio);

    long countByRegio(Regio regio);
}
