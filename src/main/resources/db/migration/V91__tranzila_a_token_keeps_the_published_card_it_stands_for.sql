alter table tranzila_transaction
    add column published_as varchar(13);

alter table tranzila_challenge
    add column published_as varchar(13);

alter table tranzila_page
    add column published_as varchar(13);

update tranzila_transaction
set published_as = case card_mask
                         when '490763xxxxxx9022' then 'AUTHENTICATED'
                         when '490763xxxxxx0022' then 'FAILED'
                         when '491891xxxxxx5005' then 'CHALLENGED'
                     end
where card_mask in ('490763xxxxxx9022', '490763xxxxxx0022', '491891xxxxxx5005');

update tranzila_challenge
set published_as = case card_mask
                         when '490763xxxxxx9022' then 'AUTHENTICATED'
                         when '490763xxxxxx0022' then 'FAILED'
                         when '491891xxxxxx5005' then 'CHALLENGED'
                     end
where card_mask in ('490763xxxxxx9022', '490763xxxxxx0022', '491891xxxxxx5005');

update tranzila_transaction
set published_as = 'AUTHENTICATED'
where token = 't5d55d2922ca4029022' and card_mask = 'xxxxxxxxxxxx9022';

update tranzila_page
set published_as = 'AUTHENTICATED'
where token = 't5d55d2922ca4029022' and card_mask = 'xxxxxxxxxxxx9022';

alter table tranzila_transaction
    add constraint tranzila_transaction_published_as
        check (published_as is null
               or published_as in ('AUTHENTICATED', 'FAILED', 'CHALLENGED'));

alter table tranzila_challenge
    add constraint tranzila_challenge_published_as
        check (published_as is null
               or published_as in ('AUTHENTICATED', 'FAILED', 'CHALLENGED'));

alter table tranzila_page
    add constraint tranzila_page_published_as
        check (published_as is null
               or published_as in ('AUTHENTICATED', 'FAILED', 'CHALLENGED'));

create index tranzila_page_by_token on tranzila_page (test_run_id, account_id, token)
    where token is not null;
