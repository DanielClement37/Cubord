export interface ErrorResponse {
    error_code: string;
    message: string;
    /** ISO 8601 datetime (UTC) */
    timestamp: string;
    correlation_id: string;
    details?: Record<string, unknown> | null;
}