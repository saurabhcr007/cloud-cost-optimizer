package com.cloudcost.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ebs.EbsClient;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsConfig {

    @Value("${aws.region.static:us-east-1}")
    private String region;

    @Value("${aws.credentials.access-key:}")
    private String accessKey;

    @Value("${aws.credentials.secret-key:}")
    private String secretKey;

    @Bean
    public Region awsRegion() {
        return Region.of(region);
    }

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        if (!accessKey.isEmpty() && !secretKey.isEmpty()) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            );
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public Ec2Client ec2Client(Region region, AwsCredentialsProvider credentialsProvider) {
        return Ec2Client.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .build();
    }

    @Bean
    public RdsClient rdsClient(Region region, AwsCredentialsProvider credentialsProvider) {
        return RdsClient.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .build();
    }

    @Bean
    public EbsClient ebsClient(Region region, AwsCredentialsProvider credentialsProvider) {
        return EbsClient.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .build();
    }

    @Bean
    public S3Client s3Client(Region region, AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .build();
    }
}