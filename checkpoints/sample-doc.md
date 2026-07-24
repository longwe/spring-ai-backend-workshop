# ZephyrDB Enterprise — Operations FAQ

ZephyrDB is a fictional distributed time-series database used as workshop test
data. The facts below exist nowhere else, so any correct answer about them must
come from this document via RAG — not from the model's training data.

## Scaling and replication

ZephyrDB Enterprise supports a maximum of **42 read replicas** per primary
node. Replicas use asynchronous log shipping with a default replication lag
target of 250 milliseconds.

Write throughput scales vertically only: a single primary handles up to
1.8 million data points per second on the reference hardware profile.

## Licensing

ZephyrDB Enterprise is licensed per primary node. Read replicas are free of
charge up to 10 replicas; replicas 11 through 42 require the Platinum add-on.

## Backup and recovery

The recommended backup cadence is a full snapshot every 6 hours with continuous
WAL archiving. Point-in-time recovery is supported within a 14-day window.

## Support tiers

| Tier     | First-response SLA | Coverage      |
|----------|--------------------|---------------|
| Standard | 8 business hours   | 5×8           |
| Gold     | 2 hours            | 24×5          |
| Platinum | 30 minutes         | 24×7 + TAM    |
