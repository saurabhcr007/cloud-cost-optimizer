package com.cloudcost.model;

public enum ResourceType {
    EC2_INSTANCE("ec2"),
    RDS_INSTANCE("rds"),
    EBS_VOLUME("ebs"),
    ELASTIC_IP("eip"),
    S3_BUCKET("s3"),
    SNAPSHOT("snapshot"),
    UNKNOWN("unknown");

    private final String value;

    ResourceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ResourceType fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (ResourceType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}