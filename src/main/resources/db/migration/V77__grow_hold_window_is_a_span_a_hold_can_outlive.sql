update grow_account
set capture_window = 'PT168H'
where not (case
               when capture_window ~
                    '^P([0-9]{1,3}D)?(T(?=[0-9])([0-9]{1,5}H)?([0-9]{1,7}M)?([0-9]{1,9}(\.[0-9]{1,6})?S)?)?$'
                   and capture_window ~ '[0-9]'
                   then capture_window::interval > interval '0'
                       and capture_window::interval <= interval '365 days'
               else false
           end);

update grow_account
set automatic_void_after = 'PT240H'
where not (case
               when automatic_void_after ~
                    '^P([0-9]{1,3}D)?(T(?=[0-9])([0-9]{1,5}H)?([0-9]{1,7}M)?([0-9]{1,9}(\.[0-9]{1,6})?S)?)?$'
                   and automatic_void_after ~ '[0-9]'
                   then automatic_void_after::interval > interval '0'
                       and automatic_void_after::interval <= interval '365 days'
               else false
           end);

alter table grow_account
    add constraint grow_account_capture_window_is_a_span_a_hold_can_outlive
        check (case
                   when capture_window ~
                        '^P([0-9]{1,3}D)?(T(?=[0-9])([0-9]{1,5}H)?([0-9]{1,7}M)?([0-9]{1,9}(\.[0-9]{1,6})?S)?)?$'
                       and capture_window ~ '[0-9]'
                       then capture_window::interval > interval '0'
                           and capture_window::interval <= interval '365 days'
                   else false
               end),
    add constraint grow_account_automatic_void_after_is_a_span_a_hold_can_outlive
        check (case
                   when automatic_void_after ~
                        '^P([0-9]{1,3}D)?(T(?=[0-9])([0-9]{1,5}H)?([0-9]{1,7}M)?([0-9]{1,9}(\.[0-9]{1,6})?S)?)?$'
                       and automatic_void_after ~ '[0-9]'
                       then automatic_void_after::interval > interval '0'
                           and automatic_void_after::interval <= interval '365 days'
                   else false
               end);
