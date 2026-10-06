package com.example.lokaleondernemers.repository;

import com.example.lokaleondernemers.model.BestelStatus;
import com.example.lokaleondernemers.model.Bestelling;
import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BestellingRepository extends JpaRepository<Bestelling, Long> {

    List<Bestelling> findByKlantOrderByGeplaatstOpDesc(Gebruiker klant);

    List<Bestelling> findByOndernemingOrderByGeplaatstOpDesc(Onderneming onderneming);

    List<Bestelling> findByOndernemingAndStatusInOrderByGeplaatstOpDesc(Onderneming onderneming,
                                                                         Collection<BestelStatus> statussen);

    long countByOndernemingAndStatus(Onderneming onderneming, BestelStatus status);

    long countByStatus(BestelStatus status);

    List<Bestelling> findAllByOrderByGeplaatstOpDesc();

    @Query("select count(r) > 0 from BestelRegel r where r.product = :product")
    boolean isProductBesteld(@Param("product") Product product);
}
