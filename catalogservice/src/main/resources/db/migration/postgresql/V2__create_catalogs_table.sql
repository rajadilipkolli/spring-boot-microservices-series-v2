create sequence catalog_id_seq start with 1 increment by 100;

create table catalogs (
    id bigint DEFAULT nextval('catalog_id_seq') not null,
    text varchar(1024) not null,
    primary key (id)
);
