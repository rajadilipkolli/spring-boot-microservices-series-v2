package simulation;

public record InventoryResponseDTO(
        Long id, String productCode, Integer availableQuantity, Integer reservedItems) {
    public InventoryResponseDTO withAvailableQuantity(int nextInt) {
        return new InventoryResponseDTO(id(), productCode(), nextInt, reservedItems());
    }
}
