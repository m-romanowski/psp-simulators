alter table azericard_terminal
    add column stored_card_source_terminal_ids varchar(32)[] not null default '{}';

alter table azericard_terminal
    alter column stored_card_source_terminal_ids drop default;
