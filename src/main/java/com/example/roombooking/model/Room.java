package com.example.roombooking.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "room")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer capacity;

    private String description;

    private String location;

    /** IANA zone id; slots start on the hour in this zone. */
    @Column(nullable = false)
    private String timezone;

    /** First bookable local hour (inclusive), 0-23. */
    @Column(name = "open_hour", nullable = false)
    private Integer openHour;

    /** End of the last bookable hour (exclusive), 1-24. */
    @Column(name = "close_hour", nullable = false)
    private Integer closeHour;
}
