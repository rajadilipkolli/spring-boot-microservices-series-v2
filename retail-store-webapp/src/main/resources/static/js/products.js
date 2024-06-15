document.addEventListener('alpine:init', () => {
    Alpine.data('initData', (pageNo) => ({
        pageNo: pageNo,
        products: {
            data: []
        },
        init() {
            this.loadProducts(this.pageNo);
            updateCartItemCount();
        },
        loadProducts(pageNo) {
            $.getJSON("/catalog-service/api/catalog?pageNo="+pageNo, (resp)=> {
                console.log("Products Resp:", resp)
                this.products = resp;
            });
        },
        addToCart(product) {
            addProductToCart(product)
        }
    }))
});