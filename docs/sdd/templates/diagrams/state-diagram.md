# State Diagram

```mermaid
stateDiagram-v2
    [*] --> Active : Creation
    Active --> Active : Inventory Adjustment / Update
    Active --> Reserved : Stock Reservation
    Reserved --> Active : Rollback
    Reserved --> [*] : Confirmation / Fulfillment
    Active --> Deleted : Soft Deletion
    Deleted --> Active : Restoration
```
