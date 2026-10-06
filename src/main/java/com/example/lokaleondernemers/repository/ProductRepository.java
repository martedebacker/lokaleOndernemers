package com.example.lokaleondernemers.repository;

import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.OndernemingStatus;
import com.example.lokaleondernemers.model.Product;
import com.example.lokaleondernemers.model.Regio;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByOndernemingAndVerwijderdFalseOrderByNaamAsc(Onderneming onderneming);

    List<Product> findByOndernemingAndZichtbaarTrueAndVerwijderdFalseOrderByCategorieAscNaamAsc(Onderneming onderneming);

    long countByOndernemingAndVerwijderdFalse(Onderneming onderneming);

    /** Producten die klanten kunnen zien, optioneel gefilterd op regio en zoekterm. */
    @Query("""
            select p from Product p join p.onderneming o
            where o.status = :status
              and p.zichtbaar = true and p.verwijderd = false
              and (:regio is null or o.regio = :regio)
              and (:zoek is null
                   or lower(p.naam) like lower(concat('%', :zoek, '%'))
                   or lower(p.categorie) like lower(concat('%', :zoek, '%'))
                   or lower(p.beschrijving) like lower(concat('%', :zoek, '%')))
            order by p.aangemaaktOp desc
            """)
    List<Product> zoekTeKoop(@Param("status") OndernemingStatus status,
                             @Param("regio") Regio regio,
                             @Param("zoek") String zoek,
                             Pageable pageable);
}
