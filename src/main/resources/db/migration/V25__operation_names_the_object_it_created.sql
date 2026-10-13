alter table grow_transaction_operation
    rename column provider_object_id to created_object_id;

alter table azericard_payment_operation
    rename column provider_object_id to created_object_id;

update grow_transaction_operation
set created_object_id = null
where created_object_id = transaction_id;

update azericard_payment_operation
set created_object_id = null
where created_object_id = int_ref;

alter table grow_transaction_operation
    add constraint grow_transaction_operation_names_another_object check (
        created_object_id is null or created_object_id <> transaction_id
    );

alter table azericard_payment_operation
    add constraint azericard_payment_operation_names_another_object check (
        created_object_id is null or created_object_id <> int_ref
    );
