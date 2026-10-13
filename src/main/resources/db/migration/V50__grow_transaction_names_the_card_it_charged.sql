alter table grow_transaction
    add column card_suffix     varchar(8),
    add column card_type       text,
    add column card_type_code  varchar(8),
    add column card_brand      text,
    add column card_brand_code varchar(8),
    add column card_exp        varchar(8);

update grow_transaction
set card_suffix = '4580',
    card_type = 'Foreign',
    card_type_code = '2',
    card_brand = 'Visa',
    card_brand_code = '3',
    card_exp = '0424'
where card_suffix is null;

alter table grow_transaction
    alter column card_suffix set not null,
    alter column card_type set not null,
    alter column card_type_code set not null,
    alter column card_brand set not null,
    alter column card_brand_code set not null,
    alter column card_exp set not null;
