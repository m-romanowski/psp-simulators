alter table azericard_terminal
    add column settlement_mac varchar(32) not null default 'LIVE_WIRE';

alter table azericard_terminal
    alter column settlement_mac drop default;

alter table azericard_terminal
    add constraint azericard_terminal_settlement_mac
        check (settlement_mac in ('LIVE_WIRE', 'PUBLISHED_EXAMPLE'));
