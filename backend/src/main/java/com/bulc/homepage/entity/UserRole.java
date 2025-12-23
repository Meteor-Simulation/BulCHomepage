package com.bulc.homepage.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRole {

    @Id
    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 50)
    private String role;
}
