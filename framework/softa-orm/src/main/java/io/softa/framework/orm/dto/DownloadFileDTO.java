package io.softa.framework.orm.dto;

import java.io.IOException;
import java.net.HttpURLConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.orm.domain.FileStream;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadFileDTO {

    @Schema(description = "File Stream")
    private FileStream fileStream;

    @Schema(description = "HTTP Connection")
    private HttpURLConnection connection;

    /**
     * Close the connection and input stream
     */
    public void close() {
        if (fileStream.getInputStream() != null) {
            try {
                fileStream.getInputStream().close();
            } catch (IOException e) {
                log.warn("Failed to close input stream", e);
            }
        }
        if (connection != null) {
            connection.disconnect();
        }
    }
}
