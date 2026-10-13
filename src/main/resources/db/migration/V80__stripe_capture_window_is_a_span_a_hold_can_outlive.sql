update stripe_account
set capture_window = 'PT168H'
where capture_window ~ '^PT(0{1,5}H)?(0{1,2}M)?0{1,2}\.0{6}[0-9]{1,3}S$';

alter table stripe_account
    add constraint stripe_account_capture_window_is_a_span_a_hold_can_outlive
        check (capture_window !~ '^PT(0{1,5}H)?(0{1,2}M)?0{1,2}\.0{6}[0-9]{1,3}S$');
