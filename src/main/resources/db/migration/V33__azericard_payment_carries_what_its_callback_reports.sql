alter table azericard_payment
    add column backref text,
    add column email   text,
    add column token   varchar(28);
