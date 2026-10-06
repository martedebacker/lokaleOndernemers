package com.example.lokaleondernemers.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "afbeelding")
@Getter
@Setter
@NoArgsConstructor
public class Afbeelding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String contentType;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = 10 * 1024 * 1024)
    private byte[] data;

    public Afbeelding(String contentType, byte[] data) {
        this.contentType = contentType;
        this.data = data;
    }
}
