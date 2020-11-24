create schema s;

create table s.my_nullable_class
(
    "id"              text primary key,
    "name"            text,
    "capacity"        text,
    "longivity"       text,
    "proc"            text,
    "version"         integer,
    "date"            date,
    "bool"            boolean,
    "timestamp"       timestamp with time zone,
    "uuid"            uuid,
    "time"            time without time zone,
    "local_date"      date,
    "local_date_time" timestamp without time zone,
    "list"            jsonb not null,
    "enum"            text
);