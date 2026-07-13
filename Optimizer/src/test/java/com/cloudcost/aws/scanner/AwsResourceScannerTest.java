package com.cloudcost.aws.scanner;

import com.cloudcost.aws.normalizer.ResourceData;
import com.cloudcost.exception.ResourceScanException;
import com.cloudcost.model.ResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.*;
import software.amazon.awssdk.services.ebs.EbsClient;
import software.amazon.awssdk.services.ebs.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.List;
import java.util.Map;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwsResourceScannerTest {

    @Mock
    private Ec2Client ec2Client;

    @Mock
    private RdsClient rdsClient;

    @Mock
    private EbsClient ebsClient;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private AwsResourceScanner scanner;

    @Test
    void testScanEc2Instances() {
        Instance instance = Instance.builder()
            .instanceId("i-123")
            .instanceType(InstanceType.M5_LARGE)
            .state(InstanceState.builder().name(InstanceStateName.RUNNING).build())
            .cpuOptions(CpuOptions.builder().coreCount(2).build())
            .memoryInfo(MemoryInfo.builder().sizeInMiB(8192).build())
            .vpcId("vpc-123")
            .subnetId("subnet-123")
            .tags(Tag.builder().key("Name").value("test-instance").build())
            .build();

        Reservation reservation = Reservation.builder().instances(instance).build();
        DescribeInstancesResponse response = DescribeInstancesResponse.builder().reservations(reservation).build();

        when(ec2Client.describeInstances(any(DescribeInstancesRequest.class))).thenReturn(response);

        List<ResourceData> results = scanner.scanEc2Instances("us-east-1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).resourceId()).isEqualTo("i-123");
        assertThat(results.get(0).name()).isEqualTo("m5.large");
        assertThat(results.get(0).metrics().get("cpuCores")).isEqualTo(2.0);
        assertThat(results.get(0).metrics().get("memoryGiB")).isEqualTo(8.0);
        verify(ec2Client).describeInstances(any(DescribeInstancesRequest.class));
    }

    @Test
    void testScanRdsInstances() {
        DBInstance instance = DBInstance.builder()
            .dbInstanceIdentifier("db-123")
            .dbInstanceClass("db.t3.medium")
            .engine("mysql")
            .engineVersion("8.0")
            .dbInstanceStatus("available")
            .allocatedStorage(100)
            .multiAZ(false)
            .tagList(Tag.builder().key("Environment").value("Production").build())
            .build();

        DescribeDbInstancesResponse response = DescribeDbInstancesResponse.builder().dbInstances(instance).build();

        when(rdsClient.describeDBInstances(any(DescribeDbInstancesRequest.class))).thenReturn(response);

        List<ResourceData> results = scanner.scanRdsInstances("us-east-1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).resourceId()).isEqualTo("db-123");
        assertThat(results.get(0).name()).isEqualTo("db.t3.medium");
        assertThat(results.get(0).additionalInfo().get("engine")).isEqualTo("mysql");
        verify(rdsClient).describeDBInstances(any(DescribeDbInstancesRequest.class));
    }

    @Test
    void testScanEbsVolumes() {
        Volume volume = Volume.builder()
            .volumeId("vol-123")
            .volumeType(VolumeType.GP3)
            .size(100)
            .iops(3000)
            .state(VolumeState.AVAILABLE)
            .availabilityZone("us-east-1a")
            .encrypted(true)
            .tags(Tag.builder().key("Name").value("data-volume").build())
            .build();

        DescribeVolumesResponse response = DescribeVolumesResponse.builder().volumes(volume).build();

        when(ebsClient.describeVolumes(any(DescribeVolumesRequest.class))).thenReturn(response);

        List<ResourceData> results = scanner.scanEbsVolumes("us-east-1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).resourceId()).isEqualTo("vol-123");
        assertThat(results.get(0).metrics().get("sizeGiB")).isEqualTo(100.0);
        assertThat(results.get(0).metrics().get("iops")).isEqualTo(3000.0);
        verify(ebsClient).describeVolumes(any(DescribeVolumesRequest.class));
    }

    @Test
    void testScanElasticIps() {
        Address address = Address.builder()
            .allocationId("eipalloc-123")
            .publicIp("203.0.113.1")
            .domain(DomainType.VPC)
            .instanceId("i-123")
            .tags(Tag.builder().key("Name").value("my-eip").build())
            .build();

        DescribeAddressesResponse response = DescribeAddressesResponse.builder().addresses(address).build();

        when(ec2Client.describeAddresses(any(DescribeAddressesRequest.class))).thenReturn(response);

        List<ResourceData> results = scanner.scanElasticIps("us-east-1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).resourceId()).isEqualTo("eipalloc-123");
        assertThat(results.get(0).name()).isEqualTo("203.0.113.1");
        assertThat(results.get(0).additionalInfo().get("instanceId")).isEqualTo("i-123");
        verify(ec2Client).describeAddresses(any(DescribeAddressesRequest.class));
    }

    @Test
    void testScanS3Buckets() {
        Bucket bucket = Bucket.builder()
            .name("my-bucket")
            .creationDate(java.time.Instant.now())
            .build();

        ListBucketsResponse response = ListBucketsResponse.builder().buckets(bucket).build();

        when(s3Client.listBuckets()).thenReturn(response);
        when(s3Client.getBucketLocation(any(GetBucketLocationRequest.class)))
            .thenReturn(GetBucketLocationResponse.builder().locationConstraint(BucketLocationConstraint.US_EAST_1).build());

        List<ResourceData> results = scanner.scanS3Buckets("us-east-1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).resourceId()).isEqualTo("my-bucket");
        verify(s3Client).listBuckets();
    }

    @Test
    void testScanRegion() {
        // Mock EC2
        Instance ec2Instance = Instance.builder()
            .instanceId("i-123")
            .instanceType(InstanceType.M5_LARGE)
            .state(InstanceState.builder().name(InstanceStateName.RUNNING).build())
            .cpuOptions(CpuOptions.builder().coreCount(2).build())
            .memoryInfo(MemoryInfo.builder().sizeInMiB(8192).build())
            .build();

        DescribeInstancesResponse ec2Response = DescribeInstancesResponse.builder()
            .reservations(Reservation.builder().instances(ec2Instance).build())
            .build();
        when(ec2Client.describeInstances(any(DescribeInstancesRequest.class))).thenReturn(ec2Response);

        // Mock RDS
        DBInstance rdsInstance = DBInstance.builder()
            .dbInstanceIdentifier("db-123")
            .dbInstanceClass("db.t3.medium")
            .engine("postgres")
            .dbInstanceStatus("available")
            .allocatedStorage(50)
            .multiAZ(false)
            .build();

        DescribeDbInstancesResponse rdsResponse = DescribeDbInstancesResponse.builder().dbInstances(rdsInstance).build();
        when(rdsClient.describeDBInstances(any(DescribeDbInstancesRequest.class))).thenReturn(rdsResponse);

        // Mock EBS
        Volume ebsVolume = Volume.builder()
            .volumeId("vol-123")
            .volumeType(VolumeType.GP3)
            .size(50)
            .state(VolumeState.AVAILABLE)
            .availabilityZone("us-east-1a")
            .encrypted(false)
            .build();

        DescribeVolumesResponse ebsResponse = DescribeVolumesResponse.builder().volumes(ebsVolume).build();
        when(ebsClient.describeVolumes(any(DescribeVolumesRequest.class))).thenReturn(ebsResponse);

        // Mock Elastic IPs
        DescribeAddressesResponse eipResponse = DescribeAddressesResponse.builder()
            .addresses(Address.builder().allocationId("eip-123").publicIp("1.2.3.4").domain(DomainType.VPC).build())
            .build();
        when(ec2Client.describeAddresses(any(DescribeAddressesRequest.class))).thenReturn(eipResponse);

        // Mock S3
        Bucket s3Bucket = Bucket.builder().name("test-bucket").creationDate(java.time.Instant.now()).build();
        ListBucketsResponse s3Response = ListBucketsResponse.builder().buckets(s3Bucket).build();
        when(s3Client.listBuckets()).thenReturn(s3Response);
        when(s3Client.getBucketLocation(any(GetBucketLocationRequest.class)))
            .thenReturn(GetBucketLocationResponse.builder().locationConstraint(BucketLocationConstraint.US_EAST_1).build());

        Map<ResourceType, List<ResourceData>> results = scanner.scanRegion("123456789012", "us-east-1");

        assertThat(results).containsKeys(
            ResourceType.EC2_INSTANCE,
            ResourceType.RDS_INSTANCE,
            ResourceType.EBS_VOLUME,
            ResourceType.ELASTIC_IP,
            ResourceType.S3_BUCKET
        );
        assertThat(results.get(ResourceType.EC2_INSTANCE)).hasSize(1);
        assertThat(results.get(ResourceType.RDS_INSTANCE)).hasSize(1);
        assertThat(results.get(ResourceType.EBS_VOLUME)).hasSize(1);
        assertThat(results.get(ResourceType.ELASTIC_IP)).hasSize(1);
        assertThat(results.get(ResourceType.S3_BUCKET)).hasSize(1);
    }
}