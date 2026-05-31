package xxx.com.web.poster;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.io.IOException;

/**
 * A data class to hold information about a downloaded resource.
 * Can be backed by a byte array in memory or a temporary file on disk.
 */
public class Resource {
    private final String url;
    private final String contentType;
    private final byte[] body;
    private final File bodyFile;
    private final String displayName;

    public Resource(String url, String contentType, byte[] body) {
        this.url = url;
        this.contentType = contentType;
        this.body = body;
        this.bodyFile = null;
        this.displayName = extractDisplayName(url);
    }

    public Resource(String url, String contentType, File bodyFile) {
        this.url = url;
        this.contentType = contentType;
        this.body = null;
        this.bodyFile = bodyFile;
        this.displayName = extractDisplayName(url);
    }

    private String extractDisplayName(String urlString) {
        try {
            URL url = new URL(urlString);
            String path = url.getPath();

            // If path is empty or just "/", use the host
            if (path == null || path.isEmpty() || path.equals("/")) {
                return url.getHost();
            }

            // Extract filename from path
            String filename = path.substring(path.lastIndexOf('/') + 1);

            // If filename is empty, try to get a meaningful name from the path
            if (filename.isEmpty()) {
                // Remove leading/trailing slashes and use the last path segment
                path = path.replaceAll("^/|/$", "");
                if (path.contains("/")) {
                    filename = path.substring(path.lastIndexOf('/') + 1);
                } else {
                    filename = path;
                }
            }

            // If still empty, use the host
            if (filename.isEmpty()) {
                return url.getHost();
            }

            // Decode URL encoding if present
            filename = java.net.URLDecoder.decode(filename, "UTF-8");

            // Truncate very long filenames but keep the extension
            if (filename.length() > 40) {
                String extension = "";
                int lastDot = filename.lastIndexOf('.');
                if (lastDot > 0) {
                    extension = filename.substring(lastDot);
                    filename = filename.substring(0, Math.min(30, lastDot)) + "..." + extension;
                } else {
                    filename = filename.substring(0, 37) + "...";
                }
            }

            return filename;
        } catch (Exception e) {
            // Fallback to simple extraction
            if (urlString.length() > 50) {
                return "..." + urlString.substring(urlString.length() - 47);
            }
            return urlString;
        }
    }

    public String getUrl() {
        return url;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isFileBacked() {
        return bodyFile != null;
    }

    public File getBodyFile() {
        return bodyFile;
    }

    public byte[] getBody() {
        if (body != null) {
            return body;
        }
        if (bodyFile != null) {
            try {
                // This is a fallback and should be avoided for large files in the UI thread.
                // The UI should handle file streaming directly.
                return Files.readAllBytes(bodyFile.toPath());
            } catch (IOException e) {
                System.err.println("Error reading file-backed resource body: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }
}