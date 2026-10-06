package com.example.lokaleondernemers.dto;

import com.example.lokaleondernemers.model.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductForm {

    @NotBlank(message = "Geef je product een naam.")
    @Size(max = 120)
    private String naam;

    @Size(max = 4000, message = "Maximaal 4000 tekens.")
    private String beschrijving;

    @NotNull(message = "Vul een prijs in.")
    @DecimalMin(value = "0.00", message = "De prijs kan niet negatief zijn.")
    @Digits(integer = 8, fraction = 2, message = "Maximaal 2 cijfers na de komma.")
    private BigDecimal prijs;

    @Size(max = 40)
    private String eenheid;

    @Size(max = 60)
    private String categorie;

    @NotNull(message = "Vul de voorraad in.")
    @Min(value = 0, message = "De voorraad kan niet negatief zijn.")
    @Max(value = 100000)
    private Integer voorraad;

    private boolean zichtbaar = true;

    private MultipartFile afbeelding;

    private boolean afbeeldingVerwijderen;

    public static ProductForm van(Product p) {
        ProductForm f = new ProductForm();
        f.naam = p.getNaam();
        f.beschrijving = p.getBeschrijving();
        f.prijs = p.getPrijs();
        f.eenheid = p.getEenheid();
        f.categorie = p.getCategorie();
        f.voorraad = p.getVoorraad();
        f.zichtbaar = p.isZichtbaar();
        return f;
    }
}
