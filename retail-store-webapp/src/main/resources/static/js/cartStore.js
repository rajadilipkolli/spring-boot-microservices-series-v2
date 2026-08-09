const getCartKey = function() {
    const userMeta = document.querySelector('meta[name="_user_identifier"]');
    const userIdentifier = (userMeta && userMeta.content) ? userMeta.content : 'ANONYMOUS';
    return "RETAILSTORE_STATE_" + userIdentifier;
};

const getCart = function() {
    let key = getCartKey();
    let cart = localStorage.getItem(key)
    if (!cart) {
        cart = JSON.stringify({items:[], totalAmount:0 });
        localStorage.setItem(key, cart)
    }
    return JSON.parse(cart)
}

const addProductToCart = function(product) {
    let cart = getCart();
    let cartItem = cart.items.find(itemModel => itemModel.productCode === product.productCode);
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
    cart.totalAmount = getCartTotal();
    localStorage.setItem(getCartKey(), JSON.stringify(cart));
    updateCartItemCount();
    document.dispatchEvent(new CustomEvent('cart-updated', { detail: cart }));
}

const updateProductQuantity = function(code, quantity) {
    let cart = getCart();
    if(quantity < 1) {
        cart.items = cart.items.filter(itemModel => itemModel.productCode !== code);
    } else {
        let cartItem = cart.items.find(itemModel => itemModel.productCode === code);
        if (cartItem) {
            cartItem.quantity = parseInt(quantity);
        } else {
            console.log("Product code is not already in Cart, ignoring")
        }
    }
    cart.totalAmount = getCartTotal();
    localStorage.setItem(getCartKey(), JSON.stringify(cart));
    updateCartItemCount();
    document.dispatchEvent(new CustomEvent('cart-updated', { detail: cart }));
}

const deleteCart = function() {
    localStorage.removeItem(getCartKey())
    updateCartItemCount();
}

function updateCartItemCount() {
    let cart = getCart();
    let count = cart.items.length;
    $('#cart-item-count').text('(' + count + ')');
}

function getCartTotal() {
    let cart = getCart();
    let totalAmount = 0;
    cart.items.forEach(item => {
        totalAmount = totalAmount + (item.price * item.quantity);
    });
    return totalAmount.toFixed(2);
}
