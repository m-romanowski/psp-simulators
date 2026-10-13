alter table autra_merchant
    add column webhook_secret text;

alter table autra_merchant
    add constraint autra_merchant_webhook_secret_is_long_enough
        check (webhook_secret is null or length(webhook_secret) >= 32);

create table autra_pix_payment (
    test_run_id   varchar(64)  not null,
    pix_id        varchar(64)  not null,
    account_id    varchar(128) not null,
    document_id   varchar(32)  not null,
    order_id      varchar(255),
    description   text,
    amount        text         not null,
    currency      text         not null,
    native_status varchar(64)  not null,
    qr_code       text         not null,
    slug          varchar(64)  not null,
    muid          varchar(64)  not null,
    expires_at    timestamptz  not null,
    origin        varchar(16)  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    arrival       bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (test_run_id, pix_id),
    constraint autra_pix_payment_id_is_thirty_two_lowercase_hex
        check (pix_id ~ '^[0-9a-f]{32}$'),
    constraint autra_pix_payment_expires_after_it_was_created
        check (expires_at > created_at)
);

create unique index autra_pix_payment_by_arrival on autra_pix_payment (test_run_id, arrival);

create index autra_pix_payment_by_order on autra_pix_payment (test_run_id, document_id, order_id);
