package com.company.iss.notification.entity;

import com.company.iss.notification.config.SmtpProvider;
import com.company.iss.notification.config.SmtpSecurity;
import com.company.iss.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column
    private String smtpFromName;

    @Column
    private String smtpFromAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SmtpProvider smtpProvider = SmtpProvider.CUSTOM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SmtpSecurity smtpSecurity = SmtpSecurity.STARTTLS;

    @Column
    private String smsProvider;

    @Column
    private String smsSenderName;

    @Column(nullable = false)
    private Boolean active = true;

}
