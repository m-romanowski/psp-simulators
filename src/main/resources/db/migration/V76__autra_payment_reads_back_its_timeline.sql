alter table autra_payment add column payment_type varchar(16);

update autra_payment p
set payment_type =
        case
            when p.hold_released_at is not null then 'PREAUTH'
            when exists (select 1
                         from autra_payment_operation o
                         where o.test_run_id = p.test_run_id
                           and o.payment_id = p.payment_id
                           and o.kind = 'AUTHORIZATION')
                then 'PREAUTH'
            else 'SALE'
            end;

alter table autra_payment alter column payment_type set not null;

alter table autra_payment
    add constraint autra_payment_is_a_sale_or_a_hold
        check (payment_type in ('SALE', 'PREAUTH'));

create table autra_payment_event (
    test_run_id varchar(64) not null,
    payment_id  varchar(64) not null,
    seq         integer     not null,
    event_type  varchar(32) not null,
    status      varchar(64) not null,
    amount      text        not null,
    error_code  varchar(64),
    msg         text,
    at          timestamptz not null,
    primary key (test_run_id, payment_id, seq),
    foreign key (test_run_id, payment_id)
        references autra_payment (test_run_id, payment_id) on delete cascade,
    constraint autra_payment_event_reports_a_refusal_in_full_or_not_at_all
        check ((error_code is null and msg is null)
               or (error_code is not null and msg is not null))
);
