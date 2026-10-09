document.addEventListener('alpine:init', () => {
    Alpine.data('initData', () => ({
        orders: [],
        /** Loads the initial orders when Alpine initializes this component. */
        init() {
            this.loadOrders();
        },
        loadOrders() {
            $.getJSON("/api/orders", (data) => {
                //console.log("orders :", data)
                this.orders = data
            });
        },
    }))
});
