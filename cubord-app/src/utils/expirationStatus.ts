/**
 * Derives expiration status from a pantry item's expiration date.
 *
 * Rules:
 *   - No date           → 'none'
 *   - Date < today      → 'expired'
 *   - Date ≤ today + 7d → 'expiring'
 *   - Otherwise         → 'fresh'
 */

export type ExpirationStatus = 'expired' | 'expiring' | 'fresh' | 'none';

/**
 * The number of days from today within which an item is considered "expiring soon".
 */
const EXPIRING_THRESHOLD_DAYS = 7;

/**
 * Returns the start of today (midnight) in local time, with time zeroed out
 * so date-only comparisons work correctly.
 */
function startOfToday(): Date {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

/**
 * Parses an ISO date string (YYYY-MM-DD) into a local-midnight Date.
 * Returns `null` for invalid strings.
 */
function parseLocalDate(iso: string): Date | null {
    const parts = iso.split('-');
    if (parts.length !== 3) return null;
    const [y, m, d] = parts.map(Number);
    if (isNaN(y) || isNaN(m) || isNaN(d)) return null;
    return new Date(y, m - 1, d);
}

/**
 * Returns the number of calendar days between two dates (positive if `to` is in the future).
 */
function diffDays(from: Date, to: Date): number {
    const MS_PER_DAY = 86_400_000;
    return Math.round((to.getTime() - from.getTime()) / MS_PER_DAY);
}

/**
 * Computes the expiration status for a given expiration date string.
 *
 * @param expirationDate  ISO date (YYYY-MM-DD) or `null`/`undefined`
 */
export function getExpirationStatus(
    expirationDate: string | null | undefined,
): ExpirationStatus {
    if (!expirationDate) return 'none';

    const expDate = parseLocalDate(expirationDate);
    if (!expDate) return 'none';

    const today = startOfToday();
    const daysUntilExpiry = diffDays(today, expDate);

    if (daysUntilExpiry < 0) return 'expired';
    if (daysUntilExpiry <= EXPIRING_THRESHOLD_DAYS) return 'expiring';
    return 'fresh';
}

/**
 * Returns a human-readable label describing the expiration status.
 *
 * Examples:
 *   - "Expired 3 days ago"
 *   - "Expires today"
 *   - "Expires in 2 days"
 *   - "Fresh"
 *   - "No expiration"
 *
 * @param expirationDate  ISO date (YYYY-MM-DD) or `null`/`undefined`
 */
export function getExpirationLabel(
    expirationDate: string | null | undefined,
): string {
    if (!expirationDate) return 'No expiration';

    const expDate = parseLocalDate(expirationDate);
    if (!expDate) return 'No expiration';

    const today = startOfToday();
    const daysUntilExpiry = diffDays(today, expDate);

    if (daysUntilExpiry < 0) {
        const ago = Math.abs(daysUntilExpiry);
        return ago === 1 ? 'Expired 1 day ago' : `Expired ${ago} days ago`;
    }
    if (daysUntilExpiry === 0) return 'Expires today';
    if (daysUntilExpiry === 1) return 'Expires in 1 day';
    if (daysUntilExpiry <= EXPIRING_THRESHOLD_DAYS) return `Expires in ${daysUntilExpiry} days`;
    return 'Fresh';
}

/**
 * Returns the number of calendar days until the item expires.
 * Negative values mean the item is already expired.
 * Returns `null` if no expiration date is set or the date is invalid.
 *
 * @param expirationDate  ISO date (YYYY-MM-DD) or `null`/`undefined`
 */
export function getDaysUntilExpiry(
    expirationDate: string | null | undefined,
): number | null {
    if (!expirationDate) return null;

    const expDate = parseLocalDate(expirationDate);
    if (!expDate) return null;

    return diffDays(startOfToday(), expDate);
}