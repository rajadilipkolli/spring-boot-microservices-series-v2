const customerDetails = document.getElementById('customer-details').dataset;
const getAddressPart = (index, defaultValue) => {
    const part = customerDetails.address?.split(',')[index]?.trim();
    return part || defaultValue;
};

document.addEventListener('alpine:init', () => {
    Alpine.data('initData', () => ({
        cart: { items: [], totalAmount: 0 },
        orderForm: {
            customer: {
                name: customerDetails.name || "Siva",
                email: customerDetails.email || "siva@gmail.com",
                phone: customerDetails.phone || "999999999999"
            },
            deliveryAddress: {
                addressLine1: getAddressPart(0, null) || "KPHB",
                addressLine2: getAddressPart(1, '') || "Kukatpally",
                city: getAddressPart(2, '') || "Hyderabad",
                state: getAddressPart(3, '') || "TS",
                zipCode: getAddressPart(4, '') || "500072",
                country: getAddressPart(5, '') || "India"
            }
        },

        init() {
            fetchCart().then(cart => {
                this.cart = cart;
                localCachedCart = cart;
            });
            document.addEventListener('cart-updated', (event) => {
                this.cart = event.detail;
            });
        },
        loadCart() {
            this.cart = getCart();
            this.cart.totalAmount = getCartTotal();
        },
        updateItemQuantity(code, quantity) {
            updateProductQuantity(code, quantity);
            this.loadCart();
        },
        removeCart() {
            deleteCart();
        },
        removeItemFromCart(code) {
            this.updateItemQuantity(code, 0);
        },
        createOrder() {
            let order = Object.assign({}, this.orderForm, {items: this.cart.items});
            //console.log("Order ", order);
            const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
            const headers = { 'Content-Type': 'application/json' };
            if (csrfHeader && csrfToken) {
                headers[csrfHeader] = csrfToken;
            }
            
            fetch('/api/orders', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify(order)
            })
            .then(response => {
                if (!response.ok) {
                    throw new Error("Order creation failed");
                }
                return response.json();
            })
            .then(resp => {
                this.removeCart();
                window.location = "/orders/" + resp.orderId;
            })
            .catch(err => {
                console.error("Order Creation Error:", err);
                alert("Order creation failed");
            });
        },
    }))
});
