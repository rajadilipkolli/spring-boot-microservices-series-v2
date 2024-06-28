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
//                console.log("Catalogs Resp:", resp)
                this.catalogs = resp;
            });
        },
        updateInventory(inventory) {
            let inventoryForm = Object.assign({}, this.inventory);

            $.ajax ({
                url: '/inventory',
                type: "PUT",
                dataType: "json",
                contentType: "application/json",
                data : JSON.stringify(inventoryForm),
                success: (resp) => {
//                     console.log("InventoryUpdate Resp:", resp)
                    alert("Inventory Updated successfully")
                },
                error: (err) => {
                    console.log("Inventory Update Error:", err)
                    if (err.status === 404) {
                        alert("Inventory item not found.");
                    } else if (err.status === 500) {
                        alert("Server error, please try again later.");
                    } else {
                        alert("Failed to update inventory. Please check your input and try again.");
                    }
                }
            });
        }
    }))
});
