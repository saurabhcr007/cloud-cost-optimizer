package com.cloudcost.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "aws_resources", indexes = {
    @Index(name = "idx_account_region_type", columnList = "account_id,region,resource_type"),
    @Index(name = "idx_resource_id_type", columnList = "resource_id,resource_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String resourceId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ResourceType resourceType;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String name;

    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resource_tags", joinColumns = @JoinColumn(name = "resource_id"))
    @MapKeyColumn(name = "tag_key")
    @Column(name = "tag_value")
    private Map<String, String> tags;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resource_metrics", joinColumns = @JoinColumn(name = "resource_id"))
    @MapKeyColumn(name = "metric_key")
    @Column(name = "metric_value")
    private Map<String, Double> metrics;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}