create table optimistically_locked_item
(
    id      UUID primary key,
    version integer not null
)