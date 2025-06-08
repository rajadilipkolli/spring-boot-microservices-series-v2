package simulation;

import io.gatling.javaapi.core.Session;

/**
 * Utility class to help with common Gatling patterns that may vary between versions. This helps
 * isolate version-specific implementation details.
 */
public class GatlingHelper {

    /**
     * Check if a response has a successful status code (200-299)
     *
     * @param session the Gatling session
     * @return true if the last response status code is between 200 and 299
     */
    public static boolean isSuccessResponse(Session session) {
        try {
            String statusStr = session.getString("status");
            if (statusStr != null) {
                int status = Integer.parseInt(statusStr);
                return status >= 200 && status < 300;
            }
        } catch (Exception e) {
            // Fall through to default check
        }

        return false;
    }
}
