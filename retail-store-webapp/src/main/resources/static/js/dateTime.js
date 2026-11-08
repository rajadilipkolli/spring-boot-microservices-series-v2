/**
 * Global date and time formatting helper.
 * Formats ISO instants using the browser's local timezone.
 */
function formatDateTime(isoString) {
    if (!isoString) {
        return '-';
    }
    const date = new Date(isoString);
    if (isNaN(date.getTime())) {
        return isoString;
    }
    try {
        const formatter = new Intl.DateTimeFormat(undefined, {
            dateStyle: 'medium',
            timeStyle: 'short'
        });
        return formatter.format(date);
    } catch (e) {
        return isoString;
    }
}
