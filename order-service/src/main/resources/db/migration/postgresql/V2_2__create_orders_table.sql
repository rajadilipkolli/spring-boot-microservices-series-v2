create sequence order_id_seq start with 1 increment by 100;
create sequence order_item_id_seq start with 1 increment by 100;

create table orders (
    id bigint DEFAULT nextval('order_id_seq') not null,
    customer_email varchar(1024) not null,
    customer_address varchar(1024) not null,
    primary key (id)
);

create table order_items (
    id bigint DEFAULT nextval('order_item_id_seq') not null,
    product_id bigint not null,
    quantity int not null,
    product_price NUMERIC(19,2),
    order_id bigint not null,
    primary key (id)
);
