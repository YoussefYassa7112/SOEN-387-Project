package com.squared.locus.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "parking_spots")
@Getter
@Setter
@NoArgsConstructor
public class ParkingSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "address", nullable = false, length = 300)
    private String address;

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    @Convert(converter = ParkingType.DbConverter.class)
    @Column(name = "parking_type", length = 20)
    private ParkingType parkingType;

    @Column(name = "restrictions", length = 1000)
    private String restrictions;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // availability_windows.spot_id -> parking_spots.id (ON DELETE CASCADE in the database)
    @OneToMany(mappedBy = "parkingSpot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AvailabilityWindow> availabilityWindows = new ArrayList<>();

    public ParkingSpot(UUID ownerId, String title, String description, String address,
                       String photoUrl, BigDecimal pricePerHour, ParkingType parkingType,
                       String restrictions) {
        this.ownerId = ownerId;
        this.title = title;
        this.description = description;
        this.address = address;
        this.photoUrl = photoUrl;
        this.pricePerHour = pricePerHour;
        this.parkingType = parkingType;
        this.restrictions = restrictions;
        this.active = true;
    }

    /** Replaces the whole schedule: old windows are deleted via orphanRemoval. */
    public void replaceAvailabilityWindows(List<AvailabilityWindow> windows) {
        availabilityWindows.clear();
        for (AvailabilityWindow w : windows) {
            w.setParkingSpot(this);
            availabilityWindows.add(w);
        }
    }

    /** True when one of the owner's windows covers the whole interval. */
    public boolean isOpenDuring(OffsetDateTime startTime, OffsetDateTime endTime) {
        for (AvailabilityWindow window : availabilityWindows) {
            boolean startsInTime = !window.getStartTime().isAfter(startTime);
            boolean endsInTime = !window.getEndTime().isBefore(endTime);
            if (startsInTime && endsInTime) {
                return true;
            }
        }
        return false;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public enum ParkingType {
        DRIVEWAY("driveway"),
        PRIVATE_LOT("privateLot"),
        GARAGE("garage"),
        OTHER("other");

        private final String dbValue;

        ParkingType(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static ParkingType fromDbValue(String value) {
            for (ParkingType t : values()) {
                if (t.dbValue.equals(value)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Unknown parking_type: " + value);
        }

        @Converter
        public static class DbConverter implements AttributeConverter<ParkingType, String> {
            @Override
            public String convertToDatabaseColumn(ParkingType attribute) {
                return attribute == null ? null : attribute.dbValue;
            }

            @Override
            public ParkingType convertToEntityAttribute(String dbData) {
                return dbData == null ? null : ParkingType.fromDbValue(dbData);
            }
        }
    }
}