package io.softa.framework.orm.oss;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@ConfigurationProperties(prefix = "oss")
@Validated
public class OSSProperties {

    @NotBlank(message = "The type of OSS must be specified. The value can be 'minio' or 'aliyun'.")
    private String type;

    @NotBlank(message = "The endpoint of OSS must be specified.")
    private String endpoint;

    // The endpoint pre-signed URLs are built from — the address the BROWSER resolves, which is not always
    // the one this process connects over. Leave blank when `endpoint` is already publicly reachable (S3, a
    // public Aliyun endpoint). Set it whenever the server reaches the store over an address the client
    // cannot resolve: a docker-network hostname (`http://minio:9000`), an Aliyun `-internal` endpoint, a
    // VPC endpoint. Left blank in that case, every URL handed to the browser points at the private address
    // and the download fails in the client — with nothing in the server log to show for it.
    private String presignEndpoint;

    @NotBlank(message = "The access key of OSS must be specified.")
    private String accessKey;

    @NotBlank(message = "The secret key of OSS must be specified.")
    private String secretKey;

    @NotBlank(message = "The bucket name of OSS must be specified.")
    private String bucketName;

    // The region of the OSS service, used to avoid network requests when generating pre-signed URLs.
    // For Minio, it is typically "us-east-1" by default.
    private String region;

    // The optional subdirectory for storing files.
    private String subDir;

    private Integer urlExpireSeconds;

    /**
     * The endpoint actually signed against: {@link #presignEndpoint} when set, {@link #endpoint} otherwise.
     * Deliberately not named `getPresignEndpoint` — that getter is Lombok's, and returns the raw (possibly
     * blank) property; callers want the resolved value and must not reach for the raw one by mistake.
     *
     * @return the endpoint to build pre-signed URLs from
     */
    public String resolvePresignEndpoint() {
        return StringUtils.isBlank(presignEndpoint) ? endpoint : presignEndpoint;
    }
}
