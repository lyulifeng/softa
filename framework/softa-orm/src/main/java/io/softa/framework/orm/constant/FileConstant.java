package io.softa.framework.orm.constant;

/**
 * Constants for file processing.
 */
public interface FileConstant {

    // the default subfolder for the file storage.
    String DEFAULT_SUBFOLDER = "default";

    // Label for the failed data.
    String FAILED_DATA = "Failed Data";

    // Column name for the failed reason.
    String FAILED_REASON = "Failed Reason";

    // Reserved env key set to Boolean.TRUE while the validation-only import pipeline runs, so a
    // side-effectful custom import handler can skip its writes (e.g. provisioning user accounts)
    // and still contribute its row-level validation feedback.
    String VALIDATE_ONLY_ENV = "__validateOnly";

    // The default value of the download URL expiration time in seconds, which is 300 seconds (5 minutes).
    int DEFAULT_DOWNLOAD_URL_EXPIRE = 300;
}
