package io.softa.framework.orm.oss.config;

import io.minio.MinioClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.softa.framework.orm.oss.OSSProperties;
import io.softa.framework.orm.oss.OssClientService;
import io.softa.framework.orm.oss.impl.MinioClientService;

// IDEA cannot resolve `oss.type` for a library module, so its Spring model treats this
// @ConditionalOnProperty class as inactive and flags the @Bean methods declared right here as missing at
// the injection point below. Suppressed rather than worked around: the minio and aliyun configs are
// mutually exclusive by design, so no property value can make both look active. Compilation and runtime
// wiring are unaffected — this is an IDE model limitation, not a wiring problem.
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
@ConditionalOnProperty(value = "oss.type", havingValue = "minio")
public class MinioConfig {

    @Autowired
    private OSSProperties ossProperties;

    @Bean(name = "minioClient")
    public MinioClient minioClient() {
        return buildClient(ossProperties.getEndpoint());
    }

    private MinioClient buildClient(String endpoint) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(ossProperties.getAccessKey(), ossProperties.getSecretKey());
        if (StringUtils.isNotBlank(ossProperties.getRegion())) {
            builder.region(ossProperties.getRegion());
        } else {
            builder.region("us-east-1"); // Default region for Minio
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnBean(MinioClient.class)
    @ConditionalOnMissingBean(OssClientService.class)
    public MinioClientService minioClientService(MinioClient minioClient) {
        // A pre-signed URL is fetched by the BROWSER, so it must carry a host the browser can resolve —
        // not necessarily the one this process connects over. The host is part of the SigV4 canonical
        // request (`X-Amz-SignedHeaders=host`), so the URL cannot be rewritten afterwards without
        // invalidating the signature: it has to be signed against the public address from the start.
        // Hence a second client when the two addresses differ. Deliberately not a @Bean: a second
        // MinioClient of the same type would make every by-type injection downstream ambiguous, and this
        // one never opens a connection anyway (presigning is local computation, and the region is always
        // set above so no bucket-location lookup fires), so it needs no shutdown hook.
        String presignEndpoint = ossProperties.resolvePresignEndpoint();
        MinioClient presignClient = presignEndpoint.equals(ossProperties.getEndpoint())
                ? minioClient
                : buildClient(presignEndpoint);
        return new MinioClientService(minioClient, presignClient, ossProperties);
    }
}
