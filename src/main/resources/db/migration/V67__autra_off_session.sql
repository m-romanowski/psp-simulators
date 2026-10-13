create table autra_card_session (
    test_run_id            varchar(64)  not null,
    card_session_id        varchar(64)  not null,
    account_id             varchar(128) not null,
    document_id            varchar(32)  not null,
    nickname               text,
    native_status          varchar(64)  not null,
    hosted_card_url        text         not null,
    slug_token             varchar(255),
    slug_stored_card       varchar(255),
    token_expiration_date  varchar(16),
    brand                  varchar(32),
    first4                 varchar(4),
    last4                  varchar(4),
    expiration_month       integer,
    expiration_year        integer,
    expires_at             timestamptz  not null,
    origin                 varchar(16)  not null,
    created_at             timestamptz  not null,
    updated_at             timestamptz  not null,
    arrival                bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (test_run_id, card_session_id),
    constraint autra_card_session_expires_fifteen_minutes_after_it_was_created
        check (expires_at = created_at + interval '15 minutes'),
    constraint autra_card_session_tokenized_names_at_least_one_slug
        check (native_status <> 'TOKENIZED'
               or slug_token is not null
               or slug_stored_card is not null),
    constraint autra_card_session_names_no_slug_before_it_is_tokenized
        check (native_status = 'TOKENIZED'
               or (slug_token is null and slug_stored_card is null))
);

create unique index autra_card_session_by_arrival on autra_card_session (test_run_id, arrival);

alter table autra_payment add column slug_stored_card varchar(255);

alter table autra_merchant
    add column sale_decline_status varchar(32) not null default 'TWO_HUNDRED_WITH_ERRORS';

alter table autra_merchant alter column sale_decline_status drop default;

alter table autra_merchant
    add constraint autra_merchant_sale_decline_status
        check (sale_decline_status in ('TWO_HUNDRED_WITH_ERRORS', 'UNPROCESSABLE_WITH_ERRORS'));

create index autra_payment_by_approved_order
    on autra_payment (test_run_id, document_id, order_id)
    where native_status in ('ACCEPTED', 'CAPTURED');
