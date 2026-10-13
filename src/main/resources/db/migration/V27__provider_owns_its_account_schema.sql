create table grow_account (
    test_run_id              varchar(64)  not null,
    account_id               varchar(128) not null,
    user_id                  text         not null,
    page_code                text         not null,
    callback_acknowledgement varchar(32)  not null,
    settlement_ceiling       varchar(32)  not null,
    refund_allowance         varchar(32)  not null,
    refund_excess_rejection  varchar(32)  not null,
    merchant_balance         varchar(32)  not null,
    process_lookup_rejection varchar(32)  not null,
    capture_window           text         not null,
    automatic_void_after     text         not null,
    primary key (test_run_id, account_id),
    constraint grow_account_callback_acknowledgement
        check (callback_acknowledgement in ('HTTP_2XX', 'APPROVE_TRANSACTION')),
    constraint grow_account_settlement_ceiling
        check (settlement_ceiling in ('FRAME_CAPTURE', 'THIRTY_PERCENT_ABOVE')),
    constraint grow_account_refund_allowance
        check (refund_allowance in ('ONE_REFUND', 'TWO_REFUNDS')),
    constraint grow_account_refund_excess_rejection
        check (refund_excess_rejection in ('ORIGINAL_AMOUNT', 'CHARGE_AMOUNT',
                                           'TRANSACTION_AMOUNT')),
    constraint grow_account_merchant_balance
        check (merchant_balance in ('AVAILABLE', 'EMPTY')),
    constraint grow_account_process_lookup_rejection
        check (process_lookup_rejection in ('PUBLISHED_EXAMPLE', 'PUBLISHED_TABLE'))
);

create unique index grow_account_by_user_id on grow_account (test_run_id, user_id);

create unique index grow_account_by_page_code on grow_account (test_run_id, page_code);

create index grow_account_by_credentials on grow_account (user_id, page_code);

create table azericard_terminal (
    test_run_id             varchar(64)  not null,
    account_id              varchar(128) not null,
    terminal_id             varchar(32)  not null,
    merchant_key            text         not null,
    gateway_key             text         not null,
    duplicate_response_code varchar(3)   not null,
    partial_completion      varchar(32)  not null,
    primary key (test_run_id, account_id),
    constraint azericard_terminal_duplicate_response_code
        check (duplicate_response_code ~ '^-?[0-9A-Za-z]{1,2}$'),
    constraint azericard_terminal_partial_completion
        check (partial_completion in ('CLOSES_THE_AUTHORIZATION',
                                      'LEAVES_THE_REMAINDER_CAPTURABLE'))
);

create unique index azericard_terminal_by_terminal_id
    on azericard_terminal (test_run_id, terminal_id);

create index azericard_terminal_by_credentials on azericard_terminal (terminal_id);

insert into grow_account
    (test_run_id, account_id, user_id, page_code, callback_acknowledgement, settlement_ceiling,
     refund_allowance, refund_excess_rejection, merchant_balance, process_lookup_rejection,
     capture_window, automatic_void_after)
select test_run_id,
       merchant_id,
       user_id,
       page_code,
       coalesce(nullif(settings::jsonb ->> 'callbackAcknowledgement', ''), 'HTTP_2XX'),
       coalesce(nullif(settings::jsonb ->> 'settlementCeiling', ''), 'FRAME_CAPTURE'),
       coalesce(nullif(settings::jsonb ->> 'refundAllowance', ''), 'ONE_REFUND'),
       coalesce(nullif(settings::jsonb ->> 'refundExcessRejection', ''), 'ORIGINAL_AMOUNT'),
       coalesce(nullif(settings::jsonb ->> 'merchantBalance', ''), 'AVAILABLE'),
       coalesce(nullif(settings::jsonb ->> 'processLookupRejection', ''), 'PUBLISHED_EXAMPLE'),
       coalesce(nullif(settings::jsonb ->> 'captureWindow', ''), 'PT168H'),
       coalesce(nullif(settings::jsonb ->> 'automaticVoidAfter', ''), 'PT240H')
from simulator_provider_account
where provider = 'grow'
  and user_id is not null
  and page_code is not null;

insert into azericard_terminal
    (test_run_id, account_id, terminal_id, merchant_key, gateway_key, duplicate_response_code,
     partial_completion)
select test_run_id,
       merchant_id,
       terminal_id,
       api_key,
       api_secret,
       coalesce(nullif(settings::jsonb ->> 'azericard.duplicate-response-code', ''), '-1'),
       coalesce(nullif(settings::jsonb ->> 'azericard.partial-completion', ''),
                'CLOSES_THE_AUTHORIZATION')
from simulator_provider_account
where provider = 'azericard'
  and terminal_id is not null
  and api_key is not null
  and api_secret is not null;

alter table simulator_provider_account
    drop column user_id,
    drop column page_code,
    drop column api_key,
    drop column api_secret,
    drop column terminal_id,
    drop column settings;

alter table simulator_provider_account rename column merchant_id to account_id;

alter table simulator_webhook_target
    drop column secret,
    drop column key_id;

alter table simulator_webhook_target rename column merchant_id to account_id;
