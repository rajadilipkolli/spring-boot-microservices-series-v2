document.addEventListener('alpine:init', () => {
    Alpine.data('initData', () => ({
        pageNo: 0,
        products: {
            data: [],
            totalPages: 0,
            totalElements: 0,
            pageNumber: 0,
            isFirst: true,
            isLast: false,
            hasNext: false,
            hasPrevious: false
        },
        init() {
            this.loadProducts();
            updateCartItemCount();
        },
        loadProducts() {
            fetch(`/api/products?page=${this.pageNo}`)
                .then(response => response.json())
                .then(data => {
                    this.products = data;
                    this.pageNo = data.pageNumber - 1;
                })
                .catch(error => console.error('Error loading products:', error));
        },
        addToCart(product) {
            addProductToCart(product)
        },
        goToPage(pageNo) {
            if (pageNo >= 0 && pageNo < this.products.totalPages) {
                this.pageNo = pageNo;
                this.loadProducts();
            }
        },
        nextPage() {
            if (this.products.hasNext) {
                this.goToPage(this.products.pageNumber);
            }
        },
        previousPage() {
            if (this.products.hasPrevious) {
                this.goToPage(this.products.pageNumber - 2);
            }
        },
        firstPage() {
            if (!this.products.isFirst) {
                this.goToPage(0);
            }
        },
        lastPage() {
            if (!this.products.isLast) {
                this.goToPage(this.products.totalPages - 1);
            }
        }
    }))
});