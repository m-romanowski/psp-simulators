alter table simulator_test_run
    add column fingerprint_key text,
    add constraint simulator_test_run_fingerprint_key_is_minted
        check (fingerprint_key is null or fingerprint_key ~ '^[0-9a-f]{64}$');

update stripe_idempotency_key
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');

update stripe_idempotency_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');

update grow_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');

update azericard_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');

update autra_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');

update worldline_idempotence_key
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');

update worldline_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');

update tranzila_keyed_exchange
set fingerprint = encode(sha256(convert_to((select gen_random_uuid()::text) || fingerprint, 'UTF8')), 'hex');
