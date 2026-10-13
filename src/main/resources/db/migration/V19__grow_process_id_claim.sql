create table grow_process_id_claim (
    test_run_id varchar(64) not null,
    process_id  varchar(32) not null,
    primary key (test_run_id, process_id)
);

insert into grow_process_id_claim (test_run_id, process_id)
select test_run_id, process_id from grow_payment_process;
