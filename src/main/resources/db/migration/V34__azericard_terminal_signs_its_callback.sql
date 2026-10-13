alter table azericard_terminal
    add column callback_signature varchar(32) not null default 'VALID';

alter table azericard_terminal
    alter column callback_signature drop default;

alter table azericard_terminal
    add constraint azericard_terminal_callback_signature
        check (callback_signature in ('VALID', 'CORRUPTED', 'ABSENT'));
