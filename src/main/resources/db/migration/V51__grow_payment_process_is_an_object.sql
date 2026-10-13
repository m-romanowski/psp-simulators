alter table grow_payment_process
    add column arrival bigint not null default nextval('simulator_provider_object_arrival');

update grow_payment_process
set arrival = already_here.arrival
from (select test_run_id,
             process_id,
             -row_number() over (order by created_at desc, test_run_id, process_id) as arrival
      from grow_payment_process) as already_here
where grow_payment_process.test_run_id = already_here.test_run_id
  and grow_payment_process.process_id = already_here.process_id;

create unique index grow_payment_process_by_arrival
    on grow_payment_process (test_run_id, arrival);

create table grow_object_id_claim (
    test_run_id varchar(64) not null,
    object_id   varchar(32) not null,
    kind        varchar(16) not null,
    primary key (test_run_id, object_id)
);

insert into grow_object_id_claim (test_run_id, object_id, kind)
select test_run_id, process_id, 'PROCESS'
from grow_process_id_claim;

insert into grow_object_id_claim (test_run_id, object_id, kind)
select test_run_id, process_id, 'PROCESS'
from grow_payment_process
on conflict (test_run_id, object_id) do nothing;

insert into grow_object_id_claim (test_run_id, object_id, kind)
select test_run_id, transaction_id, 'TRANSACTION'
from grow_transaction_id_claim
on conflict (test_run_id, object_id) do nothing;

insert into grow_object_id_claim (test_run_id, object_id, kind)
select test_run_id, transaction_id, 'TRANSACTION'
from grow_transaction
on conflict (test_run_id, object_id) do nothing;

drop table grow_process_id_claim;

drop table grow_transaction_id_claim;
