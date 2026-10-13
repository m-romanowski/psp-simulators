alter table autra_card_session
    add column page_token text;

update autra_card_session
set page_token = substring(hosted_card_url from '#token=([0-9a-f]+)$')
where page_token is null;

update autra_card_session
set page_token = md5(random()::text || clock_timestamp()::text || card_session_id)
where page_token is null;

alter table autra_card_session
    alter column page_token set not null;

create unique index autra_card_session_by_page_token
    on autra_card_session (page_token);
