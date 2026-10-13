alter table grow_payment_process
    add column hosted_link varchar(64);

update grow_payment_process
set hosted_link = substring(hosted_page_url from '\?l=(.+)$')
where hosted_link is null;

update grow_payment_process
set hosted_link = test_run_id || ':' || process_id
where hosted_link is null;

alter table grow_payment_process
    alter column hosted_link set not null;

create unique index grow_payment_process_by_hosted_link
    on grow_payment_process (hosted_link);
