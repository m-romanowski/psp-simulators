alter table autra_payment alter column tracking_number drop not null;

alter table autra_payment drop column nsu;

alter table autra_payment
    add constraint autra_payment_tracking_number_is_twelve_digits
        check (tracking_number is null or tracking_number ~ '^[0-9]{12}$');

alter table autra_merchant
    add column acquirer_transaction_identifier varchar(32) not null default 'REPORTED';

alter table autra_merchant alter column acquirer_transaction_identifier drop default;

alter table autra_merchant
    add constraint autra_merchant_acquirer_transaction_identifier
        check (acquirer_transaction_identifier in ('REPORTED', 'NOT_REPORTED'));
