package se360.trip_service.model.enums;

/**
 * Result of a trip accept attempt.
 * Used to provide clear feedback to the driver about the outcome.
 */
public enum AcceptResult {
    /**
     * Trip was successfully assigned to this driver.
     */
    SUCCESS,

    /**
     * Trip has already been assigned to another driver.
     * Either the trip status was not SEARCHING, or another driver acquired the lock
     * first.
     */
    ALREADY_ASSIGNED,

    /**
     * Trip was not found in the database.
     */
    TRIP_NOT_FOUND
}
