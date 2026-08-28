package io.softa.framework.orm.oss.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.softa.framework.orm.oss.OSSProperties;
import io.softa.framework.orm.oss.OssClientService;
import io.softa.framework.orm.oss.impl.AliyunOSSClientService;

// IDEA cannot resolve `oss.type` for a library module, so its Spring model treats this
// @ConditionalOnProperty class as inactive and flags the @Bean methods declared right here as missing at
// the injection point below. Suppressed rather than worked around: the minio and aliyun configs are
// mutually exclusive by design, so no property value can make both look active. Compilation and runtime
// wiring are unaffected — this is an IDE model limitation, not a wiring problem.
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
@ConditionalOnProperty(value = "oss.type", havingValue = "aliyun")
public class AliyunOSSConfig {

    @Autowired
    private OSSProperties ossProperties;

    @Bean
    public OSS oss() {
        return buildClient(ossProperties.getEndpoint());
    }

    private OSS buildClient(String endpoint) {
        ClientBuilderConfiguration conf = new ClientBuilderConfiguration();
        conf.setConnectionTimeout(2000);
        conf.setIdleConnectionTime(10000);
        conf.setMaxErrorRetry(0);
        return new OSSClientBuilder()
                .build(endpoint, ossProperties.getAccessKey(), ossProperties.getSecretKey(), conf);
    }

    @Bean
    @ConditionalOnBean({OSS.class})
    @ConditionalOnMissingBean(OssClientService.class)
    public AliyunOSSClientService aliyunOSSClientService(OSS oss) {
        // See MinioConfig#minioClientService for why pre-signing needs its own client and why it is not a
        // @Bean. Here the split is the norm rather than the exception: an ECS reaching OSS over
        // `oss-<region>-internal.aliyuncs.com` (free egress, and the only route available without public
        // network access) hands the browser an address it can never resolve.
        String presignEndpoint = ossProperties.resolvePresignEndpoint();
        OSS presignOss = presignEndpoint.equals(ossProperties.getEndpoint())
                ? oss
                : buildClient(presignEndpoint);
        return new AliyunOSSClientService(oss, presignOss, ossProperties);
    }
}
