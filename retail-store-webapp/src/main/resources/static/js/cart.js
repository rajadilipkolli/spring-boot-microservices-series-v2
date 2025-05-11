document.addEventListener('alpine:init', () => {
    Alpine.data('initData', () => ({
        cart: { items: [], totalAmount: 0 },
        orderForm: {
            customer: {
                name: "Siva",
                email: "siva@gmail.com",
                phone: "999999999999"
            },
            deliveryAddress: {
                addressLine1: "KPHB",
                addressLine2: "Kukatpally",
                city:"Hyderabad",
                state: "TS",
                zipCode: "500072",
                country: "India"
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
        createOrder() {
            let order = Object.assign({}, this.orderForm, {items: this.cart.items});
            //console.log("Order ", order);

            $.ajax ({
                url: '/api/orders',
                type: "POST",
                dataType: "json",
                contentType: "application/json",
                data : JSON.stringify(order),
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
