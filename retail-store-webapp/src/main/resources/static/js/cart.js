document.addEventListener('alpine:init', () => {
    Alpine.data('initData', () => ({
        cart: { items: [], totalAmount: 0 },
        orderForm: {
            customer: {
                name: window.customerName || "Siva",
                email: window.customerEmail || "siva@gmail.com",
                phone: window.customerPhone || "999999999999"
            },
            deliveryAddress: {
                addressLine1: window.customerAddressLine1 || "KPHB",
                addressLine2: window.customerAddressLine2 || "Kukatpally",
                city: window.customerCity || "Hyderabad",
                state: window.customerState || "TS",
                zipCode: window.customerZipCode || "500072",
                country: window.customerCountry || "India"
            }
        },

        init() {
            this.loadCart();
            updateCartItemCount();
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
            
            $.ajax ({
                url: '/api/orders',
                type: "POST",
                dataType: "json",
                contentType: "application/json",
                data : JSON.stringify(order),
                beforeSend: function(xhr) {
                    if (csrfHeader && csrfToken) {
                        xhr.setRequestHeader(csrfHeader, csrfToken);
                    }
                },
                success: (resp) => {
                    //console.log("Order Resp:", resp)
                    this.removeCart();
                    //alert("Order placed successfully")
                    window.location = "/orders/"+resp.orderId;
                }, error: (err) => {
                    console.log("Order Creation Error:", err)
                    alert("Order creation failed")
                }
            });
        },
    }))
});
