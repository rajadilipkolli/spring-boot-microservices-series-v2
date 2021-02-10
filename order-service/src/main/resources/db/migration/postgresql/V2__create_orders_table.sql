create sequence order_id_seq start with 1 increment by 100;

create table orders (
    id bigint DEFAULT nextval('order_id_seq') not null,
    text varchar(1024) not null,
    primary key (id)
);
