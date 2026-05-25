package com.company.iss.client.entity;

import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "clients")
@Getter
@Setter
public class Client extends BaseEntity {

    @Column(nullable = false, unique = true, length = 150)
    private String companyName;

    @Column(nullable = false, length = 250)
    private String address;

    @Column(length = 150)
    private String contactPerson;

    @Column(length = 30)
    private String contactNumber;

    @Column(length = 150)
    private String email;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;
}