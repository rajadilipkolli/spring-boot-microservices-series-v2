/**
 * Formats date and time text using the browser's locale and local timezone.
 *
 * @param {string} isoString date and time text to display
 * @returns {string} '-' for a falsy value, the original text if invalid or formatting fails,
 * or the formatted date and time
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
