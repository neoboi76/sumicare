/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "package_tiers")
public class PackageTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "weekday_price", nullable = false)
    private BigDecimal weekdayPrice;

    @Column(name = "weekend_price", nullable = false)
    private BigDecimal weekendPrice;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public BigDecimal getWeekdayPrice() { return weekdayPrice; }
    public void setWeekdayPrice(BigDecimal weekdayPrice) { this.weekdayPrice = weekdayPrice; }
    public BigDecimal getWeekendPrice() { return weekendPrice; }
    public void setWeekendPrice(BigDecimal weekendPrice) { this.weekendPrice = weekendPrice; }
}
