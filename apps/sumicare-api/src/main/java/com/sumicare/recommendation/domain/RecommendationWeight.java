/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.recommendation.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "recommendation_weights")
public class RecommendationWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "question_code", nullable = false)
    private String questionCode;

    @Column(name = "option_code", nullable = false)
    private String optionCode;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "weight", nullable = false)
    private int weight;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getQuestionCode() { return questionCode; }
    public void setQuestionCode(String questionCode) { this.questionCode = questionCode; }
    public String getOptionCode() { return optionCode; }
    public void setOptionCode(String optionCode) { this.optionCode = optionCode; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
}
