package com.example.lokaleondernemers.service;

import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.Product;
import com.example.lokaleondernemers.repository.ProductRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Importeert producten uit een Excelbestand. De kolommen worden herkend aan hun kopregel
 * (bv. "Naam", "Prijs (€)", "Stock") en de ondernemer kan die koppeling nog aanpassen voor het importeren.
 * Producten met dezelfde naam als een bestaand product worden bijgewerkt in plaats van dubbel aangemaakt.
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    public static final int MAX_RIJEN = 1000;
    private static final int MAX_KOLOMMEN = 50;

    private final ProductRepository productRepository;

    /** De velden waarnaar een Excelkolom gekoppeld kan worden. */
    public enum Veld {
        NAAM("Naam", true, "naam", "product", "productnaam", "artikel", "artikelnaam", "titel", "name"),
        BESCHRIJVING("Beschrijving", false, "beschrijving", "omschrijving", "info", "details", "description"),
        PRIJS("Prijs", true, "prijs", "verkoopprijs", "prijsincl", "prijsinclbtw", "eenheidsprijs", "kostprijs", "price", "euro", "eur"),
        EENHEID("Eenheid", false, "eenheid", "per", "verpakking", "inhoud", "unit"),
        CATEGORIE("Categorie", false, "categorie", "category", "soort", "type", "groep", "rubriek"),
        VOORRAAD("Voorraad", false, "voorraad", "stock", "aantal", "hoeveelheid", "beschikbaar", "qty", "quantity"),
        ZICHTBAAR("Zichtbaar", false, "zichtbaar", "tonen", "online", "actief", "visible", "publiceren");

        private final String label;
        private final boolean verplicht;
        private final List<String> synoniemen;

        Veld(String label, boolean verplicht, String... synoniemen) {
            this.label = label;
            this.verplicht = verplicht;
            this.synoniemen = List.of(synoniemen);
        }

        public String getLabel() {
            return label;
        }

        public boolean isVerplicht() {
            return verplicht;
        }
    }

    // ---- inlezen ----

    /** Leest het eerste werkblad: de eerste niet-lege rij is de kopregel, de rest zijn producten. */
    public Tabel lees(MultipartFile bestand) {
        if (bestand == null || bestand.isEmpty()) {
            throw new BedrijfsregelException("Kies een Excelbestand om te importeren.");
        }
        try (InputStream in = bestand.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet blad = workbook.getSheetAt(0);
            List<String> kolommen = null;
            List<Rij> rijen = new ArrayList<>();
            for (Row row : blad) {
                List<String> waarden = new ArrayList<>();
                int laatste = Math.min(Math.max(row.getLastCellNum(), 0), MAX_KOLOMMEN);
                for (int i = 0; i < laatste; i++) {
                    waarden.add(tekst(row.getCell(i)));
                }
                if (waarden.stream().allMatch(String::isEmpty)) {
                    continue;
                }
                if (kolommen == null) {
                    kolommen = waarden;
                    continue;
                }
                if (rijen.size() >= MAX_RIJEN) {
                    throw new BedrijfsregelException("Je kan maximaal " + MAX_RIJEN + " producten tegelijk importeren.");
                }
                rijen.add(new Rij(row.getRowNum() + 1, waarden));
            }
            if (kolommen == null) {
                throw new BedrijfsregelException("Het Excelbestand is leeg.");
            }
            if (rijen.isEmpty()) {
                throw new BedrijfsregelException("Er staan geen producten onder de kopregel.");
            }
            return new Tabel(bestand.getOriginalFilename(), kolommen, rijen);
        } catch (EncryptedDocumentException e) {
            throw new BedrijfsregelException("Dit Excelbestand is beveiligd met een wachtwoord. Verwijder de beveiliging en probeer opnieuw.");
        } catch (IOException | RuntimeException e) {
            if (e instanceof BedrijfsregelException b) {
                throw b;
            }
            throw new BedrijfsregelException("Dit bestand kon niet gelezen worden. Gebruik een Excelbestand (.xlsx of .xls).");
        }
    }

    /** Raadt voor elke kolom welk veld het is, op basis van de kopregel. Elk veld wordt hoogstens één keer gekoppeld. */
    public Map<Integer, Veld> raadKoppeling(List<String> kolommen) {
        Map<Integer, Veld> koppeling = new HashMap<>();
        Set<Veld> gebruikt = new HashSet<>();
        // Eerst exacte treffers, daarna kopregels die een synoniem bevatten (bv. "Prijs in euro").
        for (boolean exact : new boolean[]{true, false}) {
            for (int i = 0; i < kolommen.size(); i++) {
                if (koppeling.containsKey(i)) {
                    continue;
                }
                String kop = normaliseer(kolommen.get(i));
                if (kop.isEmpty()) {
                    continue;
                }
                for (Veld veld : Veld.values()) {
                    if (gebruikt.contains(veld)) {
                        continue;
                    }
                    boolean treffer = veld.synoniemen.stream()
                            .anyMatch(s -> exact ? kop.equals(s) : (s.length() > 3 && kop.contains(s)));
                    if (treffer) {
                        koppeling.put(i, veld);
                        gebruikt.add(veld);
                        break;
                    }
                }
            }
        }
        return koppeling;
    }

    // ---- controleren ----

    public Voorbeeld controleer(Tabel tabel, Map<Integer, Veld> koppeling, Onderneming onderneming) {
        Map<Veld, Integer> kolomVan = new EnumMap<>(Veld.class);
        koppeling.forEach((kolom, veld) -> kolomVan.putIfAbsent(veld, kolom));

        List<String> algemeneFouten = new ArrayList<>();
        for (Veld veld : Veld.values()) {
            if (veld.verplicht && !kolomVan.containsKey(veld)) {
                algemeneFouten.add("Duid aan welke kolom de " + veld.label.toLowerCase() + " bevat.");
            }
        }
        List<String> opmerkingen = new ArrayList<>();
        if (!kolomVan.containsKey(Veld.VOORRAAD)) {
            opmerkingen.add("Er is geen kolom voor de voorraad: nieuwe producten krijgen voorraad 0 (uitverkocht) "
                    + "en bij bestaande producten blijft de voorraad ongewijzigd.");
        }

        Map<String, Product> bestaande = new HashMap<>();
        for (Product p : productRepository.findByOndernemingAndVerwijderdFalseOrderByNaamAsc(onderneming)) {
            bestaande.putIfAbsent(p.getNaam().trim().toLowerCase(Locale.ROOT), p);
        }

        List<ImportRij> rijen = new ArrayList<>();
        Set<String> gezien = new HashSet<>();
        for (Rij rij : tabel.getRijen()) {
            ImportRij r = new ImportRij(rij.getNummer());
            r.naam = waarde(rij, kolomVan.get(Veld.NAAM));
            r.beschrijving = waarde(rij, kolomVan.get(Veld.BESCHRIJVING));
            r.eenheid = waarde(rij, kolomVan.get(Veld.EENHEID));
            r.categorie = waarde(rij, kolomVan.get(Veld.CATEGORIE));

            if (r.naam == null) {
                r.fouten.add("Naam ontbreekt");
            } else if (r.naam.length() > 120) {
                r.fouten.add("Naam is langer dan 120 tekens");
            } else if (!gezien.add(r.naam.toLowerCase(Locale.ROOT))) {
                r.fouten.add("Komt meer dan eens voor in het bestand");
            }
            if (kolomVan.containsKey(Veld.PRIJS)) {
                String prijs = waarde(rij, kolomVan.get(Veld.PRIJS));
                if (prijs == null) {
                    r.fouten.add("Prijs ontbreekt");
                } else {
                    r.prijs = leesPrijs(prijs);
                    if (r.prijs == null) {
                        r.fouten.add("'" + prijs + "' is geen geldige prijs");
                    }
                }
            }
            if (kolomVan.containsKey(Veld.VOORRAAD)) {
                String voorraad = waarde(rij, kolomVan.get(Veld.VOORRAAD));
                if (voorraad != null) {
                    r.voorraad = leesAantal(voorraad);
                    if (r.voorraad == null) {
                        r.fouten.add("'" + voorraad + "' is geen geldige voorraad");
                    }
                }
            }
            if (kolomVan.containsKey(Veld.ZICHTBAAR)) {
                String zichtbaar = waarde(rij, kolomVan.get(Veld.ZICHTBAAR));
                if (zichtbaar != null) {
                    r.zichtbaar = leesJaNee(zichtbaar);
                    if (r.zichtbaar == null) {
                        r.fouten.add("Zichtbaar moet ja of nee zijn");
                    }
                }
            }
            if (r.beschrijving != null && r.beschrijving.length() > 4000) {
                r.fouten.add("Beschrijving is langer dan 4000 tekens");
            }
            if (r.eenheid != null && r.eenheid.length() > 40) {
                r.fouten.add("Eenheid is langer dan 40 tekens");
            }
            if (r.categorie != null && r.categorie.length() > 60) {
                r.fouten.add("Categorie is langer dan 60 tekens");
            }
            if (r.naam != null) {
                Product bestaand = bestaande.get(r.naam.toLowerCase(Locale.ROOT));
                r.bestaandProductId = bestaand == null ? null : bestaand.getId();
            }
            rijen.add(r);
        }
        return new Voorbeeld(rijen, algemeneFouten, opmerkingen, kolomVan.keySet());
    }

    // ---- importeren ----

    /** Importeert alle foutloze rijen. Velden zonder gekoppelde kolom blijven bij bestaande producten ongewijzigd. */
    @Transactional
    public Resultaat importeer(Tabel tabel, Map<Integer, Veld> koppeling, Onderneming onderneming) {
        Voorbeeld voorbeeld = controleer(tabel, koppeling, onderneming);
        if (!voorbeeld.getAlgemeneFouten().isEmpty()) {
            throw new BedrijfsregelException(voorbeeld.getAlgemeneFouten().getFirst());
        }
        Set<Veld> velden = voorbeeld.getGekoppeldeVelden();
        int nieuw = 0;
        int bijgewerkt = 0;
        for (ImportRij r : voorbeeld.getRijen()) {
            if (!r.isGeldig()) {
                continue;
            }
            Product p;
            if (r.bestaandProductId != null) {
                p = productRepository.findById(r.bestaandProductId).orElseThrow();
                bijgewerkt++;
            } else {
                p = new Product();
                p.setOnderneming(onderneming);
                p.setNaam(r.naam);
                nieuw++;
            }
            p.setPrijs(r.prijs);
            if (velden.contains(Veld.BESCHRIJVING) || p.getId() == null) {
                p.setBeschrijving(r.beschrijving);
            }
            if (velden.contains(Veld.EENHEID) || p.getId() == null) {
                p.setEenheid(r.eenheid);
            }
            if (velden.contains(Veld.CATEGORIE) || p.getId() == null) {
                p.setCategorie(r.categorie);
            }
            if (r.voorraad != null) {
                p.setVoorraad(r.voorraad);
            }
            if (r.zichtbaar != null) {
                p.setZichtbaar(r.zichtbaar);
            }
            productRepository.save(p);
        }
        return new Resultaat(nieuw, bijgewerkt, voorbeeld.getAantalFout());
    }

    /** Een leeg voorbeeldbestand met de juiste kopregel en twee voorbeeldproducten. */
    public byte[] sjabloon() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream uit = new ByteArrayOutputStream()) {
            Sheet blad = wb.createSheet("Producten");
            Font vet = wb.createFont();
            vet.setBold(true);
            CellStyle kop = wb.createCellStyle();
            kop.setFont(vet);
            String[] koppen = {"Naam", "Beschrijving", "Prijs", "Eenheid", "Categorie", "Voorraad", "Zichtbaar"};
            Row kopRij = blad.createRow(0);
            for (int i = 0; i < koppen.length; i++) {
                Cell c = kopRij.createCell(i);
                c.setCellValue(koppen[i]);
                c.setCellStyle(kop);
            }
            Object[][] voorbeelden = {
                    {"Desembrood", "Langzaam gerezen brood met krokante korst", 4.20, "per stuk", "Brood", 20, "ja"},
                    {"Aardbeienconfituur", "Met aardbeien uit eigen tuin", 5.50, "per pot (370 g)", "Confituur", 12, "ja"},
            };
            for (int r = 0; r < voorbeelden.length; r++) {
                Row rij = blad.createRow(r + 1);
                for (int i = 0; i < voorbeelden[r].length; i++) {
                    Object w = voorbeelden[r][i];
                    if (w instanceof Number n) {
                        rij.createCell(i).setCellValue(n.doubleValue());
                    } else {
                        rij.createCell(i).setCellValue(w.toString());
                    }
                }
            }
            int[] breedtes = {24, 44, 10, 18, 16, 10, 10};
            for (int i = 0; i < breedtes.length; i++) {
                blad.setColumnWidth(i, breedtes[i] * 256);
            }
            blad.createFreezePane(0, 1);
            wb.write(uit);
            return uit.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- hulpfuncties ----

    private static String waarde(Rij rij, Integer kolom) {
        if (kolom == null || kolom >= rij.getWaarden().size()) {
            return null;
        }
        String w = rij.getWaarden().get(kolom).trim();
        return w.isEmpty() ? null : w;
    }

    private static String tekst(Cell cel) {
        if (cel == null) {
            return "";
        }
        CellType type = cel.getCellType() == CellType.FORMULA ? cel.getCachedFormulaResultType() : cel.getCellType();
        return switch (type) {
            case NUMERIC -> DateUtil.isCellDateFormatted(cel)
                    ? cel.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(cel.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case STRING -> cel.getStringCellValue().trim();
            case BOOLEAN -> cel.getBooleanCellValue() ? "ja" : "nee";
            default -> "";
        };
    }

    static String normaliseer(String kop) {
        return Normalizer.normalize(kop == null ? "" : kop, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    /** Begrijpt "4,20", "4.20", "€ 4,20", "1.250,00" en "1,250.00". */
    static BigDecimal leesPrijs(String tekst) {
        String t = tekst.replace("€", "").replaceAll("(?i)eur(o)?", "").replace(" ", "").replace(" ", "");
        int komma = t.lastIndexOf(',');
        int punt = t.lastIndexOf('.');
        if (komma >= 0 && punt >= 0) {
            t = komma > punt ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
        } else if (komma >= 0) {
            t = t.replace(',', '.');
        }
        try {
            BigDecimal prijs = new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
            return prijs.signum() < 0 || prijs.precision() > 10 ? null : prijs;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer leesAantal(String tekst) {
        try {
            int aantal = new BigDecimal(tekst.replace(",", ".").trim()).intValueExact();
            return aantal < 0 || aantal > 100000 ? null : aantal;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    static Boolean leesJaNee(String tekst) {
        return switch (tekst.trim().toLowerCase(Locale.ROOT)) {
            case "ja", "j", "yes", "y", "true", "waar", "1", "x", "v", "online", "zichtbaar" -> true;
            case "nee", "n", "no", "false", "onwaar", "0", "verborgen", "offline" -> false;
            default -> null;
        };
    }

    // ---- datatypes ----

    /** De ruwe inhoud van het Excelbestand; wordt tussen upload en bevestiging in de sessie bewaard. */
    @Getter
    public static class Tabel implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final String bestandsnaam;
        private final List<String> kolommen;
        private final List<Rij> rijen;

        Tabel(String bestandsnaam, List<String> kolommen, List<Rij> rijen) {
            this.bestandsnaam = bestandsnaam;
            this.kolommen = kolommen;
            this.rijen = rijen;
        }
    }

    @Getter
    public static class Rij implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final int nummer;
        private final List<String> waarden;

        Rij(int nummer, List<String> waarden) {
            this.nummer = nummer;
            this.waarden = waarden;
        }
    }

    @Getter
    public static class ImportRij {
        private final int rijNummer;
        private String naam;
        private String beschrijving;
        private BigDecimal prijs;
        private String eenheid;
        private String categorie;
        private Integer voorraad;
        private Boolean zichtbaar;
        private Long bestaandProductId;
        private final List<String> fouten = new ArrayList<>();

        ImportRij(int rijNummer) {
            this.rijNummer = rijNummer;
        }

        public boolean isGeldig() {
            return fouten.isEmpty();
        }

        public boolean isBijwerken() {
            return bestaandProductId != null;
        }
    }

    @Getter
    public static class Voorbeeld {
        private final List<ImportRij> rijen;
        private final List<String> algemeneFouten;
        private final List<String> opmerkingen;
        private final Set<Veld> gekoppeldeVelden;

        Voorbeeld(List<ImportRij> rijen, List<String> algemeneFouten, List<String> opmerkingen, Set<Veld> gekoppeldeVelden) {
            this.rijen = rijen;
            this.algemeneFouten = algemeneFouten;
            this.opmerkingen = opmerkingen;
            this.gekoppeldeVelden = gekoppeldeVelden;
        }

        public long getAantalNieuw() {
            return rijen.stream().filter(r -> r.isGeldig() && !r.isBijwerken()).count();
        }

        public long getAantalBijwerken() {
            return rijen.stream().filter(r -> r.isGeldig() && r.isBijwerken()).count();
        }

        public long getAantalFout() {
            return rijen.stream().filter(r -> !r.isGeldig()).count();
        }

        public boolean isImporteerbaar() {
            return algemeneFouten.isEmpty() && getAantalNieuw() + getAantalBijwerken() > 0;
        }
    }

    public record Resultaat(int nieuw, int bijgewerkt, long overgeslagen) {
    }

    /** Sessie-inhoud tijdens een import: het bestand plus de (aangepaste) kolomkoppeling. */
    @Getter
    public static class Sessie implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        public static final String ATTRIBUUT = "productImport";
        private final Long ondernemingId;
        private final Tabel tabel;
        private final LinkedHashMap<Integer, Veld> koppeling;

        public Sessie(Long ondernemingId, Tabel tabel, Map<Integer, Veld> koppeling) {
            this.ondernemingId = ondernemingId;
            this.tabel = tabel;
            this.koppeling = new LinkedHashMap<>(koppeling);
        }

        public Optional<Veld> veldVan(int kolom) {
            return Optional.ofNullable(koppeling.get(kolom));
        }
    }
}
