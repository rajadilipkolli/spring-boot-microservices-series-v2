document.addEventListener('alpine:init', () => {
    Alpine.data('initData', (pageNo) => ({
        pageNo: pageNo,
        catalogs: {
            data: []
        },
        init() {
            this.loadInventories(this.pageNo);
        },
        loadInventories(pageNo) {
            $.getJSON("/api/inventory?page="+pageNo, (resp)=> {
                console.log("Catalogs Resp:", resp)
                this.catalogs = resp;
            });
        }
    }))
});
