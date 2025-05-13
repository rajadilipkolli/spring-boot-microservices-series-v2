const RETAILSTORE_STATE_KEY = "RETAILSTORE_STATE";

const getCart = function() {
    let cart = localStorage.getItem(RETAILSTORE_STATE_KEY)
    if (!cart) {
        cart = JSON.stringify({items:[], totalAmount:0 });
        localStorage.setItem(RETAILSTORE_STATE_KEY, cart)
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
    localStorage.setItem(RETAILSTORE_STATE_KEY, JSON.stringify(cart));
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
    localStorage.setItem(RETAILSTORE_STATE_KEY, JSON.stringify(cart));
    updateCartItemCount();
    document.dispatchEvent(new CustomEvent('cart-updated', { detail: cart }));
}

const deleteCart = function() {
    localStorage.removeItem(RETAILSTORE_STATE_KEY)
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
