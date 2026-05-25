package com.company.iss.notification.entity;

import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notification_settings")
@Getter
@Setter
public class NotificationSettings extends BaseEntity {

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private Boolean emailEnabled = false;

    @Column(nullable = false)
    private Boolean smsEnabled = false;

    @Column
    private String smtpHost;

    @Column
    private Integer smtpPort;

    @Column
    private String smtpUsername;

    @Column(length = 1000)
    private String smtpPassword;

    @Column
    private String smtpFromName;

    @Column
    private String smsProvider;

    @Column(length = 2000)
    private String smsApiKey;

    @Column
    private String smsSenderName;

    @Column(nullable = false)
    private Boolean active = true;

}