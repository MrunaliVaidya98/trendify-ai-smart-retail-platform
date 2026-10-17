package com.trendify.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoConfig {
  @Bean DynamoDbClient dynamo(@Value("${trendify.aws.region}") String region,
      @Value("${trendify.aws.dynamodb-endpoint:}") String endpoint) {
    var builder = DynamoDbClient.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.create());
    if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
    return builder.build();
  }
}
