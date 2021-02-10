create sequence product_id_seq start with 1 increment by 100;

create table products (
    id bigint DEFAULT nextval('product_id_seq') not null,
    code varchar(1024) not null unique,
    name varchar(1024),
    description varchar(1024),
    price NUMERIC(19,2),
    primary key (id)
);
