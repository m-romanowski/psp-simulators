create table simulator_fingerprint_key (
    test_run_id     varchar(64) primary key,
    fingerprint_key text        not null,
    constraint simulator_fingerprint_key_is_minted
        check (fingerprint_key ~ '^[0-9a-f]{64}$')
);

alter table stripe_idempotency_exchange
    add column answer_kept boolean not null default true,
    add constraint stripe_idempotency_exchange_answer_kept_or_none
        check (answer_kept or (http_status is null and content_type is null and body is null));

alter table grow_keyed_exchange
    add column answer_kept boolean not null default true,
    add constraint grow_keyed_exchange_answer_kept_or_none
        check (answer_kept or (http_status is null and content_type is null and body is null));

alter table azericard_keyed_exchange
    add column answer_kept boolean not null default true,
    add constraint azericard_keyed_exchange_answer_kept_or_none
        check (answer_kept or (http_status is null and content_type is null and body is null));

alter table autra_keyed_exchange
    add column answer_kept boolean not null default true,
    add constraint autra_keyed_exchange_answer_kept_or_none
        check (answer_kept or (http_status is null and content_type is null and body is null));

alter table worldline_keyed_exchange
    add column answer_kept boolean not null default true,
    add constraint worldline_keyed_exchange_answer_kept_or_none
        check (answer_kept or (http_status is null and content_type is null and body is null));

alter table tranzila_keyed_exchange
    add column answer_kept boolean not null default true,
    add constraint tranzila_keyed_exchange_answer_kept_or_none
        check (answer_kept or (http_status is null and content_type is null and body is null));

update stripe_idempotency_key
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');

update worldline_idempotence_key
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');

update stripe_idempotency_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex'),
    http_status = null,
    content_type = null,
    body = null,
    answer_kept = false;

update grow_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex'),
    http_status = null,
    content_type = null,
    body = null,
    answer_kept = false;

update azericard_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex'),
    http_status = null,
    content_type = null,
    body = null,
    answer_kept = false;

update autra_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex'),
    http_status = null,
    content_type = null,
    body = null,
    answer_kept = false;

update worldline_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex'),
    http_status = null,
    content_type = null,
    body = null,
    answer_kept = false;

update tranzila_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex'),
    http_status = null,
    content_type = null,
    body = null,
    answer_kept = false;
