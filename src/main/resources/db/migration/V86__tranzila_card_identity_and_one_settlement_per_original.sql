alter table tranzila_transaction add column card_fingerprint varchar(64);

update tranzila_transaction
set card_fingerprint = encode(sha256(convert_to('mask:' || test_run_id || ':' || card_mask, 'UTF8')), 'hex')
where card_fingerprint is null;

alter table tranzila_transaction
    alter column card_fingerprint set not null,
    add constraint tranzila_transaction_card_fingerprint_is_sha256_hex
        check (card_fingerprint ~ '^[0-9a-f]{64}$');

drop index tranzila_transaction_closes_a_hold_once;

drop index tranzila_transaction_credits_once;

drop index tranzila_transaction_cancels_once;

do $$
    declare
        the_last_arrival bigint := coalesce((select max(arrival) from tranzila_transaction), 0);
    begin
        execute format(
                'create unique index tranzila_transaction_closes_a_hold_once'
                    || ' on tranzila_transaction (test_run_id, reference_txn_id)'
                    || ' where txn_type in (%L, %L) and processor_response_code in (%L, %L)',
                'force', 'reversal', '000', '777');
        execute format(
                'create unique index tranzila_transaction_settles_an_original_once'
                    || ' on tranzila_transaction (test_run_id, reference_txn_id)'
                    || ' where txn_type in (%L, %L) and processor_response_code in (%L, %L)'
                    || ' and arrival > %s',
                'credit', 'cancel', '000', '777', the_last_arrival);
        execute format(
                'create unique index tranzila_transaction_credits_once'
                    || ' on tranzila_transaction (test_run_id, reference_txn_id)'
                    || ' where txn_type = %L and processor_response_code in (%L, %L)'
                    || ' and arrival <= %s',
                'credit', '000', '777', the_last_arrival);
        execute format(
                'create unique index tranzila_transaction_cancels_once'
                    || ' on tranzila_transaction (test_run_id, reference_txn_id)'
                    || ' where txn_type = %L and processor_response_code in (%L, %L)'
                    || ' and arrival <= %s',
                'cancel', '000', '777', the_last_arrival);
    end
$$;

create index tranzila_transaction_by_dc_disable_and_type
    on tranzila_transaction (test_run_id, account_id, dc_disable, txn_type);
