/**
 * Validates the registration form and submits its fields to the registration API.
 * Displays validation or request errors, or redirects to login on success.
 */
function registerUser() {
    const form = document.getElementById('registrationForm');
    const formData = new FormData(form);
    const data = {};
    formData.forEach((value, key) => data[key] = value);

    // Basic validation
    if (!validateEmail(data.email)) {
        showError('Invalid email format.');
        return;
    }

    if (data.password !== data.confirmPassword) {
        showError('Passwords do not match.');
        return;
    }

    if (data.password.length < 8) {
        showError('Password must be at least 8 characters long.');
        return;
    }

    if (!validatePasswordComplexity(data.password)) {
        showError('Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character.');
        return;
    }

    fetch('/api/register', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(data)
    })
    .then(response => {
        if (!response.ok) {
            return response.json().then(err => { throw new Error(err.message || 'Registration failed'); });
        }
        return response.json();
    })
    .then(result => {
        // Redirect to login page after successful registration
        window.location.href = '/login?registrationSuccess=true';
    })
    .catch(error => {
        showError(error.message);
    });
}

/**
 * Displays a message as text in the registration error panel and makes it visible.
 * @param {string} message - The error message to display.
 */
function showError(message) {
    const errorDiv = document.getElementById('registrationError');
    errorDiv.textContent = message;
    errorDiv.classList.remove('d-none');
}

/**
 * Checks for uppercase and lowercase letters, a digit, and a supported special character.
 * Password length is checked separately when submitting the form.
 * @param {string} password - The password to check.
 * @returns {boolean} Whether all four character requirements are satisfied.
 */
function validatePasswordComplexity(password) {
    const hasUpper = /[A-Z]/.test(password);
    const hasLower = /[a-z]/.test(password);
    const hasNumber = /\d/.test(password);
    const hasSpecial = /[!@#$%^&*(),.?":{}|<>]/.test(password);
    return hasUpper && hasLower && hasNumber && hasSpecial;
}

/**
 * Checks for non-whitespace text around an at sign and a dot in the domain.
 * @param {string} email - The email address to check.
 * @returns {boolean} Whether the address matches the basic email format.
 */
function validateEmail(email) {
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return re.test(email);
}

document.getElementById('registerButton').addEventListener('click', registerUser);
