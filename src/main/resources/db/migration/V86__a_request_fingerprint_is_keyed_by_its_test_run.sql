create table simulator_fingerprint_key (
    test_run_id     varchar(64) primary key,
    fingerprint_key text        not null,
    constraint simulator_fingerprint_key_is_minted
        check (fingerprint_key ~ '^[0-9a-f]{64}$')
);

update stripe_idempotency_key
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');

update stripe_idempotency_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');

update grow_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex'),
    http_status = null,
    content_type = null,
    body = null;

update azericard_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex'),
    http_status = null,
    content_type = null,
    body = null;

update autra_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex'),
    http_status = null,
    content_type = null,
    body = null;

update worldline_idempotence_key
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');

update worldline_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex'),
    http_status = null,
    content_type = null,
    body = null;

update tranzila_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');
