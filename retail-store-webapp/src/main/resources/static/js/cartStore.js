const authChannel = new BroadcastChannel('retailstore_auth_channel');

authChannel.onmessage = function(event) {
    if (event.data === 'CART_UPDATED') {
        fetchCart().then(cart => {
            updateCartItemCountUI(cart.items ? cart.items.length : 0);
            document.dispatchEvent(new CustomEvent('cart-updated', { detail: cart }));
        });
    }
};

async function fetchCart() {
    try {
        const response = await fetch('/api/cart');
        if (response.ok) {
            return await response.json();
        }
    } catch (e) {
        console.error("Failed to fetch cart", e);
    }
    return { items: [], totalAmount: 0 };
}

async function saveCart(cart) {
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
    const headers = { 'Content-Type': 'application/json' };
    if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;

    try {
        const response = await fetch('/api/cart', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify(cart)
        });
        if (response.ok) {
            const updatedCart = await response.json();
            authChannel.postMessage('CART_UPDATED');
            updateCartItemCountUI(updatedCart.items ? updatedCart.items.length : 0);
            document.dispatchEvent(new CustomEvent('cart-updated', { detail: updatedCart }));
        } else {
            throw new Error(`Failed to save cart: ${response.status}`);
        }
    } catch (e) {
        console.error("Failed to save cart", e);
        // On conflict, refresh cart
        authChannel.postMessage('CART_UPDATED');
    }
}

async function addProductToCart(product) {
    let cart = await fetchCart();
    if (!cart.items) cart.items = [];
    
    let cartItem = cart.items.find(item => item.productCode === product.productCode);
    if (cartItem) {
        cartItem.quantity = parseInt(cartItem.quantity) + 1;
    } else {
        cart.items.push({
            productCode: product.productCode,
            productName: product.productName,
            price: product.price,
            quantity: 1
        });
    }
    cart.totalAmount = calculateTotal(cart);
    await saveCart(cart);
}

async function updateProductQuantity(code, quantity) {
    let cart = await fetchCart();
    if (!cart.items) cart.items = [];

    if (quantity < 1) {
        cart.items = cart.items.filter(item => item.productCode !== code);
    } else {
        let cartItem = cart.items.find(item => item.productCode === code);
        if (cartItem) cartItem.quantity = parseInt(quantity);
    }
    cart.totalAmount = calculateTotal(cart);
    await saveCart(cart);
}

async function deleteCart() {
    let cart = await fetchCart();
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
    const headers = {};
    if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;

    let url = '/api/cart';
    if (cart && cart.revision) {
        url += `?revision=${encodeURIComponent(cart.revision)}`;
    }

    try {
        const response = await fetch(url, { method: 'DELETE', headers });
        if (response.ok) {
            authChannel.postMessage('CART_UPDATED');
            let emptyCart = { items: [], totalAmount: 0 };
            updateCartItemCountUI(0);
            document.dispatchEvent(new CustomEvent('cart-updated', { detail: emptyCart }));
        } else {
            throw new Error(`Failed to delete cart: ${response.status}`);
        }
    } catch (e) {
        console.error("Failed to delete cart", e);
        // On conflict, refresh cart
        authChannel.postMessage('CART_UPDATED');
    }
}

function calculateTotal(cart) {
    if (!cart || !cart.items) return 0;
    return parseFloat(cart.items.reduce((total, item) => total + (item.price * item.quantity), 0).toFixed(2));
}

function updateCartItemCountUI(count) {
    const badge = document.getElementById('cart-item-count');
    if (badge) badge.innerText = '(' + count + ')';
}

// Since Alpine calls getCartTotal occasionally or synchronously, we return totalAmount if available, but mostly it's fetched asynchronously.
// To avoid breaking legacy code that expects getCart() synchronously:
let localCachedCart = { items: [], totalAmount: 0 };

// Initial load
fetchCart().then(cart => {
    localCachedCart = cart;
    updateCartItemCountUI(cart.items ? cart.items.length : 0);
});

document.addEventListener('cart-updated', (e) => {
    localCachedCart = e.detail;
});
const getCart = function() {
    return localCachedCart;
}
const getCartTotal = function() {
    return calculateTotal(localCachedCart);
}
