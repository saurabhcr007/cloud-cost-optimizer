package com.cloudcost.aws.scanner;

import com.cloudcost.aws.normalizer.ResourceData;
import com.cloudcost.model.ResourceType;
import com.cloudcost.exception.ResourceScanException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBInstance;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesRequest;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.ebs.EbsClient;
import software.amazon.awssdk.services.ebs.model.Volume;
import software.amazon.awssdk.services.ebs.model.DescribeVolumesRequest;
import software.amazon.awssdk.services.ebs.model.DescribeVolumesResponse;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AwsResourceScanner {

    private final Ec2Client ec2Client;
    private final RdsClient rdsClient;
    private final EbsClient ebsClient;
    private final S3Client s3Client;

    public AwsResourceScanner(Ec2Client ec2Client, RdsClient rdsClient, EbsClient ebsClient, S3Client s3Client) {
        this.ec2Client = ec2Client;
        this.rdsClient = rdsClient;
        this.ebsClient = ebsClient;
        this.s3Client = s3Client;
    }

    @Retryable(value = { Exception.class }, maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 1000, multiplier = 2))
    public Map<ResourceType, List<ResourceData>> scanRegion(String accountId, String region) {
        Map<ResourceType, List<ResourceData>> results = new HashMap<>();

        try {
            results.put(ResourceType.EC2_INSTANCE, scanEc2Instances(region));
            results.put(ResourceType.RDS_INSTANCE, scanRdsInstances(region));
            results.put(ResourceType.EBS_VOLUME, scanEbsVolumes(region));
            results.put(ResourceType.ELASTIC_IP, scanElasticIps(region));
            results.put(ResourceType.S3_BUCKET, scanS3Buckets(region));
        } catch (Exception e) {
            throw new ResourceScanException("Failed to scan region: " + region, e);
        }

        return results;
    }

    public List<ResourceData> scanEc2Instances(String region) {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
            .filters(Filter.builder().name("instance-state-name").values("running").build())
            .build();

        DescribeInstancesResponse response = ec2Client.describeInstances(request);

        return response.reservations().stream()
            .flatMap(r -> r.instances().stream())
            .map(this::mapEc2InstanceToResourceData)
            .collect(Collectors.toList());
    }

    public List<ResourceData> scanRdsInstances(String region) {
        DescribeDbInstancesRequest request = DescribeDbInstancesRequest.builder().build();
        DescribeDbInstancesResponse response = rdsClient.describeDBInstances(request);

        return response.dbInstances().stream()
            .map(this::mapRdsInstanceToResourceData)
            .collect(Collectors.toList());
    }

    public List<ResourceData> scanEbsVolumes(String region) {
        DescribeVolumesRequest request = DescribeVolumesRequest.builder().build();
        DescribeVolumesResponse response = ebsClient.describeVolumes(request);

        return response.volumes().stream()
            .map(this::mapEbsVolumeToResourceData)
            .collect(Collectors.toList());
    }

    public List<ResourceData> scanElasticIps(String region) {
        DescribeAddressesRequest request = DescribeAddressesRequest.builder().build();
        DescribeAddressesResponse response = ec2Client.describeAddresses(request);

        return response.addresses().stream()
            .map(this::mapElasticIpToResourceData)
            .collect(Collectors.toList());
    }

    public List<ResourceData> scanS3Buckets(String region) {
        ListBucketsResponse response = s3Client.listBuckets();

        return response.buckets().stream()
            .filter(bucket -> region.equals(getBucketRegion(bucket.name())))
            .map(this::mapS3BucketToResourceData)
            .collect(Collectors.toList());
    }

    private ResourceData mapEc2InstanceToResourceData(Instance instance) {
        Map<String, String> tags = instance.tags() != null
            ? instance.tags().stream().collect(Collectors.toMap(Tag::key, Tag::value))
            : Collections.emptyMap();

        return ResourceData.builder()
            .resourceId(instance.instanceId())
            .name(instance.instanceTypeAsString())
            .description("EC2 Instance: " + instance.instanceTypeAsString())
            .tags(tags)
            .metrics(Map.of(
                "cpuCores", (double) instance.cpuOptions().coreCount(),
                "memoryGiB", instance.memoryInfo().sizeInMiB() / 1024.0
            ))
            .additionalInfo(Map.of(
                "state", instance.state().nameAsString(),
                "vpcId", instance.vpcId() != null ? instance.vpcId() : "",
                "subnetId", instance.subnetId() != null ? instance.subnetId() : ""
            ))
            .build();
    }

    private ResourceData mapRdsInstanceToResourceData(DBInstance instance) {
        Map<String, String> tags = instance.tagList() != null
            ? instance.tagList().stream().collect(Collectors.toMap(t -> t.key(), t -> t.value()))
            : Collections.emptyMap();

        return ResourceData.builder()
            .resourceId(instance.dbInstanceIdentifier())
            .name(instance.dbInstanceClass())
            .description("RDS Instance: " + instance.engine())
            .tags(tags)
            .metrics(Map.of(
                "allocatedStorage", (double) instance.allocatedStorage(),
                "instanceClass", instance.dbInstanceClass().hashCode() * 1.0
            ))
            .additionalInfo(Map.of(
                "engine", instance.engine(),
                "engineVersion", instance.engineVersion(),
                "status", instance.dbInstanceStatus(),
                "multiAz", String.valueOf(instance.multiAZ())
            ))
            .build();
    }

    private ResourceData mapEbsVolumeToResourceData(Volume volume) {
        Map<String, String> tags = volume.tags() != null
            ? volume.tags().stream().collect(Collectors.toMap(Tag::key, Tag::value))
            : Collections.emptyMap();

        return ResourceData.builder()
            .resourceId(volume.volumeId())
            .name(volume.volumeTypeAsString())
            .description("EBS Volume: " + volume.size() + " GiB")
            .tags(tags)
            .metrics(Map.of(
                "sizeGiB", (double) volume.size(),
                "iops", volume.iops() != null ? (double) volume.iops() : 0.0
            ))
            .additionalInfo(Map.of(
                "state", volume.stateAsString(),
                "availabilityZone", volume.availabilityZone(),
                "encrypted", String.valueOf(volume.encrypted())
            ))
            .build();
    }

    private ResourceData mapElasticIpToResourceData(Address address) {
        Map<String, String> tags = address.tags() != null
            ? address.tags().stream().collect(Collectors.toMap(Tag::key, Tag::value))
            : Collections.emptyMap();

        return ResourceData.builder()
            .resourceId(address.allocationId() != null ? address.allocationId() : address.publicIp())
            .name(address.publicIp())
            .description("Elastic IP: " + address.publicIp())
            .tags(tags)
            .metrics(Collections.emptyMap())
            .additionalInfo(Map.of(
                "publicIp", address.publicIp(),
                "domain", address.domainAsString(),
                "instanceId", address.instanceId() != null ? address.instanceId() : ""
            ))
            .build();
    }

    private ResourceData mapS3BucketToResourceData(Bucket bucket) {
        return ResourceData.builder()
            .resourceId(bucket.name())
            .name(bucket.name())
            .description("S3 Bucket")
            .tags(Collections.emptyMap())
            .metrics(Collections.emptyMap())
            .additionalInfo(Map.of(
                "creationDate", bucket.creationDate().toString()
            ))
            .build();
    }

    private String getBucketRegion(String bucketName) {
        try {
            return s3Client.getBucketLocation(builder -> builder.bucket(bucketName)).locationConstraintAsString();
        } catch (Exception e) {
            return "us-east-1";
        }
    }
}